"""把校宝「班级列表」页 64 个班级的完整字段导入 class_group。

- 数据源：data/schoolpal/classes.json（班级列表页 grid 数据，64 行）
- 匹配：优先 sp_class_id，其次 ext_id = 校宝班级ID（排课导入时用过），最后 ext_id = SP-CLASS-xxx
- 幂等：可反复重跑；不会新增重复班级
"""
import json
import os
import re
import subprocess

BASE = r"d:\Code\Projects\Huancai\data\schoolpal"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_class_list.sql")


def q(s=None, limit=255):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:limit] + "'"


def one(sql):
    p = subprocess.run([MYSQL] + DB_ARGS + ["-N", "-B", "-e", sql],
                       capture_output=True, text=True, encoding="utf-8")
    if p.returncode != 0:
        raise SystemExit("查询失败：" + p.stderr)
    return [line.split("\t") for line in p.stdout.splitlines() if line]


def campus_id(name):
    if name and "鲅鱼圈" in name:
        return 1
    if name and "新民" in name:
        return 2
    return 1


def schedule_text(c):
    desc = (c.get("classTimeDescription") or "").strip()
    if desc:
        return desc
    name = c.get("className") or ""
    d = re.search(r"（?(周[一二三四五六日天])）?", name)
    t = re.search(r"(\d{1,2})\s*[：:]\s*(\d{2})", name)
    if d and t:
        return "%s %s:%s" % (d.group(1), t.group(1), t.group(2))
    return None


def date_only(value):
    s = (value or "").strip()[:10]
    return s if re.fullmatch(r"\d{4}-\d{2}-\d{2}", s) else ""


classes = json.load(open(os.path.join(BASE, "classes.json"), encoding="utf-8"))["rows"]

course_id = {row[1]: row[0] for row in one(
    "SELECT id, name FROM course WHERE ext_id LIKE 'SP-LESSON-%'")}
staff = one("SELECT id, name, IFNULL(campus_id, 1) FROM staff")
staff_by_name = {}
for sid, name, cid in staff:
    staff_by_name.setdefault(name, []).append((int(cid), sid))

existing = {}
ext_by_id = {}
for cid, ext, sp in one(
        "SELECT id, IFNULL(ext_id, ''), IFNULL(sp_class_id, 0) FROM class_group"):
    ext_by_id[cid] = ext
    if sp != "0":
        existing[("sp", sp)] = cid
    if ext:
        existing[("ext", ext)] = cid

lines = ["USE huancai_erp;", "START TRANSACTION;",
         "UPDATE class_group SET source = '校宝排课' WHERE source IS NULL OR source = '';"]
