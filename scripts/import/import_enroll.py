import os
import re
import xlrd

ROSTER = r"d:\Code\Projects\Huancai\data\schoolpal\student_roster_20261025.xls"
ENROLL = r"d:\Code\Projects\Huancai\data\schoolpal\enroll_20261025.xls"
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_enroll.sql")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

MARK = "[校宝报读导入]"


def txt(v):
    if v is None:
        return ""
    if isinstance(v, float) and abs(v - int(v)) < 1e-9:
        return str(int(v))
    return str(v).strip()


def num(v, default=0.0):
    try:
        return round(float(v), 2)
    except (TypeError, ValueError):
        return default


def q(s):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32)
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:500] + "'"


def digits(s):
    return re.sub(r"\D", "", s or "")


def parse_lessons(v):
    """'10.0000课时' -> 10.0 ; '365天' -> 0.0（按天计费的资料费类）"""
    s = txt(v)
    if "课时" in s:
        return num(s.replace("课时", "").strip())
    return 0.0


def campus_of(name):
    if "鲅鱼圈" in name:
        return 1
    if "新民" in name:
        return 2
    return None


def category_of(name):
    if "资料费" in name:
        return "资料费"
    if "阅读" in name:
        return "阅读"
    if "作文" in name:
        return "作文"
    if "英语" in name:
        return "英语"
    if "数学" in name:
        return "数学"
    if "国学" in name or "诗词" in name:
        return "国学"
    if "语文" in name or "文学素养" in name or "佳篇" in name or "大语文" in name:
        return "语文"
    if "一对一" in name:
        return "一对一"
    return "其他"


# ---------- 读取花名册学员索引 ----------
rb = xlrd.open_workbook(ROSTER)
rs = rb.sheet_by_index(0)
rh = [rs.cell_value(0, c) for c in range(rs.ncols)]


def rcol(n):
    return rh.index(n)


by_phone = {}
by_name = {}
for r in range(1, rs.nrows):
    name = txt(rs.cell_value(r, rcol("学员名")))
    if not name:
        continue
    for c in ("联系电话", "次要电话", "其他电话"):
        p = digits(txt(rs.cell_value(r, rcol(c))))
        if len(p) >= 7:
            by_phone.setdefault(p, name)
    by_name.setdefault(name, []).append(r)

# ---------- 读取报读 ----------
eb = xlrd.open_workbook(ENROLL)
es = eb.sheet_by_index(0)
eh = [es.cell_value(0, c) for c in range(es.ncols)]


def ecol(n):
    return eh.index(n)


courses = {}
stats = {"rows": 0, "match_phone": 0, "match_name": 0, "created": 0, "pending": []}

lines = [
    "USE huancai_erp;",
    "START TRANSACTION;",
    "DELETE FROM payment WHERE order_id IN (SELECT id FROM sales_order WHERE order_no LIKE 'QB-E-%');",
    "DELETE FROM lesson_txn WHERE biz_type = 'import' AND note LIKE '[校宝报读导入]%';",
    "DELETE FROM enrollment WHERE remark LIKE '[校宝报读导入]%';",
    "DELETE FROM sales_order WHERE order_no LIKE 'QB-E-%';",
]

# 先删后插，重建课程（仅更新导入课程）
for r in range(1, es.nrows):
    row = [es.cell_value(r, c) for c in range(es.ncols)]
    cname = txt(row[ecol("报读课程")])
    if not cname:
        continue
    if cname not in courses:
        courses[cname] = len(courses) + 1

# 课程插入（使用 ext_id 前缀 SPCOURSE 便于重跑：先按名称查找已有再更新单价无必要，这里直接插入并记录 id 映射）
lines.append("DELETE FROM course WHERE ext_id LIKE 'SP-COURSE-%';")
for idx, (cname, _) in enumerate(courses.items(), start=1):
    cid = campus_of(cname)
    lines.append(
        "INSERT INTO course (campus_id, name, category, lesson_minutes, status, ext_id) VALUES "
        f"({cid if cid else 'NULL'}, {q(cname)}, {q(category_of(cname))}, 90, 1, {q('SP-COURSE-%03d' % idx)});"
    )
    lines.append(f"SET @course_{idx} = LAST_INSERT_ID();")

