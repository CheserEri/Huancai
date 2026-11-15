import json
import os
from datetime import datetime

ATT = r"d:\Code\Projects\Huancai\data\legacy\attend.json"
TMP = os.path.join(os.environ.get("TEMP", "."), "hc-import")
OUT = os.path.join(TMP, "import_attendance.sql")
OUT_EXT = os.path.join(TMP, "backfill_student_ext.sql")

with open(os.path.join(TMP, "stu_map.json"), encoding="utf-8") as f:
    stu_map = {int(k): v for k, v in json.load(f).items()}

sched = {}
with open(os.path.join(TMP, "ref_schedule.tsv"), "r", encoding="utf-8-sig") as f:
    for line in f:
        c = line.rstrip("\n").split("\t")
        if len(c) >= 5:
            sched[int(c[0])] = (int(c[1]), int(c[2]), int(c[3]), int(c[4]))

STATUS = {"上课": "出勤", "请假": "请假", "未记录": "未记录"}


def q(s, limit=200):
    if s is None or s == "":
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''")[:limit] + "'"


rows = json.load(open(ATT, encoding="utf-8-sig"))
vals = []
skipped = 0
seen = set()
for r in rows:
    sid = r.get("sid")
    sm = sched.get(sid)
    stu = stu_map.get(r.get("stuInfoId"))
    if not sm or not stu:
        skipped += 1
        continue
    sch_id, class_id, teacher_id, campus_id = sm
    key = "%s_%s" % (r.get("logId"), r.get("stuInfoId"))
    if key in seen:
        continue
    seen.add(key)
    attend = STATUS.get(r.get("attend"), "出勤")
    times = r.get("classTimes") or 0
    if attend == "出勤" and times <= 0:
        times = 1
    elif attend != "出勤":
        times = 0
    vals.append(
        "({},{},{},{},{},{},{},{},{},{})".format(
            campus_id,
            sch_id,
            "NULL" if class_id == 0 else class_id,
            stu,
            "NULL" if teacher_id == 0 else teacher_id,
            q(r.get("date")),
            "%.1f" % float(times),
            q(attend),
            q((r.get("date") or "") + " 00:00:00"),
            q(key, 64),
        )
    )

COLS = "campus_id,schedule_id,class_id,student_id,teacher_id,lesson_date,lessons,status,checked_at,ext_id"
with open(OUT, "w", encoding="utf-8") as f:
    f.write("SET NAMES utf8mb4;\n")
    f.write("DELETE FROM attendance WHERE ext_id IS NOT NULL;\n")
    for i in range(0, len(vals), 500):
        f.write("INSERT INTO attendance (" + COLS + ") VALUES\n")
        f.write(",\n".join(vals[i:i + 500]))
        f.write(";\n")

with open(OUT_EXT, "w", encoding="utf-8") as f:
    f.write("SET NAMES utf8mb4;\n")
    items = sorted(stu_map.items())
    for i in range(0, len(items), 300):
        chunk = items[i:i + 300]
        cases = " ".join("WHEN %d THEN '%s'" % (lid, ext) for ext, lid in chunk)
        ids = ",".join(str(lid) for ext, lid in chunk)
        f.write("UPDATE student SET ext_id = CASE id %s END WHERE id IN (%s);\n" % (cases, ids))

print("rows=%d skipped=%d out=%s" % (len(vals), skipped, OUT))
