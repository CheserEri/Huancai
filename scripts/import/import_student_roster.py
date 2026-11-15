"""把历史学员花名册（27 列）的字段导入 student 表。

- 数据源：data/legacy/student_roster_20261025.xls（3,400 行）
- 匹配方式：姓名 + 主要联系电话（唯一），退化到唯一姓名
- 幂等：按 student.id 逐行 UPDATE，可反复重跑
"""
import os
import re
import subprocess

import xlrd

ROSTER = r"d:\Code\Projects\Huancai\data\legacy\student_roster_20261025.xls"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_student_roster.sql")


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


def date_or_null(v):
    s = txt(v)
    if not s or s in ("未知", "无"):
        return "NULL"
    s = s[:10].replace("/", "-").replace(".", "-")
    if re.fullmatch(r"\d{4}-\d{1,2}-\d{1,2}", s):
        y, m, d = s.split("-")
        return "'%s-%02d-%02d'" % (y, int(m), int(d))
    return "NULL"


# ---------- 库内学员索引 ----------
proc = subprocess.run([MYSQL] + DB_ARGS + ["-N", "-B", "-e",
                                           "SELECT id, name, IFNULL(phone, '') FROM student"],
                      capture_output=True, text=True, encoding="utf-8")
if proc.returncode != 0:
    raise SystemExit("查询 student 失败：" + proc.stderr)

by_key = {}
by_name = {}
for line in proc.stdout.splitlines():
    parts = line.split("\t")
    if len(parts) < 3:
        continue
    sid, name, phone = parts[0], parts[1], digits(parts[2])
    by_key.setdefault((name, phone), []).append(int(sid))
    by_name.setdefault(name, []).append(int(sid))

# ---------- 读取花名册 ----------
book = xlrd.open_workbook(ROSTER)
sheet = book.sheet_by_index(0)
header = [sheet.cell_value(0, c) for c in range(sheet.ncols)]


def col(name):
    return header.index(name)


def col_endswith(suffix):
    """按列名后缀取列：源快照的列名前缀可能随来源变化，只依赖后缀匹配。"""
    for i, h in enumerate(header):
        if str(h).endswith(suffix):
            return i
    raise KeyError(suffix)


lines = ["USE huancai_erp;", "START TRANSACTION;"]
stats = {"rows": 0, "matched_key": 0, "matched_name": 0, "unmatched": [],
         "follow": 0, "card": 0, "course": 0, "points": 0, "balance": 0,
         "grade": 0, "debt": 0, "wechat": 0}