for r in range(1, es.nrows):
    row = [es.cell_value(r, c) for c in range(es.ncols)]
    sname = txt(row[ecol("学员姓名")])
    if not sname:
        continue
    stats["rows"] += 1
    phone = digits(txt(row[ecol("联系电话")]))
    cname = txt(row[ecol("报读课程")])
    campus_name = txt(row[ecol("报读校区")])
    campus_id = campus_of(campus_name) or 1
    mode = txt(row[ecol("授课模式")])
    total = parse_lessons(row[ecol("购买课时/天数")])
    used = parse_lessons(row[ecol("已用课时/天数")])
    remain = parse_lessons(row[ecol("剩余课时/天数")])
    total_fee = num(row[ecol("总学费")])
    paid = num(row[ecol("实缴学费")])
    debt = num(row[ecol("欠费金额")])
    status_raw = txt(row[ecol("课程状态")])
    expire = txt(row[ecol("到期日期")])[:10] or None
    sign_date = txt(row[ecol("报名日期")])[:10] or None
    last_lesson = txt(row[ecol("最近上课日期")])[:10] or None

    status = {"在读": "生效", "停课": "停用", "已结课": "已退", "已退费": "已退"}.get(status_raw, "生效")

    # 学员定位
    target = by_phone.get(phone)
    lines.append("SET @sid = NULL;")
    if target:
        stats["match_phone"] += 1
        lines.append(f"SET @sid = (SELECT id FROM student WHERE name = {q(target)} ORDER BY id LIMIT 1);")
    elif sname in by_name:
        stats["match_name"] += 1
        lines.append(f"SET @sid = (SELECT id FROM student WHERE name = {q(sname)} ORDER BY id LIMIT 1);")
    else:
        stats["created"] += 1
        stats["pending"].append((sname, phone))
        lines.append(
            "INSERT INTO student (campus_id, name, gender, phone, status, source, remark) VALUES "
            f"({campus_id}, {q(sname)}, '未知', {q(phone)}, '在读', '校宝导入', {q(MARK + '花名册无记录，按报读数据补齐')});"
        )
        lines.append("SET @sid = LAST_INSERT_ID();")

    course_idx = courses.get(cname, 1)
    order_no = "QB-E-%05d" % stats["rows"]

    lines.append(
        "INSERT INTO enrollment (campus_id, student_id, course_id, total_lessons, used_lessons, remain_lessons, "
        "paid_amount, expire_date, status, remark) VALUES "
        f"({campus_id}, @sid, @course_{course_idx}, {total}, {used}, {remain}, {paid}, {q(expire)}, {q(status)}, "
        f"{q(MARK + '；授课模式：' + mode + ('；最近上课：' + last_lesson if last_lesson else ''))});"
    )
    lines.append("SET @enroll = LAST_INSERT_ID();")
    lines.append(
        "INSERT INTO lesson_txn (campus_id, enrollment_id, student_id, type, lessons, balance_after, biz_type, note) VALUES "
        f"({campus_id}, @enroll, @sid, '授予', {total}, {remain}, 'import', {q(MARK)});"
    )
    lines.append(
        "INSERT INTO sales_order (order_no, campus_id, student_id, type, content, receivable, received, discount, debt, "
        "status, paid_at, remark) VALUES "
        f"({q(order_no)}, {campus_id}, @sid, '报名', {q(cname)}, {total_fee}, {paid}, {round(total_fee - paid - debt, 2)}, {debt}, "
        f"{q('已完成' if debt <= 0 else '部分支付')}, {q(sign_date + ' 00:00:00' if sign_date else None)}, {q(MARK)});"
    )
    lines.append("SET @order = LAST_INSERT_ID();")
    if paid > 0:
        lines.append(
            "INSERT INTO payment (campus_id, order_id, student_id, amount, method, paid_at, remark) VALUES "
            f"({campus_id}, @order, @sid, {paid}, '—', {q(sign_date + ' 00:00:00' if sign_date else None)}, {q(MARK)});"
        )

lines.append("COMMIT;")

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print("rows", stats["rows"], "courses", len(courses))
print("match_phone", stats["match_phone"], "match_name", stats["match_name"], "created", stats["created"])
print("pending_sample", stats["pending"][:10])
print("sql", OUT, os.path.getsize(OUT))
