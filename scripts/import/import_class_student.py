import os
import json

MAP_DIR = r"d:\Code\Projects\Huancai\data\schoolpal\map"
ROSTER = r"d:\Code\Projects\Huancai\data\schoolpal\class_roster.json"
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_class_student.sql")
os.makedirs(os.path.dirname(OUT), exist_ok=True)


def q(s=None, limit=255):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:limit] + "'"


def num(v, default=0.0):
    try:
        return round(float(v), 1)
    except (TypeError, ValueError):
        return default


def dt(s):
    if not s or str(s).startswith("0001-01-01"):
        return None
    return str(s).replace("T", " ")[:19]


def load_tsv(name):
    out = {}
    with open(os.path.join(MAP_DIR, name + ".tsv"), encoding="utf-8-sig") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 2 and parts[0]:
                out[parts[0].strip()] = parts[1].strip()
    return out


student_map = load_tsv("student")
class_map = {}
with open(os.path.join(MAP_DIR, "class.tsv"), encoding="utf-8-sig") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 3 and parts[0]:
            class_map[parts[0].strip()] = (parts[1].strip(), parts[2].strip())

# 兜底：校宝班级ID -> (班级名, 校区) -> 本地 class_group
ext_name = {}
with open(os.path.join(MAP_DIR, "class_ext_name.tsv"), encoding="utf-8-sig") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 3 and parts[0]:
            ext_name[parts[0].strip()] = (parts[1].strip(), parts[2].strip())
name_map = {}
with open(os.path.join(MAP_DIR, "class_name.tsv"), encoding="utf-8-sig") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 3 and parts[0]:
            name_map[(parts[1].strip(), parts[2].strip())] = parts[0].strip()

with open(ROSTER, encoding="utf-8") as f:
    roster = json.load(f)

lines = [
    "USE huancai_erp;",
    "START TRANSACTION;",
    "DELETE FROM class_student;",
    "-- 基础名单：由历史上课记录反推（班级 + 学员 + 实际上课次数）",
    """
INSERT INTO class_student (campus_id, class_id, student_id, status, join_date, total_lessons, used_lessons, remain_lessons)
SELECT a.campus_id, a.class_id, a.student_id, '在读', MIN(a.lesson_date), COUNT(*), SUM(a.lessons), 0
FROM attendance a
JOIN class_group cg ON cg.id = a.class_id
WHERE a.class_id IS NOT NULL
GROUP BY a.campus_id, a.class_id, a.student_id;
""".strip(),
    "-- 校宝班级学员快照覆盖（在读班级的课时余额与入班日期）",
]

matched = 0
skipped_cls = 0
skipped_stu = 0
used_fallback = 0
for ext_class, students in roster.items():
    cls = class_map.get(str(ext_class))
    if not cls:
        info = ext_name.get(str(ext_class))
        local = name_map.get((info[0], info[1])) if info else None
        if local:
            cls = (local, info[1])
            used_fallback += 1
    if not cls:
        skipped_cls += len(students)
        continue
    cls_id, campus_id = cls
    for s in students:
        stu = student_map.get(str(s.get("stuInfoId")))
        if not stu:
            skipped_stu += 1
            continue
        matched += 1
        lines.append(
            "INSERT INTO class_student (campus_id, class_id, student_id, status, join_date, total_lessons, "
            "used_lessons, remain_lessons, remark, ext_id) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) "
            "ON DUPLICATE KEY UPDATE status = VALUES(status), join_date = VALUES(join_date), "
            "total_lessons = VALUES(total_lessons), used_lessons = VALUES(used_lessons), "
            "remain_lessons = VALUES(remain_lessons), remark = VALUES(remark), ext_id = VALUES(ext_id);"
            % (
                campus_id,
                cls_id,
                stu,
                q(s.get("status") or "在读", 16),
                q(dt(s.get("addDate"))),
                num(s.get("totalClassTimes")),
                num(s.get("usedClassTimes")),
                num(s.get("unusedClassTimes")),
                q(s.get("newOld"), 32),
                q("SPCS-%s" % s.get("id")),
            )
        )

lines.append("COMMIT;")
lines.append("SELECT COUNT(*) AS class_student_rows, COUNT(DISTINCT class_id) AS classes, COUNT(DISTINCT student_id) AS students FROM class_student;")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print("roster_classes", len(roster), "roster_students", sum(len(v) for v in roster.values()))
print("matched", matched, "skipped_no_class", skipped_cls, "skipped_no_student", skipped_stu, "class_fallback", used_fallback)
print("sql", OUT, os.path.getsize(OUT))
