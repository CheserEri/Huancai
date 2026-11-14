import json
import os
import re
from datetime import datetime, timezone, timedelta

SRC = r"d:\Code\Projects\Huancai\data\schoolpal\schedule.json"
CLASSES = r"d:\Code\Projects\Huancai\data\schoolpal\classes.json"
REF_CLASSES = os.path.join(os.environ.get("TEMP", "."), "hc-import", "ref_class.tsv")
REF_COURSES = os.path.join(os.environ.get("TEMP", "."), "hc-import", "ref_course.tsv")
REF_STAFF = os.path.join(os.environ.get("TEMP", "."), "hc-import", "ref_staff.tsv")
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_schedule.sql")

CST = timezone(timedelta(hours=8))
SCHOOL_CAMPUS = {"620989": 1, "629815": 2}


def q(s):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:500] + "'"


def num(v):
    if v is None or v == "":
        return "NULL"
    try:
        return str(int(v))
    except (TypeError, ValueError):
        return "NULL"


def load_tsv(path):
    m = {}
    with open(path, "r", encoding="utf-8-sig") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if len(c) >= 2 and c[1].strip():
                m[c[1].strip()] = int(c[0])
    return m


def parse_ms(v):
    if not v:
        return None
    m = re.search(r"\((-?\d+)", str(v))
    if not m:
        return None
    ms = int(m.group(1))
    if ms <= 0:
        return None
    return datetime.fromtimestamp(ms / 1000, CST).replace(tzinfo=None)


def parse_date(item):
    d = str(item.get("DayOfWeek") or "")[:10]
    if re.match(r"^\d{4}-\d{2}-\d{2}$", d):
        return d
    ps = parse_ms(item.get("PeriodStart"))
    return ps.strftime("%Y-%m-%d") if ps else None


def parse_times(item):
    tp = str(item.get("TimePeriod") or "").strip()
    m = re.match(r"^(\d{1,2}:\d{2})-(\d{1,2}:\d{2})$", tp)
    if m:
        return m.group(1), m.group(2)
    ps, pe = parse_ms(item.get("PeriodStart")), parse_ms(item.get("PeriodEnd"))
    if ps and pe:
        return ps.strftime("%H:%M"), pe.strftime("%H:%M")
    return None, None


class_by_name = load_tsv(REF_CLASSES)
course_by_name = load_tsv(REF_COURSES)
staff_by_name = load_tsv(REF_STAFF)
sp_class_by_id = {c["id"]: c for c in json.load(open(CLASSES, encoding="utf-8-sig"))["rows"]}

rows = json.load(open(SRC, encoding="utf-8-sig"))
vals = []
skipped = 0
for it in rows:
    d = parse_date(it)
    st, et = parse_times(it)
    if not d or not st or not et:
        skipped += 1
        continue
    campus = SCHOOL_CAMPUS.get(str(it.get("SchoolId")), 1)
    cls_id = None
    spc = sp_class_by_id.get(it.get("ClassId"))
    if spc:
        cls_id = class_by_name.get((spc.get("className") or "").strip())
    course_id = course_by_name.get((it.get("LessonName") or "").strip())
    teacher_id = staff_by_name.get((it.get("TeacherName") or "").strip())
    status = "已上课" if (it.get("TeachingLogId") or 0) > 0 else "待上课"
    ps, pe = parse_ms(it.get("PeriodStart")), parse_ms(it.get("PeriodEnd"))
    vals.append(
        "({},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},NOW())".format(
            campus,
            num(cls_id),
            num(course_id),
            num(teacher_id),
            q(d),
            q(st + ":00"),
            q(et + ":00"),
            q(str(it.get("ClassroomName") or "").strip() or None),
            q(status),
            q((it.get("ClassName") or "").strip() or None),
            q((it.get("LessonName") or "").strip() or None),
            q((it.get("TeacherName") or "").strip() or None),
            q((it.get("AssistantName") or "").strip() or None),
            num(it.get("LessonMode")),
            num(it.get("TeachingLogId")),
            num(it.get("ShouldAttendanceCount")),
            num(it.get("ActualAttendanceCount")),
            q(ps.strftime("%Y-%m-%d %H:%M:%S") if ps else None),
            q(pe.strftime("%Y-%m-%d %H:%M:%S") if pe else None),
            num(it.get("ClassId")),
            num(it.get("ClassTeacherId")),
            num(it.get("TeacheAssistantId")),
            num(it.get("SchoolId")),
            q(str(it.get("Id"))),
        )
    )

COLS = (
    "campus_id,class_id,course_id,teacher_id,lesson_date,start_time,end_time,room,status,"
    "class_name,course_name,teacher_name,assistant_name,lesson_mode,teaching_log_id,"
    "should_count,actual_count,period_start,period_end,ext_class_id,ext_teacher_id,"
    "ext_assistant_id,ext_school_id,ext_id,created_at"
)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("SET NAMES utf8mb4;\n")
    f.write("DELETE FROM schedule WHERE ext_id IS NOT NULL;\n")
    for i in range(0, len(vals), 500):
        f.write("INSERT INTO schedule (" + COLS + ") VALUES\n")
        f.write(",\n".join(vals[i:i + 500]))
        f.write(";\n")

print("rows=%d skipped=%d out=%s" % (len(vals), skipped, OUT))
