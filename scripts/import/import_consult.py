import json
import os
import re
from datetime import datetime, timezone, timedelta

SRC = r"d:\Code\Projects\Huancai\data\legacy\consult_raw.json"
REF_STAFF = os.path.join(os.environ.get("TEMP", "."), "hc-import", "ref_staff.tsv")
OUT = os.path.join(os.environ.get("TEMP", "."), "hc-import", "import_consult.sql")

CST = timezone(timedelta(hours=8))
SCHOOL_CAMPUS = {"620989": 1, "629815": 2}
STATE_TEXT = {1: "待跟进", 2: "跟进中", 3: "已到访", 4: "已成交", 5: "已失效", 6: "已邀约", 7: "已试听"}


def q(s, n=500):
    if s is None or s == "":
        return "NULL"
    s = "".join(ch for ch in str(s) if ch in "\n\t" or ord(ch) >= 32).strip()
    if not s:
        return "NULL"
    return "'" + s.replace("\\", "\\\\").replace("'", "''")[:n] + "'"


def num(v):
    if v is None or v == "":
        return "NULL"
    try:
        return str(int(v))
    except (TypeError, ValueError):
        return "NULL"


def dec(v):
    if v is None or v == "":
        return "NULL"
    try:
        return str(round(float(v), 2))
    except (TypeError, ValueError):
        return "NULL"


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


def dt(v):
    d = parse_ms(v)
    return q(d.strftime("%Y-%m-%d %H:%M:%S")) if d else "NULL"


def d8(v):
    d = parse_ms(v)
    return q(d.strftime("%Y-%m-%d")) if d else "NULL"


staff_by_name = {}
with open(REF_STAFF, "r", encoding="utf-8-sig") as f:
    for line in f:
        c = line.rstrip("\n").split("\t")
        if len(c) >= 2 and c[1].strip():
            staff_by_name[c[1].strip()] = int(c[0])

rows = json.load(open(SRC, encoding="utf-8-sig"))
vals = []
for it in rows:
    stuname = (it.get("StuName") or "").strip()
    if not stuname:
        continue
    campus = SCHOOL_CAMPUS.get(str(it.get("SchoolId") or it.get("AscriptionSchoolId")), 1)
    phone = (it.get("MotherTel") or it.get("FatherTel") or it.get("OtherTel") or "").strip()
    state = it.get("FollowUpState")
    state_txt = STATE_TEXT.get(state, "待跟进")
    enrolled = 1 if it.get("Enrolled") else 0
    status = "已成交" if enrolled else ("已到访" if state in (3, 7) else "待跟进")
    lesson = (it.get("LessonClassName") or it.get("SecondLessonClassName") or "").strip() or None
    vals.append(
        "({},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{})".format(
            campus,
            q(stuname, 64),
            q(phone, 32),
            q(stuname, 64),
            q(it.get("Method"), 32),
            q(lesson, 64),
            q(status, 16),
            "NULL",
            num(staff_by_name.get((it.get("SalesManViewName") or "").strip())),
            q(it.get("Comment"), 500),
            q(it.get("Sex"), 8),
            q(it.get("ParentName"), 64),
            q(it.get("MotherTel"), 32),
            q(it.get("FatherTel"), 32),
            q(it.get("OtherTel"), 32),
            q(it.get("SchoolName"), 64),
            q(it.get("Method"), 32),
            q(it.get("ChannelName"), 64),
            q(it.get("Salesway"), 32),
            q(it.get("SceneTitle"), 64),
            q(it.get("SalesManViewName"), 64),
            num(state),
            q(state_txt, 32),
            str(enrolled),
            q(it.get("Interest"), 32),
            q(it.get("Grade1"), 32),
            q(it.get("PubSchoolName"), 128),
            q(it.get("HomeAdd"), 255),
            d8(it.get("BirthDate")),
            dec(it.get("Arrearage")),
            dec(it.get("Remain")),
            q(it.get("LastCommuContent"), 500),
            dt(it.get("EditDate")),
            num(it.get("SchoolId")),
            q(str(it.get("Id")), 64),
        )
    )

COLS = (
    "campus_id,name,phone,child_name,source,intention_course,status,next_follow_at,consultant_id,"
    "remark,sex,parent_name,mother_tel,father_tel,other_tel,school_name,method,channel,salesway,scene,"
    "salesman_name,follow_state,follow_state_text,enrolled,interest,grade,pub_school,home_address,"
    "birthday,arrearage,remain_lessons,content,last_follow_at,ext_school_id,ext_id"
)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("SET NAMES utf8mb4;\n")
    f.write("DELETE FROM consult WHERE ext_id IS NOT NULL;\n")
    for i in range(0, len(vals), 400):
        f.write("INSERT INTO consult (" + COLS + ") VALUES\n")
        f.write(",\n".join(vals[i:i + 400]))
        f.write(";\n")

print("rows=%d out=%s" % (len(vals), OUT))
