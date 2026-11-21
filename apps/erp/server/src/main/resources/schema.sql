-- 幻彩教育 ERP 数据库结构（MySQL 8.0，单租户，两校区）
-- 说明：所有业务表统一带 campus_id，便于按校区隔离与后续多校区扩展。
-- 历史导入数据通过 ext_id 保存原始系统主键，保证幂等重跑。

CREATE DATABASE IF NOT EXISTS huancai_erp
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE huancai_erp;

-- ---------------------------------------------------------------
-- 校区
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS campus (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)  NOT NULL COMMENT '校区编码',
  name        VARCHAR(64)  NOT NULL COMMENT '校区全称',
  short_name  VARCHAR(32)  DEFAULT NULL COMMENT '简称',
  address     VARCHAR(255) DEFAULT NULL,
  phone       VARCHAR(32)  DEFAULT NULL,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  ext_id      VARCHAR(64)  DEFAULT NULL COMMENT '原始ID',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_campus_code (code),
  KEY idx_campus_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校区';

-- ---------------------------------------------------------------
-- 员工（校长/教师/顾问/财务）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id     BIGINT       DEFAULT NULL,
  name          VARCHAR(64)  NOT NULL,
  phone         VARCHAR(32)  DEFAULT NULL,
  account       VARCHAR(64)  DEFAULT NULL COMMENT '登录账号',
  password_hash VARCHAR(100) DEFAULT NULL COMMENT 'BCrypt',
  role          VARCHAR(32)  NOT NULL DEFAULT 'teacher' COMMENT 'admin/manager/finance/reception/consultant/teacher/assistant',
  extra_roles   VARCHAR(128) DEFAULT NULL COMMENT '兼任角色，逗号分隔',
  title         VARCHAR(64)  DEFAULT NULL,
  menu_permissions TEXT      DEFAULT NULL COMMENT '可见菜单路径 JSON 数组，空表示仅工作台；admin 不受限制',
  permission_template_id BIGINT DEFAULT NULL COMMENT '绑定的权限模板；模板调整时自动同步权限',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1在职 0离职',
  ext_id        VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_staff_account (account),
  KEY idx_staff_campus (campus_id),
  KEY idx_staff_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工';