for r in range(1, sheet.nrows):
    name = txt(sheet.cell_value(r, col("学员名")))
    if not name:
        continue
    stats["rows"] += 1
    phone = digits(txt(sheet.cell_value(r, col("联系电话"))))
    sid = None
    if len(by_key.get((name, phone), [])) == 1:
        sid = by_key[(name, phone)][0]
        stats["matched_key"] += 1
    elif len(by_name.get(name, [])) == 1:
        sid = by_name[name][0]
        stats["matched_name"] += 1
    if sid is None:
        stats["unmatched"].append((name, phone))
        continue

    gender = txt(sheet.cell_value(r, col("性别"))) or "未知"
    primary_relation = txt(sheet.cell_value(r, col("所属关系(主要)")))
    secondary_relation = txt(sheet.cell_value(r, col("所属关系(次要)")))
    secondary_phone = digits(txt(sheet.cell_value(r, col("次要电话"))))
    other_relation = txt(sheet.cell_value(r, col("所属关系(其他)")))
    other_phone = digits(txt(sheet.cell_value(r, col("其他电话"))))
    follow = 1 if txt(sheet.cell_value(r, col_endswith("是否关注"))) == "关注" else 0
    card_no = txt(sheet.cell_value(r, col("磁卡绑定")))
    if card_no == "无":
        card_no = ""
    face_status = txt(sheet.cell_value(r, col("人脸录入"))) or "未录入"
    is_star = 1 if "星标" in txt(sheet.cell_value(r, col("是否星标"))) else 0
    status = txt(sheet.cell_value(r, col("学员状态"))) or "在读"
    campus_text = txt(sheet.cell_value(r, col("报读校区")))
    balance = num(sheet.cell_value(r, col("余额")))
    debt = num(sheet.cell_value(r, col("欠费")))
    course_count = int(num(sheet.cell_value(r, col("在读课程数"))))
    points = int(num(sheet.cell_value(r, col("积分"))))
    channel_name = txt(sheet.cell_value(r, col("渠道名称")))
    age_text = txt(sheet.cell_value(r, col("年龄")))
    grade = txt(sheet.cell_value(r, col("年级")))
    wechat = txt(sheet.cell_value(r, col("微信号")))
    birthday = date_or_null(sheet.cell_value(r, col("生日")))
    school = txt(sheet.cell_value(r, col("公立学校")))
    public_school_class = txt(sheet.cell_value(r, col("公立校班级")))
    address = txt(sheet.cell_value(r, col("居住区域")))
    extra_info = txt(sheet.cell_value(r, col("其他信息")))

    stats["follow"] += follow
    stats["card"] += 1 if card_no else 0
    stats["course"] += 1 if course_count > 0 else 0
    stats["points"] += 1 if points > 0 else 0
    stats["balance"] += 1 if balance > 0 else 0
    stats["grade"] += 1 if grade else 0
    stats["debt"] += 1 if debt > 0 else 0
    stats["wechat"] += 1 if wechat else 0

    lines.append(
        "UPDATE student SET "
        "campus_text=%s, gender=%s, phone=%s, primary_relation=%s, "
        "secondary_relation=%s, secondary_phone=%s, other_relation=%s, other_phone=%s, "
        "follow_status=%d, card_no=%s, face_status=%s, is_star=%d, balance=%s, debt=%s, "
        "points=%d, course_count=%d, status=%s, channel_name=%s, age_text=%s, grade=%s, "
        "wechat=%s, birthday=%s, school=%s, public_school_class=%s, address=%s, extra_info=%s "
        "WHERE id=%d;" % (
            q(campus_text), q(gender), q(phone), q(primary_relation),
            q(secondary_relation), q(secondary_phone), q(other_relation), q(other_phone),
            follow, q(card_no), q(face_status), is_star, balance, debt,
            points, course_count, q(status), q(channel_name), q(age_text), q(grade),
            q(wechat), birthday, q(school), q(public_school_class), q(address), q(extra_info),
            sid,
        )
    )

lines.append("COMMIT;")
lines.append(
    "SELECT COUNT(*) AS total, "
    "SUM(primary_relation IS NOT NULL) AS relation_filled, "
    "SUM(follow_status=1) AS followed, "
    "SUM(card_no IS NOT NULL AND card_no<>'') AS card_filled, "
    "SUM(course_count>0) AS course_filled, "
    "SUM(points>0) AS points_filled, "
    "SUM(balance>0) AS balance_filled, "
    "SUM(grade IS NOT NULL AND grade<>'') AS grade_filled, "
    "SUM(debt>0) AS debt_filled, "
    "SUM(campus_text IS NOT NULL) AS campus_text_filled "
    "FROM student;"
)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print("rows", stats["rows"], "matched_key", stats["matched_key"],
      "matched_name", stats["matched_name"], "unmatched", len(stats["unmatched"]))
print("roster non-empty -> follow", stats["follow"], "card", stats["card"],
      "course", stats["course"], "points", stats["points"],
      "balance", stats["balance"], "grade", stats["grade"],
      "debt", stats["debt"], "wechat", stats["wechat"])
if stats["unmatched"]:
    print("unmatched_sample", stats["unmatched"][:10])

with open(OUT, "rb") as sql_file:
    run = subprocess.run([MYSQL] + DB_ARGS, stdin=sql_file,
                         capture_output=True, text=True, encoding="utf-8")
print(run.stdout.strip())
if run.returncode != 0:
    raise SystemExit("执行导入失败：" + run.stderr)
print("sql", OUT, os.path.getsize(OUT))
