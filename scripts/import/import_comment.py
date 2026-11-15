import os
import json
import glob

CHUNK_DIR = r"d:\Code\Projects\Huancai\data\legacy\comment_chunks"
MAP_DIR = r"d:\Code\Projects\Huancai\data\legacy\map"
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_comment.sql")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

BATCH_ROWS = 500


def q(s=None, limit=8000):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:limit] + "'"


def rating_of(score):
    try:
        v = float(score)
    except (TypeError, ValueError):
        return None
    if v >= 4.5:
        return "优秀"
    if v >= 3.5:
        return "良好"
    if v > 0:
        return "需加强"
    return None


def dt(s):
    if not s or s.startswith("0001-01-01"):
        return None
    return s.replace("T", " ")[:19]


def load_tsv(name):
    out = {}
    with open(os.path.join(MAP_DIR, name + ".tsv"), encoding="utf-8-sig") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 2 and parts[0]:
                out[parts[0].strip()] = parts[1].strip()
    return out


student_map = load_tsv("student")
staff_map = load_tsv("staff")
att_map = load_tsv("attendance")
sch_map = {}
with open(os.path.join(MAP_DIR, "schedule.tsv"), encoding="utf-8-sig") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 4 and parts[0]:
            sch_map[parts[0].strip()] = (parts[1].strip(), parts[2].strip(), parts[3].strip())

records = {}
for fp in sorted(glob.glob(os.path.join(CHUNK_DIR, "cmt_*.json"))):
    with open(fp, encoding="utf-8") as f:
        for it in json.load(f):
            key = (int(it["logId"]), int(it["stuInfoId"]))
            prev = records.get(key)
            if prev is None or int(it.get("commentId") or 0) >= int(prev.get("commentId") or 0):
                records[key] = it

rows = []
skipped = 0
for (log_id, stu_id), it in records.items():
    sid = student_map.get(str(stu_id))
    if not sid:
        skipped += 1
        continue
    sch = sch_map.get(str(log_id))
    sch_id = sch[0] if sch else None
    cls_id = sch[1] if sch and sch[1] and sch[1] != "NULL" else None
    campus_id = sch[2] if sch and sch[2] and sch[2] != "NULL" else "1"
    att_id = att_map.get("%d_%d" % (log_id, stu_id))
    tea_id = staff_map.get(str(it.get("teacherId") or ""))
    created = dt(it.get("dateTime")) or "2019-01-01 00:00:00"
    read_at = created if it.get("read") else None
    rows.append(
        "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"
        % (
            campus_id,
            att_id or "NULL",
            sch_id or "NULL",
            cls_id or "NULL",
            sid,
            tea_id or "NULL",
            q(it.get("content"), 4000),
            q(rating_of(it.get("aveScore"))),
            q(read_at),
            q("SPC-%s" % it.get("commentId")),
            q(created),
        )
    )

lines = [
    "USE huancai_erp;",
    "START TRANSACTION;",
    "DELETE FROM class_comment WHERE ext_id LIKE 'SPC-%';",
]
HEAD = (
    "INSERT INTO class_comment (campus_id, attendance_id, schedule_id, class_id, student_id, teacher_id, "
    "content, rating, read_at, ext_id, created_at) VALUES "
)
for i in range(0, len(rows), BATCH_ROWS):
    lines.append(HEAD + ",".join(rows[i:i + BATCH_ROWS]) + ";")
lines.append("COMMIT;")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print("records", len(records), "insert_rows", len(rows), "skipped_no_student", skipped)
print("sql", OUT, os.path.getsize(OUT))