stats = {"updated": 0, "inserted": 0, "no_teacher": [], "no_course": []}
for idx, c in enumerate(classes, start=1):
    sp_id = str(c["id"])
    cid = existing.get(("sp", sp_id)) or existing.get(("ext", sp_id))
    ext_new = "SP-CLASS-%03d" % idx
    if not cid:
        cid = existing.get(("ext", ext_new))

    ext_keep = ext_by_id.get(cid, "") if cid else ""

    teacher = (c.get("teacherName") or "").strip()
    teacher_ref = "NULL"
    if teacher:
        cands = staff_by_name.get(teacher)
        if cands:
            wanted = campus_id(c.get("schoolName"))
            teacher_ref = sorted(cands, key=lambda x: 0 if x[0] == wanted else 1)[0][1]
        else:
            stats["no_teacher"].append(teacher)

    lesson_name = c.get("lessonName") or ""
    course_ref = course_id.get(lesson_name)
    if not course_ref:
        stats["no_course"].append(lesson_name)
    course_ref = course_ref or "NULL"

    stu = c.get("stuCount") or {}
    capacity = int(stu.get("maxStuCount") or 0)
    enrolled = int(stu.get("stuCount") or 0)
    rate = stu.get("rate")
    rate = str(round(float(rate), 2)) if isinstance(rate, (int, float)) else "NULL"
    fees = "；".join(c.get("feestandard") or [])
    status = "已开班" if c.get("status") == 1 else "已结班"
    create_time = date_only(c.get("createdTime"))
    name = q(c.get("className"), 64)
    room = q(c.get("classroom"), 64)
    stext = q(schedule_text(c), 128)
    start = q(date_only(c.get("openDate")))
    close = q(date_only(c.get("closeDate")))
    lesson_class = q(c.get("lessonClassName"), 64)
    assist = q(c.get("assistName"), 64)
    time_desc = q((c.get("classTimeDescription") or "")[:128], 128)
    up_rate = q(c.get("upRate"), 16)
    fee_standard = q(fees)
    create_by = q(c.get("createby"), 64)
    schedule_count = int(c.get("count") or 0)
    enrolling = 1 if c.get("enrolling") else 0
    is_collection = 1 if c.get("isCollection") else 0
    ext_val = q(ext_keep or ext_new, 64)

    if cid:
        lines.append(
            f"UPDATE class_group SET campus_id={campus_id(c.get('schoolName'))}, course_id={course_ref}, "
            f"teacher_id={teacher_ref}, teacher_text={q(teacher, 64)}, name={name}, "
            f"capacity={capacity}, enrolled_count={enrolled}, "
            f"room={room}, schedule_text={stext}, start_date={start}, end_date={close}, status={q(status, 16)}, "
            f"sp_class_id={sp_id}, lesson_class_name={lesson_class}, assist_name={assist}, "
            f"class_time_desc={time_desc}, stu_rate={rate}, up_rate={up_rate}, schedule_count={schedule_count}, "
            f"enrolling={enrolling}, create_by={create_by}, fee_standard={fee_standard}, "
            f"is_collection={is_collection}, source='校宝班级列表', ext_id={ext_val} WHERE id={cid};"
        )
        stats["updated"] += 1
    else:
        created = q(create_time + " 00:00:00") if create_time else "NOW()"
        lines.append(
            f"INSERT INTO class_group (campus_id, course_id, teacher_id, teacher_text, name, capacity, "
            f"enrolled_count, room, schedule_text, start_date, end_date, status, sp_class_id, "
            f"lesson_class_name, assist_name, class_time_desc, stu_rate, up_rate, schedule_count, enrolling, "
            f"create_by, fee_standard, is_collection, source, ext_id, created_at, updated_at) VALUES ("
            f"{campus_id(c.get('schoolName'))}, {course_ref}, {teacher_ref}, {q(teacher, 64)}, {name}, "
            f"{capacity}, {enrolled}, "
            f"{room}, {stext}, {start}, {close}, {q(status, 16)}, {sp_id}, {lesson_class}, {assist}, "
            f"{time_desc}, {rate}, {up_rate}, {schedule_count}, {enrolling}, {create_by}, {fee_standard}, "
            f"{is_collection}, '校宝班级列表', {q(ext_new, 64)}, {created}, {created});"
        )
        stats["inserted"] += 1

# 历史班级（由排课反推）的课次数按实际排课记录数补齐
lines.append(
    "UPDATE class_group cg SET schedule_count = "
    "(SELECT COUNT(*) FROM schedule s WHERE s.class_id = cg.id) "
    "WHERE cg.source = '校宝排课';"
)
lines.append("COMMIT;")
lines.append(
    "SELECT source, COUNT(*) classes, SUM(enrolled_count) enrolled, SUM(schedule_count) lessons, "
    "SUM(sp_class_id IS NOT NULL) with_sp_id FROM class_group GROUP BY source;"
)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

with open(OUT, "rb") as f:
    run = subprocess.run([MYSQL] + DB_ARGS, stdin=f, capture_output=True,
                         text=True, encoding="utf-8")
print("classes", len(classes), "updated", stats["updated"], "inserted", stats["inserted"])
print("no_teacher", stats["no_teacher"], "no_course", stats["no_course"])
print(run.stdout.strip())
if run.returncode != 0:
    raise SystemExit("执行导入失败：" + run.stderr)
print("sql", OUT, os.path.getsize(OUT))
