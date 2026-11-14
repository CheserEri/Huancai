import json
import os
import re

BASE = r"d:\Code\Projects\Huancai\data\schoolpal"
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_staff_class.sql")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

employees = json.load(open(os.path.join(BASE, "employees.json"), encoding="utf-8"))["rows"]
classes = json.load(open(os.path.join(BASE, "classes.json"), encoding="utf-8"))["rows"]


def q(s):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:255] + "'"


def campus_id(name):
    if name and "鲅鱼圈" in name:
        return 1
    if name and "新民" in name:
        return 2
    return None


def role_of(e):
    roles = (e.get("users") or [{}])[0].get("roleNames") or ""
    if e.get("isAdmin") or "机构主管" in roles:
        return "admin"
    if "校区主管" in roles:
        return "manager"
    if e.get("isTeacher") == "是":
        return "teacher"
    if "财务" in (e.get("departmentName") or ""):
        return "finance"
    return "consultant"


def category_of(lesson_class_name, lesson_name):
    s = (lesson_class_name or "") + (lesson_name or "")
    for key in ("阅读", "作文", "数学", "英语", "国学", "语文"):
        if key in s:
            return key
    if "大语文" in s or "文学" in s:
        return "语文"
    return "其他"


lines = [
    "USE huancai_erp;",
    "START TRANSACTION;",
    "DELETE FROM class_group WHERE ext_id LIKE 'SP-CLASS-%';",
    "DELETE FROM course WHERE ext_id LIKE 'SP-LESSON-%';",
    "DELETE FROM staff WHERE ext_id LIKE 'SP-EMP-%';",
]

# 管理员账号补齐为真实身份（保留登录密码）
admin = next((e for e in employees if e.get("isAdmin")), None)
if admin:
    lines.append(
        "UPDATE staff SET name = %s, campus_id = 1, ext_id = %s, title = '机构主管' WHERE account = 'admin';"
        % (q(admin["name"]), q(str(admin["id"])))
    )

for idx, e in enumerate(employees, start=1):
    if admin and e.get("isAdmin"):
        continue
    cid = campus_id(e.get("departmentName"))
    lines.append(
        "INSERT INTO staff (campus_id, name, phone, role, title, status, ext_id) VALUES "
        f"({cid if cid else 'NULL'}, {q(e.get('name'))}, {q(e.get('mobile'))}, {q(role_of(e))}, "
        f"{q((e.get('users') or [{}])[0].get('roleNames'))}, {1 if e.get('isActive') else 0}, {q('SP-EMP-%02d' % idx)});"
    )

# 校宝“课程/科目”（班级挂在 lessonName 上）
lessons = {}
for c in classes:
    ln = c.get("lessonName")
    if ln and ln not in lessons:
        lessons[ln] = {"category": category_of(c.get("lessonClassName"), ln), "fees": c.get("feestandard") or []}

for idx, (ln, meta) in enumerate(lessons.items(), start=1):
    price = None
    for f in meta["fees"]:
        m = re.match(r"([\d.]+)课时=([\d.]+)元", f)
        if m and float(m.group(1)) == 1:
            price = float(m.group(2))
            break
    lines.append(
        "INSERT INTO course (campus_id, name, category, lesson_minutes, list_price, status, ext_id) VALUES "
        f"(NULL, {q(ln)}, {q(meta['category'])}, 90, {price if price is not None else 'NULL'}, 1, {q('SP-LESSON-%02d' % idx)});"
    )
    lines.append(f"SET @lesson_{idx} = LAST_INSERT_ID();")

lesson_idx = {name: i + 1 for i, name in enumerate(lessons.keys())}

for idx, c in enumerate(classes, start=1):
    cid = campus_id(c.get("schoolName"))
    if not cid:
        cid = 1
    teacher = c.get("teacherName") or ""
    start = (c.get("openDate") or "")[:10]
    close = (c.get("closeDate") or "")[:10]
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", start):
        start = None
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", close):
        close = None
    stu = c.get("stuCount") or {}
    cap = stu.get("maxStuCount") or 0
    enrolled = stu.get("stuCount") or 0
    name = c.get("className") or ""
    schedule_text = ""
    m = re.search(r"(\d{1,2})\s*[：:]\s*(\d{2})", name)
    d = re.search(r"（?(周[一二三四五六日天])）?", name)
    if d and m:
        schedule_text = f"{d.group(1)} {m.group(1)}:{m.group(2)}"
    status = "已开班" if c.get("status") == 1 else "已结班"
    course_ref = f"@lesson_{lesson_idx[c['lessonName']]}" if c.get("lessonName") in lesson_idx else "NULL"
    lines.append(
        "INSERT INTO class_group (campus_id, course_id, name, teacher_id, capacity, enrolled_count, room, "
        "schedule_text, start_date, end_date, status, ext_id) VALUES "
        f"({cid}, {course_ref}, {q(name)}, (SELECT id FROM staff WHERE name = {q(teacher)} ORDER BY id LIMIT 1), "
        f"{cap}, {enrolled}, {q(c.get('classroom'))}, {q(schedule_text)}, {q(start)}, {q(close)}, {q(status)}, {q('SP-CLASS-%03d' % idx)});"
    )

lines.append("COMMIT;")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print("employees", len(employees), "classes", len(classes), "lessons", len(lessons))
print("sql", OUT, os.path.getsize(OUT))