-- ---------------------------------------------------------------
-- 员工登录会话（token 凭证，7 天有效）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff_session (
  token       VARCHAR(64) NOT NULL COMMENT '登录令牌',
  staff_id    BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at  DATETIME    NOT NULL,
  PRIMARY KEY (token),
  KEY idx_session_staff (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工登录会话';

-- ---------------------------------------------------------------
-- 权限模板（组织架构 - 员工菜单权限预设）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permission_template (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64)  NOT NULL COMMENT '模板名称',
  role        VARCHAR(32)  DEFAULT NULL COMMENT '适用角色，空表示通用',
  builtin     TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置角色模板（不可删） 0自定义模板',
  permissions TEXT         DEFAULT NULL COMMENT '可见菜单路径 JSON 数组',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_permission_template_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限模板';

-- ---------------------------------------------------------------
-- 学员
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id           BIGINT       NOT NULL,
  campus_text         VARCHAR(64)  DEFAULT NULL COMMENT '历史花名册报读校区原文（可多校区）',
  ext_id              VARCHAR(64)  DEFAULT NULL COMMENT '历史学员ID',
  student_no          VARCHAR(64)  DEFAULT NULL COMMENT '学号',
  name                VARCHAR(64)  NOT NULL,
  gender              VARCHAR(8)   NOT NULL DEFAULT '未知' COMMENT '男/女/未知',
  birthday            DATE         DEFAULT NULL,
  age_text            VARCHAR(16)  DEFAULT NULL COMMENT '年龄（原始文，可为“未知”）',
  phone               VARCHAR(32)  DEFAULT NULL COMMENT '主要联系电话',
  primary_relation    VARCHAR(16)  DEFAULT NULL COMMENT '所属关系(主要)：父亲/母亲/其他',
  secondary_relation  VARCHAR(16)  DEFAULT NULL COMMENT '所属关系(次要)',
  secondary_phone     VARCHAR(32)  DEFAULT NULL COMMENT '次要电话',
  other_relation      VARCHAR(16)  DEFAULT NULL COMMENT '所属关系(其他)',
  other_phone         VARCHAR(32)  DEFAULT NULL COMMENT '其他电话',
  follow_status       TINYINT      NOT NULL DEFAULT 0 COMMENT '家长端是否关注 1是 0否',
  card_no             VARCHAR(32)  DEFAULT NULL COMMENT '磁卡绑定',
  face_status         VARCHAR(16)  DEFAULT NULL COMMENT '人脸录入状态',
  is_star             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否星标 1是 0否',
  balance             DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '账户余额（元）',
  debt                DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '欠费（元，历史花名册口径）',
  points              INT          NOT NULL DEFAULT 0 COMMENT '积分',
  course_count        INT          NOT NULL DEFAULT 0 COMMENT '在读课程数',
  status              VARCHAR(16)  NOT NULL DEFAULT '在读' COMMENT '在读/停课/休学/结课/意向/流失',
  source              VARCHAR(32)  DEFAULT NULL COMMENT '来源：转介绍/地推/美团/抖音/自然到访/老生续费',
  channel_name        VARCHAR(64)  DEFAULT NULL COMMENT '渠道名称（历史花名册）',
  school              VARCHAR(128) DEFAULT NULL COMMENT '公立学校',
  public_school_class VARCHAR(32)  DEFAULT NULL COMMENT '公立校班级',
  grade               VARCHAR(32)  DEFAULT NULL COMMENT '年级',
  wechat              VARCHAR(64)  DEFAULT NULL COMMENT '微信号',
  address             VARCHAR(255) DEFAULT NULL COMMENT '居住区域',
  extra_info          VARCHAR(500) DEFAULT NULL COMMENT '其他信息',
  enroll_date         DATE         DEFAULT NULL COMMENT '建档/报名日期',
  remark              VARCHAR(500) DEFAULT NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_student_campus (campus_id),
  KEY idx_student_name (name),
  KEY idx_student_phone (phone),
  KEY idx_student_status (status),
  KEY idx_student_ext (ext_id),
  KEY idx_student_card (card_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员';

-- ---------------------------------------------------------------
-- 家长/监护人
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guardian (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  student_id  BIGINT       NOT NULL,
  name        VARCHAR(64)  NOT NULL,
  relation    VARCHAR(16)  DEFAULT NULL COMMENT '父亲/母亲/祖父母/其他',
  phone       VARCHAR(32)  DEFAULT NULL,
  is_primary  TINYINT      NOT NULL DEFAULT 0,
  ext_id      VARCHAR(64)  DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_guardian_student (student_id),
  KEY idx_guardian_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长';

-- ---------------------------------------------------------------
-- 课程
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id       BIGINT        DEFAULT NULL,
  code            VARCHAR(32)   DEFAULT NULL,
  name            VARCHAR(64)   NOT NULL,
  category        VARCHAR(32)   DEFAULT NULL COMMENT '国学/书法/围棋/阅读等',
  subject         VARCHAR(64)   DEFAULT NULL COMMENT '科目',
  lesson_minutes  INT           NOT NULL DEFAULT 90 COMMENT '单课时长（分钟）',
  total_lessons   DECIMAL(6,1)  DEFAULT NULL COMMENT '标准总课时',
  list_price      DECIMAL(10,2) DEFAULT NULL COMMENT '标准单价/总价',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=开售 0=停售',
  remark          VARCHAR(255)  DEFAULT NULL,
  ext_id          VARCHAR(64)   DEFAULT NULL,
  lesson_mode     VARCHAR(16)   DEFAULT NULL COMMENT '班课/一对一',
  campus_scope    VARCHAR(16)   NOT NULL DEFAULT '全部' COMMENT '全部/指定',
  lesson_charge   TINYINT       NOT NULL DEFAULT 1 COMMENT '按课时收费',
  time_charge     TINYINT       NOT NULL DEFAULT 0 COMMENT '按时间收费',
  period_charge   TINYINT       NOT NULL DEFAULT 0 COMMENT '按期收费',
  material_fee    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '教材杂费',
  report_debt     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '报读欠费',
  default_price_template_id BIGINT DEFAULT NULL COMMENT '默认基础价格模板',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_course_campus (campus_id),
  KEY idx_course_name (name),
  KEY idx_course_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程';

-- ---------------------------------------------------------------
-- 课程定价标准（按课时收费时按开课校区维护）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_price (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  course_id   BIGINT        NOT NULL,
  campus_id   BIGINT        DEFAULT NULL COMMENT '为空表示全部校区',
  lessons     DECIMAL(6,2)  NOT NULL DEFAULT 0 COMMENT '数量（课时）',
  total_price DECIMAL(10,2) DEFAULT NULL COMMENT '总价（元）',
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cprice_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程定价标准';

-- ---------------------------------------------------------------
-- 课程类别 / 科目 / 升期关系
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_category (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_category (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程类别';

CREATE TABLE IF NOT EXISTS course_subject (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL,
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1=正常 0=停用',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_subject (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科目';

CREATE TABLE IF NOT EXISTS course_upgrade (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  name           VARCHAR(64) NOT NULL COMMENT '升期关系名称',
  from_course_id BIGINT      DEFAULT NULL,
  to_course_id   BIGINT      DEFAULT NULL,
  period_count   INT         NOT NULL DEFAULT 0 COMMENT '期数',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cup_from (from_course_id),
  KEY idx_cup_to (to_course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='升期关系';

-- ---------------------------------------------------------------
-- 班级
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_group (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id         BIGINT       NOT NULL,
  course_id         BIGINT       DEFAULT NULL,
  name              VARCHAR(64)  NOT NULL,
  teacher_id        BIGINT       DEFAULT NULL,
  teacher_text      VARCHAR(64)  DEFAULT NULL COMMENT '历史教师姓名原文',
  capacity          INT          NOT NULL DEFAULT 0,
  enrolled_count    INT          NOT NULL DEFAULT 0,
  room              VARCHAR(64)  DEFAULT NULL,
  schedule_text     VARCHAR(128) DEFAULT NULL COMMENT '如 周三 17:30-19:00',
  start_date        DATE         DEFAULT NULL,
  end_date          DATE         DEFAULT NULL,
  status            VARCHAR(16)  NOT NULL DEFAULT '已开班' COMMENT '开放招生/已开班/满班/已结班',
  sp_class_id       BIGINT       DEFAULT NULL COMMENT '历史班级ID',
  lesson_class_name VARCHAR(64)  DEFAULT NULL COMMENT '班型',
  assist_name       VARCHAR(64)  DEFAULT NULL COMMENT '助教',
  class_time_desc   VARCHAR(128) DEFAULT NULL COMMENT '原系统上课时间原文',
  stu_rate          DECIMAL(5,2) DEFAULT NULL COMMENT '满班率%',
  up_rate           VARCHAR(16)  DEFAULT NULL COMMENT '升班率',
  schedule_count    INT          NOT NULL DEFAULT 0 COMMENT '课次数（历史班级列表口径）',
  enrolling         TINYINT      NOT NULL DEFAULT 1 COMMENT '招生中 1是 0否',
  create_by         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  fee_standard      VARCHAR(255) DEFAULT NULL COMMENT '收费标准',
  is_collection     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否合集',
  source            VARCHAR(16)  NOT NULL DEFAULT '历史排课' COMMENT '历史班级列表/历史排课',
  ext_id            VARCHAR(64)  DEFAULT NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_class_campus (campus_id),
  KEY idx_class_course (course_id),
  KEY idx_class_ext (ext_id),
  KEY idx_class_sp (sp_class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级';

-- ---------------------------------------------------------------
-- 班级学员（在读名单快照，来自历史班级学员列表）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_student (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id      BIGINT        NOT NULL,
  class_id       BIGINT        NOT NULL,
  student_id     BIGINT        NOT NULL,
  status         VARCHAR(16)   NOT NULL DEFAULT '在读',
  join_date      DATETIME      DEFAULT NULL,
  total_lessons  DECIMAL(6,1)  NOT NULL DEFAULT 0,
  used_lessons   DECIMAL(6,1)  NOT NULL DEFAULT 0,
  remain_lessons DECIMAL(6,1)  NOT NULL DEFAULT 0,
  remark         VARCHAR(255)  DEFAULT NULL,
  ext_id         VARCHAR(64)   DEFAULT NULL COMMENT '历史班级学员关系ID',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_class_student (class_id, student_id),
  KEY idx_cs_student (student_id),
  KEY idx_cs_campus (campus_id),
  KEY idx_cs_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级学员';

-- ---------------------------------------------------------------
-- 报读 / 课时账户
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollment (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id      BIGINT        NOT NULL,
  student_id     BIGINT        NOT NULL,
  course_id      BIGINT        NOT NULL,
  class_id       BIGINT        DEFAULT NULL,
  order_id       BIGINT        DEFAULT NULL,
  total_lessons  DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '购买课时',
  gift_lessons   DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '赠送课时',
  used_lessons   DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '已消耗课时',
  remain_lessons DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '剩余课时',
  paid_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '报读累计优惠金额',
  consumed_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已课耗金额',
  unit_price     DECIMAL(10,2) DEFAULT NULL COMMENT '课耗单价',
  price_template_id BIGINT    DEFAULT NULL COMMENT '本次报读使用的价格模板',
  expire_date    DATE          DEFAULT NULL,
  status         VARCHAR(16)   NOT NULL DEFAULT '生效' COMMENT '生效/停用/已退/已转',
  remark         VARCHAR(255)  DEFAULT NULL,
  ext_id         VARCHAR(64)   DEFAULT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_enroll_student (student_id),
  KEY idx_enroll_course (course_id),
  KEY idx_enroll_class (class_id),
  KEY idx_enroll_campus (campus_id),
  KEY idx_enroll_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报读/课时账户';

-- ---------------------------------------------------------------
-- 课时流水（只追加）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lesson_txn (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id       BIGINT       NOT NULL,
  enrollment_id   BIGINT       NOT NULL,
  student_id      BIGINT       NOT NULL,
  type            VARCHAR(16)  NOT NULL COMMENT '授予/赠送/扣减/退课/调整',
  lessons         DECIMAL(6,1) NOT NULL COMMENT '正数增加，负数扣减',
  unit_price      DECIMAL(10,2) DEFAULT NULL COMMENT '本次单价',
  amount          DECIMAL(12,2) DEFAULT NULL COMMENT '本次金额',
  balance_after   DECIMAL(6,1) DEFAULT NULL,
  biz_type        VARCHAR(32)  DEFAULT NULL COMMENT 'order/attendance/adjust',
  biz_id          BIGINT       DEFAULT NULL,
  note            VARCHAR(255) DEFAULT NULL,
  operator_id     BIGINT       DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_txn_enroll (enrollment_id),
  KEY idx_txn_student (student_id, created_at),
  KEY idx_txn_campus (campus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课时流水';

-- ---------------------------------------------------------------
-- 排课 / 课次
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schedule (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id   BIGINT       NOT NULL,
  class_id    BIGINT       DEFAULT NULL COMMENT '一对一时为空',
  course_id   BIGINT       DEFAULT NULL,
  teacher_id  BIGINT       DEFAULT NULL,
  student_id  BIGINT       DEFAULT NULL COMMENT '一对一课次',
  lesson_date DATE         NOT NULL,
  start_time  TIME         NOT NULL,
  end_time    TIME         NOT NULL,
  room        VARCHAR(64)  DEFAULT NULL,
  topic       VARCHAR(128) DEFAULT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT '待上课' COMMENT '待上课/已上课/已取消',
  class_name     VARCHAR(128) DEFAULT NULL COMMENT '原始班级名称快照',
  course_name    VARCHAR(128) DEFAULT NULL COMMENT '原始课程名称快照',
  teacher_name   VARCHAR(64)  DEFAULT NULL COMMENT '原始教师名称快照',
  assistant_name VARCHAR(64)  DEFAULT NULL COMMENT '原始助教名称快照',
  lesson_mode    TINYINT      DEFAULT NULL COMMENT '1=班级 2=一对一',
  teaching_log_id BIGINT      DEFAULT NULL COMMENT '原系统记上课ID，>0 表示已上课',
  should_count   INT          DEFAULT NULL COMMENT '应到人数',
  actual_count   INT          DEFAULT NULL COMMENT '实到人数',
  period_start   DATETIME     DEFAULT NULL,
  period_end     DATETIME     DEFAULT NULL,
  ext_class_id   BIGINT       DEFAULT NULL COMMENT '历史班级ID',
  ext_teacher_id BIGINT       DEFAULT NULL COMMENT '历史教师ID',
  ext_assistant_id BIGINT     DEFAULT NULL COMMENT '历史助教ID',
  ext_school_id  BIGINT       DEFAULT NULL COMMENT '历史校区ID',
  ext_id      VARCHAR(64)  DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_schedule_date (lesson_date),
  KEY idx_schedule_class (class_id),
  KEY idx_schedule_teacher (teacher_id),
  KEY idx_schedule_campus (campus_id),
  KEY idx_schedule_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课';

-- ---------------------------------------------------------------
-- 上课记录 / 点名
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id     BIGINT       NOT NULL,
  schedule_id   BIGINT       DEFAULT NULL,
  class_id      BIGINT       DEFAULT NULL,
  student_id    BIGINT       NOT NULL,
  enrollment_id BIGINT       DEFAULT NULL,
  teacher_id    BIGINT       DEFAULT NULL,
  lesson_date   DATE         NOT NULL,
  lessons       DECIMAL(6,1) NOT NULL DEFAULT 1 COMMENT '扣减课时，0 表示请假/缺席',
  status        VARCHAR(16)  NOT NULL DEFAULT '出勤' COMMENT '出勤/请假/缺席/迟到',
  checked_at    DATETIME     DEFAULT NULL,
  remark        VARCHAR(255) DEFAULT NULL,
  ext_id        VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_att_student (student_id, lesson_date),
  KEY idx_att_schedule (schedule_id),
  KEY idx_att_class (class_id),
  KEY idx_att_campus (campus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上课记录';

-- ---------------------------------------------------------------
-- 课堂点评
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_comment (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id     BIGINT       NOT NULL,
  attendance_id BIGINT       DEFAULT NULL,
  schedule_id   BIGINT       DEFAULT NULL,
  class_id      BIGINT       DEFAULT NULL,
  student_id    BIGINT       NOT NULL,
  teacher_id    BIGINT       DEFAULT NULL,
  content       TEXT         DEFAULT NULL,
  rating        VARCHAR(16)  DEFAULT NULL COMMENT '优秀/良好/需加强',
  read_at       DATETIME     DEFAULT NULL,
  ext_id        VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_comment_student (student_id),
  KEY idx_comment_class (class_id),
  KEY idx_comment_campus (campus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课堂点评';

-- ---------------------------------------------------------------
-- 课次整体点评（一堂课共用一条，与学员逐人点评分离）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_lesson_comment (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  campus_id   BIGINT      DEFAULT NULL,
  class_id    BIGINT      NOT NULL,
  schedule_id BIGINT      NOT NULL,
  teacher_id  BIGINT      DEFAULT NULL,
  rating      VARCHAR(16) DEFAULT NULL COMMENT '整体评价：优秀/良好/需加强',
  content     TEXT        DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lesson_comment (schedule_id),
  KEY idx_lesson_comment_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次整体点评';

-- ---------------------------------------------------------------
-- 班级相册
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_photo (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id  BIGINT       NOT NULL,
  class_id   BIGINT       NOT NULL,
  url        VARCHAR(500) NOT NULL COMMENT '文件访问地址',
  uploader   VARCHAR(64)  DEFAULT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_class_photo_class (class_id, created_at),
  KEY idx_class_photo_campus (campus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级相册';

-- ---------------------------------------------------------------
-- 订单
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sales_order (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(32)   NOT NULL,
  campus_id    BIGINT        NOT NULL,
  student_id   BIGINT        NOT NULL,
  type         VARCHAR(16)   NOT NULL COMMENT '报名/续费/补费/退费/转课/充值',
  content      VARCHAR(255)  DEFAULT NULL,
  receivable   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '应收',
  received     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实收，退费为负',
  discount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠',
  debt         DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '欠费',
  handler_id   BIGINT        DEFAULT NULL,
  pay_method   VARCHAR(16)   DEFAULT NULL COMMENT '微信/支付宝/现金/银行卡/—',
  status       VARCHAR(16)   NOT NULL DEFAULT '已完成' COMMENT '待支付/部分支付/已完成/已退款/已作废',
  paid_at      DATETIME      DEFAULT NULL,
  remark       VARCHAR(255)  DEFAULT NULL,
  ext_id       VARCHAR(64)   DEFAULT NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_student (student_id),
  KEY idx_order_campus (campus_id),
  KEY idx_order_date (created_at),
  KEY idx_order_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- ---------------------------------------------------------------
-- 历史订单快照（订单列表 / 订单详情只读数据源）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sp_order (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  sp_id               BIGINT        DEFAULT NULL COMMENT '历史订单ID；本地闭环办理的订单为空',
  order_no            VARCHAR(32)   NOT NULL COMMENT '订单号',
  order_no_num        BIGINT        GENERATED ALWAYS AS (CAST(order_no AS UNSIGNED)) STORED COMMENT '订单号数值，用于排序',
  campus_id           BIGINT        DEFAULT NULL,
  school_id           BIGINT        DEFAULT NULL COMMENT '历史校区ID',
  campus_name         VARCHAR(100)  DEFAULT NULL,
  student_id          BIGINT        DEFAULT NULL COMMENT '对应本地学员，匹配不到为空',
  stu_name            VARCHAR(100)  NOT NULL,
  stu_tel             VARCHAR(50)   DEFAULT NULL,
  stu_name_pinyin     VARCHAR(200)  DEFAULT NULL COMMENT '姓名全拼+首字母，用于拼音简写检索',
  type_code           INT           NOT NULL COMMENT '1报名2账户余额变动3转课4停课5复课6结课7退费8教材杂费9积分10补费',
  content             TEXT          DEFAULT NULL,
  should_pay          DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '应收/应退',
  real_pay            DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实收/实退',
  remain              DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '学员账户变动',
  arrearage           DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '欠费',
  credit              DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '积分',
  source_type         INT           DEFAULT NULL COMMENT '0-5 订单来源',
  salesman            VARCHAR(50)   DEFAULT NULL,
  creator             VARCHAR(50)   DEFAULT NULL,
  sales_source        VARCHAR(255)  DEFAULT NULL,
  tags                VARCHAR(255)  DEFAULT NULL,
  is_return           TINYINT       NOT NULL DEFAULT 0,
  status_code         INT           NOT NULL COMMENT '1待支付2已完成3已作废4已失效',
  contract_status     INT           NOT NULL DEFAULT 0,
  pay_method          VARCHAR(32)   DEFAULT NULL,
  comment             VARCHAR(500)  DEFAULT NULL COMMENT '对内备注',
  comment_outer       VARCHAR(500)  DEFAULT NULL COMMENT '对外备注',
  has_confirmed_detail TINYINT      NOT NULL DEFAULT 0,
  has_textbook        TINYINT       NOT NULL DEFAULT 0,
  is_online_payment   TINYINT       NOT NULL DEFAULT 0,
  has_multi_lesson_enroll TINYINT   NOT NULL DEFAULT 0,
  deal_date           DATETIME      NOT NULL COMMENT '经办日期',
  created_time        DATETIME      NOT NULL COMMENT '原系统创建时间',
  updated_time        DATETIME      DEFAULT NULL COMMENT '最后修改时间',
  expiry_date         DATETIME      DEFAULT NULL,
  raw_json            LONGTEXT      DEFAULT NULL COMMENT '原始订单 JSON',
  void_reason         VARCHAR(100)  DEFAULT NULL COMMENT '作废原因',
  void_at             DATETIME      DEFAULT NULL COMMENT '作废时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sp_order_sp_id (sp_id),
  UNIQUE KEY uk_sp_order_no (order_no),
  KEY idx_sp_order_no_num (order_no_num),
  KEY idx_sp_order_deal_date (deal_date),
  KEY idx_sp_order_student (student_id),
  KEY idx_sp_order_campus (campus_id),
  KEY idx_sp_order_status (status_code),
  KEY idx_sp_order_type (type_code),
  KEY idx_sp_order_creator (creator),
  KEY idx_sp_order_salesman (salesman)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史订单快照';

-- ---------------------------------------------------------------
-- 收款记录
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id    BIGINT        NOT NULL,
  order_id     BIGINT        NOT NULL,
  student_id   BIGINT        NOT NULL,
  amount       DECIMAL(10,2) NOT NULL COMMENT '收款金额，退款为负',
  method       VARCHAR(16)   DEFAULT NULL,
  paid_at      DATETIME      NOT NULL,
  operator_id  BIGINT        DEFAULT NULL,
  voucher_no   VARCHAR(64)   DEFAULT NULL,
  remark       VARCHAR(255)  DEFAULT NULL,
  ext_id       VARCHAR(64)   DEFAULT NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_payment_order (order_id),
  KEY idx_payment_student (student_id),
  KEY idx_payment_date (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款记录';

-- ---------------------------------------------------------------
-- 咨询/线索
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consult (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  campus_id        BIGINT       NOT NULL,
  name             VARCHAR(64)  NOT NULL COMMENT '联系人',
  phone            VARCHAR(32)  DEFAULT NULL,
  child_name       VARCHAR(64)  DEFAULT NULL,
  source           VARCHAR(32)  DEFAULT NULL,
  intention_course VARCHAR(64)  DEFAULT NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT '待跟进' COMMENT '待跟进/已到访/已成交/已失效',
  next_follow_at   DATETIME     DEFAULT NULL,
  consultant_id    BIGINT       DEFAULT NULL,
  remark           VARCHAR(500) DEFAULT NULL,
  sex              VARCHAR(8)   DEFAULT NULL,
  parent_name      VARCHAR(64)  DEFAULT NULL,
  mother_tel       VARCHAR(32)  DEFAULT NULL,
  father_tel       VARCHAR(32)  DEFAULT NULL,
  other_tel        VARCHAR(32)  DEFAULT NULL,
  school_name      VARCHAR(64)  DEFAULT NULL COMMENT '校区名称',
  method           VARCHAR(32)  DEFAULT NULL COMMENT '来源方式，如 来电/到访',
  channel          VARCHAR(64)  DEFAULT NULL,
  salesway         VARCHAR(32)  DEFAULT NULL,
  scene            VARCHAR(64)  DEFAULT NULL,
  salesman_name    VARCHAR(64)  DEFAULT NULL COMMENT '招生顾问',
  follow_state     TINYINT      DEFAULT NULL COMMENT '原系统跟进状态码',
  follow_state_text VARCHAR(32) DEFAULT NULL,
  enrolled         TINYINT(1)   DEFAULT 0,
  interest         VARCHAR(32)  DEFAULT NULL,
  grade            VARCHAR(32)  DEFAULT NULL,
  pub_school       VARCHAR(128) DEFAULT NULL COMMENT '公立学校',
  home_address     VARCHAR(255) DEFAULT NULL,
  birthday         DATE         DEFAULT NULL,
  arrearage        DECIMAL(10,2) DEFAULT NULL,
  remain_lessons   DECIMAL(10,2) DEFAULT NULL,
  content          VARCHAR(500) DEFAULT NULL COMMENT '最近沟通内容',
  last_follow_at   DATETIME     DEFAULT NULL,
  ext_school_id    BIGINT       DEFAULT NULL,
  ext_id           VARCHAR(64)  DEFAULT NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_consult_campus (campus_id),
  KEY idx_consult_phone (phone),
  KEY idx_consult_status (status),
  KEY idx_consult_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询线索';

-- ---------------------------------------------------------------
-- 基础数据：单校区（鲅鱼圈幻彩学堂）
-- ---------------------------------------------------------------
INSERT INTO campus (code, name, short_name, status)
SELECT 'byq', '鲅鱼圈幻彩学堂', '鲅鱼圈', 1
WHERE NOT EXISTS (SELECT 1 FROM campus WHERE code = 'byq');

-- ---------------------------------------------------------------
-- 营销中心（满折 / 满减 / 赠送课时）
-- 数据本地闭环，不回写原系统
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketing_activity (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  name          VARCHAR(120)  NOT NULL COMMENT '活动名称',
  type          VARCHAR(16)   NOT NULL COMMENT '满折/满减/赠送课时',
  status        VARCHAR(16)   NOT NULL DEFAULT '生效中' COMMENT '未开始/生效中/已结束/已下架',
  start_time    DATETIME      DEFAULT NULL COMMENT '活动开始时间',
  end_time      DATETIME      DEFAULT NULL COMMENT '活动结束时间，空表示长期有效',
  rules_json    TEXT          DEFAULT NULL COMMENT '优惠层级配置 JSON',
  discount_text VARCHAR(64)   DEFAULT NULL COMMENT '优惠展示文案',
  campus_scope  VARCHAR(8)    NOT NULL DEFAULT '全部' COMMENT '全部/指定',
  campus_ids    VARCHAR(255)  DEFAULT NULL COMMENT '指定校区ID，逗号分隔',
  course_scope  VARCHAR(8)    NOT NULL DEFAULT '全部' COMMENT '全部/指定',
  course_ids    VARCHAR(255)  DEFAULT NULL COMMENT '指定课程ID，逗号分隔',
  trade_count   INT           NOT NULL DEFAULT 0 COMMENT '付款交易数',
  paid_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
  remark        VARCHAR(255)  DEFAULT NULL,
  ext_id        VARCHAR(64)   DEFAULT NULL COMMENT '原始ID',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mk_type (type),
  KEY idx_mk_status (status),
  KEY idx_mk_ext (ext_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动';

-- ---------------------------------------------------------------
-- 基础价格模板：价格与课程绑定，课程本身不再承载价格
-- 模板只停用不物理删除，订单保存模板明文快照
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS price_template (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  name          VARCHAR(120)  NOT NULL COMMENT '模板名称',
  campus_scope  VARCHAR(8)    NOT NULL DEFAULT '全部' COMMENT '全部/指定',
  campus_ids    VARCHAR(255)  DEFAULT NULL COMMENT '指定校区ID，逗号分隔',
  class_type    VARCHAR(64)   DEFAULT NULL COMMENT '班型，空表示不限',
  material_fee  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '教材杂费',
  valid_days    INT           DEFAULT NULL COMMENT '报读有效期天数，空表示不限',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark        VARCHAR(255)  DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础价格模板';

CREATE TABLE IF NOT EXISTS price_template_course (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  template_id  BIGINT      NOT NULL,
  course_id    BIGINT      NOT NULL,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ptc (template_id, course_id),
  KEY idx_ptc_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格模板适用课程';

CREATE TABLE IF NOT EXISTS price_template_price (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  template_id  BIGINT        NOT NULL,
  lessons      DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '课时档位',
  unit_price   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '课时单价',
  total_price  DECIMAL(10,2) DEFAULT NULL COMMENT '档位总价',
  sort_no      INT           NOT NULL DEFAULT 0,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ptp_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格模板课时档位';

CREATE TABLE IF NOT EXISTS activity_category (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  name         VARCHAR(64) NOT NULL COMMENT '分类名称，如1-3年级',
  parent_id    BIGINT      DEFAULT NULL COMMENT '上级分类ID，空表示一级分类',
  sort_no      INT         NOT NULL DEFAULT 0,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ac_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板分类（支持层级）';

CREATE TABLE IF NOT EXISTS activity_template (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  category_id  BIGINT       DEFAULT NULL COMMENT '所属活动分类',
  name         VARCHAR(120) NOT NULL COMMENT '活动名称',
  type         VARCHAR(16)  NOT NULL COMMENT '节日活动/报课优惠',
  start_date   DATE         DEFAULT NULL,
  end_date     DATE         DEFAULT NULL,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark       VARCHAR(255) DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_at_category (category_id),
  KEY idx_at_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板';

CREATE TABLE IF NOT EXISTS activity_template_rule (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  template_id    BIGINT        NOT NULL,
  lessons        DECIMAL(6,1)  NOT NULL DEFAULT 0 COMMENT '报课优惠的课时门槛，节日活动为0',
  discount_mode  VARCHAR(16)   NOT NULL COMMENT '折扣/立减/赠送课时',
  discount_value DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠值',
  sort_no        INT           NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_atr_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动模板优惠规则';

CREATE TABLE IF NOT EXISTS sp_order_item (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  order_id              BIGINT        NOT NULL,
  enrollment_id         BIGINT        DEFAULT NULL,
  course_id             BIGINT        DEFAULT NULL,
  course_name           VARCHAR(64)   DEFAULT NULL,
  price_template_id     BIGINT        DEFAULT NULL,
  price_template_name   VARCHAR(120)  DEFAULT NULL,
  activity_template_id  BIGINT        DEFAULT NULL,
  activity_template_name VARCHAR(120) DEFAULT NULL,
  activity_template_ids VARCHAR(255)  DEFAULT NULL COMMENT '使用的活动模板ID（逗号分隔）',
  lessons               DECIMAL(6,1)  NOT NULL DEFAULT 0,
  unit_price            DECIMAL(10,2) NOT NULL DEFAULT 0,
  base_amount           DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '课时应收',
  discount_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  material_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '教材杂费',
  amount                DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本项应收合计',
  created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_soi_order (order_id),
  KEY idx_soi_enrollment (enrollment_id),
  KEY idx_soi_template (price_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细与模板快照';

CREATE TABLE IF NOT EXISTS student_operation_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id      BIGINT        DEFAULT NULL,
  student_id     BIGINT        NOT NULL,
  type           VARCHAR(32)   NOT NULL,
  content        VARCHAR(1000) NOT NULL,
  operator_id    BIGINT        DEFAULT NULL,
  operator_name  VARCHAR(64)   DEFAULT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sol_student (student_id),
  KEY idx_sol_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员操作日志';

CREATE TABLE IF NOT EXISTS data_fee_template (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  name          VARCHAR(100)  NOT NULL,
  price         DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '资料费价格',
  cycle_months  INT           NOT NULL DEFAULT 1 COMMENT '缴纳周期（月）',
  status        VARCHAR(20)   NOT NULL DEFAULT '启用',
  remark        VARCHAR(255)  DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料费缴纳模板（A4打印资料，无库存）';

CREATE TABLE IF NOT EXISTS data_fee_payment (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id     BIGINT        DEFAULT NULL,
  campus_name   VARCHAR(100)  DEFAULT NULL,
  student_id    BIGINT        NOT NULL,
  student_name  VARCHAR(50)   DEFAULT NULL,
  enrollment_id BIGINT        DEFAULT NULL,
  course_id     BIGINT        DEFAULT NULL,
  course_name   VARCHAR(100)  DEFAULT NULL,
  template_id   BIGINT        DEFAULT NULL,
  template_name VARCHAR(100)  DEFAULT NULL,
  price         DECIMAL(10,2) NOT NULL DEFAULT 0,
  cycle_months  INT           NOT NULL DEFAULT 1,
  period_start  DATE          DEFAULT NULL,
  period_end    DATE          DEFAULT NULL,
  paid_at       DATETIME      DEFAULT NULL,
  pay_method    VARCHAR(20)   DEFAULT NULL,
  handler       VARCHAR(50)   DEFAULT NULL,
  order_id      BIGINT        DEFAULT NULL,
  order_no      VARCHAR(50)   DEFAULT NULL,
  remark        VARCHAR(255)  DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dfp_student (student_id),
  KEY idx_dfp_enrollment (enrollment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料费缴纳记录';

CREATE TABLE IF NOT EXISTS points_goods (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  name          VARCHAR(120)  NOT NULL COMMENT '兑换物名称',
  points_value  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '所需积分（积分口径，非金额）',
  stock         INT           NOT NULL DEFAULT 0 COMMENT '库存数量',
  status        VARCHAR(20)   NOT NULL DEFAULT '正常',
  remark        VARCHAR(255)  DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换物（积分口径，与金额无关）';

CREATE TABLE IF NOT EXISTS points_exchange_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(50)   DEFAULT NULL,
  student_id    BIGINT        NOT NULL,
  student_name  VARCHAR(50)   DEFAULT NULL,
  goods_id      BIGINT        NOT NULL,
  goods_name    VARCHAR(120)  DEFAULT NULL,
  quantity      INT           NOT NULL DEFAULT 1,
  points_cost   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '消耗积分（积分口径，非金额）',
  handler       VARCHAR(50)   DEFAULT NULL,
  remark        VARCHAR(255)  DEFAULT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_per_student (student_id),
  KEY idx_per_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';

-- ---------------------------------------------------------------
-- 成绩中心：考试（含随堂测评 / 考级 / 课堂表现）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grade_exam (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id    BIGINT        NOT NULL,
  name         VARCHAR(120)  NOT NULL COMMENT '考试名称',
  type         VARCHAR(20)   NOT NULL DEFAULT '周考' COMMENT '周考/月考/学校考试',
  course_id    BIGINT        DEFAULT NULL,
  course_name  VARCHAR(64)   DEFAULT NULL COMMENT '课程名称冗余，便于列表展示',
  exam_date    DATE          DEFAULT NULL,
  full_score   DECIMAL(6,2)  NOT NULL DEFAULT 100 COMMENT '满分',
  pass_score   DECIMAL(6,2)  DEFAULT NULL COMMENT '及格线（分），为空按满分 60%',
  scoring_mode VARCHAR(8)    NOT NULL DEFAULT '分数' COMMENT '分数/等级',
  paper_images TEXT           DEFAULT NULL COMMENT '试卷图片 JSON 数组',
  status       VARCHAR(16)   NOT NULL DEFAULT '草稿' COMMENT '草稿/已发布/已归档',
  creator      VARCHAR(50)   DEFAULT NULL,
  remark       VARCHAR(500)  DEFAULT NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ge_campus (campus_id),
  KEY idx_ge_date (exam_date),
  KEY idx_ge_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试成绩';

CREATE TABLE IF NOT EXISTS grade_exam_class (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  exam_id    BIGINT NOT NULL,
  class_id   BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gec (exam_id, class_id),
  KEY idx_gec_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试适用班级';

CREATE TABLE IF NOT EXISTS grade_part (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  exam_id    BIGINT       NOT NULL,
  name       VARCHAR(64)  NOT NULL COMMENT '分项名称',
  weight     DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '权重%',
  full_score DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '分项满分',
  sort       INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_gp_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试分项';

CREATE TABLE IF NOT EXISTS grade_score (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  exam_id       BIGINT       NOT NULL,
  class_id      BIGINT       NOT NULL,
  student_id    BIGINT       NOT NULL,
  enrollment_id BIGINT       DEFAULT NULL,
  total_score   DECIMAL(6,2) DEFAULT NULL COMMENT '总分（分项录入时自动汇总）',
  level         VARCHAR(16)  DEFAULT NULL COMMENT '等级成绩（等级制考试）',
  part_scores   JSON         DEFAULT NULL COMMENT '分项成绩 {"分项id":"得分"}',
  status        VARCHAR(16)  NOT NULL DEFAULT '未考' COMMENT '正常/缺考/免考/未考',
  rank_class    INT          DEFAULT NULL COMMENT '班级排名',
  rank_campus   INT          DEFAULT NULL COMMENT '校区排名',
  remark        VARCHAR(255) DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gs (exam_id, class_id, student_id),
  KEY idx_gs_student (student_id),
  KEY idx_gs_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试成绩明细';

CREATE TABLE IF NOT EXISTS grade_student_paper (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  exam_id    BIGINT       NOT NULL,
  student_id BIGINT       NOT NULL,
  url        VARCHAR(255) NOT NULL COMMENT '试卷图片地址',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gsp (exam_id, student_id),
  KEY idx_gsp_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员试卷图片（仅家长可查看自己孩子）';

CREATE TABLE IF NOT EXISTS grade_rule (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  name       VARCHAR(20)  NOT NULL COMMENT '等级名称，如 优秀/良好/合格',
  min_score  DECIMAL(6,2) NOT NULL DEFAULT 0,
  max_score  DECIMAL(6,2) NOT NULL DEFAULT 100,
  sort       INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩等级规则';

CREATE TABLE IF NOT EXISTS grade_part_template (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '模板名称',
  course_category VARCHAR(64) DEFAULT NULL COMMENT '适用课程类别，空表示通用',
  parts           JSON        DEFAULT NULL COMMENT '分项配置 [{"name":"听力","weight":30,"fullScore":30}]',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩分项模板';

-- ---------------------------------------------------------------
-- 家长端微信小程序
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_parent (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  openid      VARCHAR(64)  DEFAULT NULL COMMENT '微信 openid',
  unionid     VARCHAR(64)  DEFAULT NULL,
  nickname    VARCHAR(64)  DEFAULT NULL,
  avatar_url  VARCHAR(512) DEFAULT NULL,
  phone       VARCHAR(32)  DEFAULT NULL COMMENT '绑定用手机号',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_parent_openid (openid),
  KEY idx_wx_parent_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端小程序用户';

CREATE TABLE IF NOT EXISTS wx_parent_session (
  token       VARCHAR(64) NOT NULL,
  parent_id   BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at  DATETIME    NOT NULL,
  PRIMARY KEY (token),
  KEY idx_wx_session_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端登录会话';

CREATE TABLE IF NOT EXISTS wx_parent_student (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT      NOT NULL,
  student_id  BIGINT      NOT NULL,
  relation    VARCHAR(16) DEFAULT NULL COMMENT '父亲/母亲/其他',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_parent_student (parent_id, student_id),
  KEY idx_wx_ps_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长与学员绑定关系';

-- ---------------------------------------------------------------
-- 家长端订阅消息额度
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_subscribe_quota (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  parent_id    BIGINT       NOT NULL,
  template_id  VARCHAR(128) NOT NULL COMMENT '订阅消息模板ID',
  remain_count INT          NOT NULL DEFAULT 0 COMMENT '剩余可发送次数（用户每授权一次+1，发送成功-1）',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_subscribe_quota (parent_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端小程序订阅消息剩余次数';

-- ---------------------------------------------------------------
-- 家校服务：消息推送
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_message (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  title            VARCHAR(128)  NOT NULL,
  content          VARCHAR(1000) NOT NULL,
  msg_type         VARCHAR(16)   NOT NULL DEFAULT '系统通知' COMMENT '系统通知/休课通知',
  audience_type    VARCHAR(16)   NOT NULL DEFAULT '全员' COMMENT '全员/班级/学员',
  class_name_text  VARCHAR(512)  DEFAULT NULL COMMENT '推送班级名称快照',
  target_count     INT           NOT NULL DEFAULT 0 COMMENT '目标学员数',
  receiver_count   INT           NOT NULL DEFAULT 0 COMMENT '实际触达家长数（已绑定小程序）',
  sender           VARCHAR(64)   DEFAULT NULL COMMENT '发送人',
  sent_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wx_message_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-消息推送主表';

CREATE TABLE IF NOT EXISTS wx_message_receiver (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  message_id   BIGINT       NOT NULL,
  parent_id    BIGINT       NOT NULL,
  student_id   BIGINT       NOT NULL,
  student_name VARCHAR(64)  DEFAULT NULL COMMENT '学员姓名快照',
  read_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_at      DATETIME     DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_receiver (message_id, parent_id, student_id),
  KEY idx_wx_receiver_parent (parent_id, read_flag),
  KEY idx_wx_receiver_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-消息接收记录';

CREATE TABLE IF NOT EXISTS wx_message_target (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  message_id   BIGINT      NOT NULL,
  student_id   BIGINT      NOT NULL,
  student_name VARCHAR(64) DEFAULT NULL COMMENT '学员姓名快照',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_target (message_id, student_id),
  KEY idx_wx_target_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-消息目标学员';

-- ---------------------------------------------------------------
-- 家校服务：活动海报推送
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_activity_poster (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  title           VARCHAR(128) NOT NULL,
  description     VARCHAR(500) DEFAULT NULL COMMENT '推送说明，作为消息正文',
  audience_type   VARCHAR(32)  NOT NULL DEFAULT '学员' COMMENT '年级/班级/学员 组合摘要',
  grade_text      VARCHAR(255) DEFAULT NULL COMMENT '年级快照',
  class_name_text VARCHAR(512) DEFAULT NULL COMMENT '班级名称快照',
  target_count    INT          NOT NULL DEFAULT 0 COMMENT '目标学员数',
  receiver_count  INT          NOT NULL DEFAULT 0 COMMENT '触达家长数（发送时已绑定）',
  message_id      BIGINT       DEFAULT NULL COMMENT '关联 wx_message.id（消息通知与已读统计）',
  sender          VARCHAR(64)  DEFAULT NULL COMMENT '发送人',
  start_date      DATE         DEFAULT NULL COMMENT '为空长期有效',
  end_date        DATE         DEFAULT NULL,
  sent_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wx_poster_msg (message_id),
  KEY idx_wx_poster_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-活动海报推送';

CREATE TABLE IF NOT EXISTS wx_activity_poster_image (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  poster_id  BIGINT       NOT NULL,
  url        VARCHAR(512) NOT NULL COMMENT '/uploads/activity-poster/...',
  sort_no    INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wx_poster_image (poster_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-活动海报图片';

CREATE TABLE IF NOT EXISTS wx_activity_poster_target (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  poster_id    BIGINT      NOT NULL,
  student_id   BIGINT      NOT NULL,
  student_name VARCHAR(64) DEFAULT NULL COMMENT '学员姓名快照',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wx_poster_target (poster_id, student_id),
  KEY idx_wx_poster_target_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-活动海报目标学员';

-- ---------------------------------------------------------------
-- 家校服务：班级休课
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_suspend (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  schedule_id   BIGINT       NOT NULL COMMENT '排课课次ID',
  campus_id     BIGINT       DEFAULT NULL,
  class_id      BIGINT       NOT NULL,
  class_name    VARCHAR(128) DEFAULT NULL,
  course_name   VARCHAR(128) DEFAULT NULL,
  teacher_name  VARCHAR(64)  DEFAULT NULL,
  lesson_date   DATE         NOT NULL,
  start_time    TIME         DEFAULT NULL,
  end_time      TIME         DEFAULT NULL,
  reason        VARCHAR(255) NOT NULL COMMENT '休课原因',
  status        VARCHAR(16)  NOT NULL DEFAULT '已休课' COMMENT '已休课/已恢复',
  message_id    BIGINT       DEFAULT NULL COMMENT '关联的休课通知消息ID',
  notify_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已推送通知',
  operator_name VARCHAR(64)  DEFAULT NULL,
  suspended_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  restored_at   DATETIME     DEFAULT NULL,
  restored_by   VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_suspend_schedule (schedule_id),
  KEY idx_suspend_class (class_id, lesson_date),
  KEY idx_suspend_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-班级休课记录';

-- ---------------------------------------------------------------
-- 家校服务：班级作业
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_homework (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  campus_id    BIGINT        DEFAULT NULL,
  class_id     BIGINT        NOT NULL,
  class_name   VARCHAR(128)  DEFAULT NULL,
  teacher_id   BIGINT        DEFAULT NULL,
  teacher_name VARCHAR(64)   DEFAULT NULL,
  title        VARCHAR(128)  NOT NULL COMMENT '作业标题',
  content      VARCHAR(1000) NOT NULL COMMENT '作业内容',
  due_date     DATE          DEFAULT NULL COMMENT '截止日期',
  publish_date DATE          NOT NULL COMMENT '发布日期',
  publisher    VARCHAR(64)   DEFAULT NULL COMMENT '发布人',
  status       VARCHAR(16)   NOT NULL DEFAULT '已发布',
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_homework_class (class_id, publish_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家校服务-班级作业';


-- ---------------------------------------------------------------
-- 教师课时费：教师 + 年级档 课酬规则
-- 计费：上课人数 < threshold_students 时按 人数 * unit_price；
--       达到/超过 threshold_students 时按固定金额 fixed_amount
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_hour_rate (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  teacher_id         BIGINT       NOT NULL,
  teacher_name       VARCHAR(64)  DEFAULT NULL,
  grade_start        INT          NOT NULL DEFAULT 1 COMMENT '适用年级起始（1=一年级，9=九年级）',
  grade_end          INT          NOT NULL DEFAULT 9 COMMENT '适用年级结束（含）',
  unit_price         DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '人数单价（元/人）',
  threshold_students INT          NOT NULL DEFAULT 10 COMMENT '人数额度，达到后按固定金额',
  fixed_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '达到人数额度后的单节固定金额',
  remark             VARCHAR(255) DEFAULT NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师课时费-课酬规则';

-- ---------------------------------------------------------------
-- 教师课时费：单节课酬手工调整（如试听课 0 元）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teacher_hour_adjust (
  schedule_id  BIGINT        NOT NULL,
  teacher_id   BIGINT        DEFAULT NULL,
  amount       DECIMAL(10,2) DEFAULT NULL COMMENT '手工课酬；为空表示按规则计算',
  remark       VARCHAR(255)  DEFAULT NULL,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师课时费-单节调整';

-- ---------------------------------------------------------------
-- 安卓试用 App 安装包下载记录（每个账号每日限 5 次）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_download_log (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  staff_id      BIGINT      NOT NULL COMMENT '下载账号ID',
  staff_name    VARCHAR(64) DEFAULT NULL COMMENT '下载人姓名快照',
  ip            VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  download_date DATE        NOT NULL COMMENT '下载日期（按自然日统计）',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_app_download_staff_date (staff_id, download_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安卓试用App安装包下载记录';
