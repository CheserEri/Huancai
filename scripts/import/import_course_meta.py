"""从校宝「报读明细」导出补全 course 主数据：校区、授课模式、欠费。

- 数据源：data/schoolpal/enroll_20261025.xls（1,645 行 × 22 列）
- 匹配：course.name = 报读课程（67 门课程全部能匹配）
- 幂等：按课程名逐条 UPDATE，可反复重跑
"""
import collections
import os
import subprocess

import xlrd

XLS = r"d:\Code\Projects\Huancai\data\schoolpal\enroll_20261025.xls"
MYSQL = r"D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe"
DB_ARGS = ["-h", "127.0.0.1", "-P", "3307", "-u", "hc", "-phc123456",
           "-D", "huancai_erp", "--default-character-set=utf8mb4"]
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_course_meta.sql")


def txt(v):
    if isinstance(v, float) and abs(v - int(v)) < 1e-9:
        return str(int(v))
    return str(v).strip() if v is not None else ""


def q(s):
    if not s:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


sheet = xlrd.open_workbook(XLS).sheet_by_index(0)
header = [sheet.cell_value(0, c) for c in range(sheet.ncols)]
idx = {name: header.index(name) for name in
       ["报读课程", "报读校区", "授课模式", "欠费金额"]}

agg = collections.OrderedDict()
for r in range(1, sheet.nrows):
    name = txt(sheet.cell_value(r, idx["报读课程"]))
    if not name:
        continue
    item = agg.setdefault(name, {"campus": collections.Counter(), "mode": collections.Counter(),
                                 "debt": 0.0})
    campus = txt(sheet.cell_value(r, idx["报读校区"]))
    if campus:
        item["campus"][campus] += 1
    mode = txt(sheet.cell_value(r, idx["授课模式"]))
    if mode:
        item["mode"][mode] += 1
    try:
        item["debt"] += float(sheet.cell_value(r, idx["欠费金额"]) or 0)
    except (TypeError, ValueError):
        pass

lines = ["USE huancai_erp;", "START TRANSACTION;"]
for name, item in agg.items():
    campus_name = item["campus"].most_common(1)[0][0] if item["campus"] else ""
    campus_id = 1 if "鲅鱼圈" in campus_name else (2 if "新民" in campus_name else None)
    mode = item["mode"].most_common(1)[0][0] if item["mode"] else "班课"
    lines.append(
        "UPDATE course SET campus_id=%s, lesson_mode=%s, report_debt=%.2f WHERE name=%s;"
        % ("NULL" if campus_id is None else campus_id, q(mode), item["debt"], q(name))
    )
lines.append("COMMIT;")
lines.append(
    "SELECT COUNT(*) courses, SUM(campus_id=1) ba, SUM(campus_id=2) xm, "
    "SUM(campus_id IS NULL) no_campus, SUM(lesson_mode='一对一') one2one, "
    "SUM(lesson_mode='班课') group_class, SUM(report_debt) debt FROM course;"
)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

with open(OUT, "rb") as f:
    run = subprocess.run([MYSQL] + DB_ARGS, stdin=f, capture_output=True,
                         text=True, encoding="utf-8")
print("courses in xls", len(agg))
print(run.stdout.strip())
if run.returncode != 0:
    raise SystemExit("执行导入失败：" + run.stderr)
