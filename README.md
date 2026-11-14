# 幻彩教育管理系统（校宝复刻版）

对校宝 [pro.schoolpal.cn](https://pro.schoolpal.cn) 的本地复刻：页面 1:1、数据来自校宝真实同步，不做假数据。
当前为**只读镜像**：所有写操作（新建 / 编辑 / 删除 / 批量上下架 / 确认领用等）都会提示「需在校宝端操作」。

## 目录结构

```
Huancai/
├─ apps/                        应用程序
│  ├─ erp/                      业务系统（前后端分离，互不依赖）
│  │  ├─ server/                后端：Spring Boot 3.3.4 + MyBatis-Plus，端口 8080
│  │  │  ├─ pom.xml
│  │  │  └─ src/main/
│  │  │     ├─ java/cn/huancai/erp/
│  │  │     │  ├─ common/       统一返回体 ApiResult<T>{code,message,data}
│  │  │     │  ├─ config/       跨域、MyBatis-Plus 分页等配置
│  │  │     │  └─ module/       按业务域分包，一个模块 = controller + service + mapper + vo
│  │  │     │     ├─ student/   学员
│  │  │     │     ├─ classgroup/班级 + 一对一
│  │  │     │     ├─ schedule/  排课
│  │  │     │     ├─ course/    课程
│  │  │     │     ├─ teaching/  记上课 / 点评
│  │  │     │     ├─ order/     订单
│  │  │     │     ├─ finance/   财务
│  │  │     │     ├─ material/  教材 / 杂费 / 库存 / 购买记录
│  │  │     │     ├─ staff/     员工 / 登录
│  │  │     │     ├─ comment/   课堂点评
│  │  │     │     ├─ attendance/考勤
│  │  │     │     ├─ consult/   咨询本
│  │  │     │     ├─ report/    报表
│  │  │     │     ├─ wx/        公众号
│  │  │     │     ├─ campus/    校区
│  │  │     │     └─ system/    系统
│  │  │     └─ resources/
│  │  │        ├─ application.yml  ★ 数据库连接配置
│  │  │        ├─ schema.sql       ★ 全量建表 DDL
│  │  │        └─ mapper/**/*.xml  MyBatis XML
│  │  ├─ web/                   前端：Vue 3 + TypeScript + Vite + ant-design-vue，端口 5173
│  │  │  ├─ vite.config.ts      /api 代理到 127.0.0.1:8080
│  │  │  └─ src/
│  │  │     ├─ api/client.ts    全部后端接口封装（唯一出口）
│  │  │     ├─ config/menu.ts   菜单与功能状态（ok / upgrade / locked / external）
│  │  │     ├─ router/index.ts  hash 路由
│  │  │     ├─ layout/          主框架
│  │  │     └─ views/           各业务页面，一页一文件
│  │  ├─ app/                   员工端：Flutter
│  │  └─ ref/                   App 界面参考截图
│  └─ ledger/                   旧记账小工具（Flask，独立运行）
├─ docs/
│  ├─ reference/                校宝业务功能说明（md / html）
│  ├─ specs/                    需求与实施方案（pricing-refactor）
│  └─ prototypes/升班原型/      升班功能 HTML 原型
├─ scripts/
│  └─ import/                   校宝数据导入脚本（读取 data/schoolpal）
├─ data/
│  └─ schoolpal/                校宝抓取原始数据（JSON / XLS，含敏感信息，不提交）
├─ storage/
│  └─ uploads/                  运行期上传文件（班级相册、试卷图片）
├─ tools/                       本地运行环境（不提交）
│  ├─ mysql-8.0.28-winx64/      MySQL 程序
│  ├─ mysql-data/               ★ MySQL 数据目录（库文件在这里）
│  ├─ apache-maven-3.9.16/      Maven
│  └─ m2repo/                   Maven 本地仓库
├─ backups/                     数据库备份（不提交）
├─ screenshots/                 调试截图（不提交）
├─ tmp/                         临时脚本与产物（不提交）
├─ .trae/                       Trae IDE 配置
├─ .gitignore
└─ README.md
```

前后端只通过 HTTP 通信：前端所有请求走 `apps/erp/web/src/api/client.ts`，统一以 `/api` 前缀经 Vite 代理转发到 8080，后端不渲染页面、前端不直连数据库。

## 数据库位置

| 项目 | 值 |
| --- | --- |
| 地址 | `127.0.0.1:3307` |
| 库名 | `huancai_erp` |
| 账号 / 密码 | `hc` / `hc123456` |
| **物理数据目录** | `D:\Code\Projects\Huancai\tools\mysql-data` |
| 库文件目录 | `D:\Code\Projects\Huancai\tools\mysql-data\huancai_erp` |
| 错误日志 | `D:\Code\Projects\Huancai\tools\mysql-data\err.log` |
| 建表语句 | `apps/erp/server/src/main/resources/schema.sql` |
| 连接配置 | `apps/erp/server/src/main/resources/application.yml` |
| 上传文件 | `storage/uploads`（后端 `app.upload-dir`） |

启动 MySQL（需在新开的终端里保持运行，关闭即停库）：

```powershell
$base = "D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64"
$data = "D:\Code\Projects\Huancai\tools\mysql-data"
& "$base\bin\mysqld.exe" "--basedir=$base" "--datadir=$data" "--port=3307" "--console"
```

命令行连库：

```powershell
& "D:\Code\Projects\Huancai\tools\mysql-8.0.28-winx64\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u hc -phc123456 huancai_erp
```

## 启动顺序

1. **MySQL**：上面的 mysqld 命令（3307）
2. **后端**（必须在项目根目录执行，上传目录 `./storage/uploads` 相对根目录）：

```powershell
& "D:\Code\Projects\Huancai\tools\apache-maven-3.9.16\bin\mvn.cmd" "-Dmaven.repo.local=D:\Code\Projects\Huancai\tools\m2repo" -f apps\erp\server\pom.xml clean package "-DskipTests"
java -jar apps\erp\server\target\erp-server-0.0.1-SNAPSHOT.jar
```

3. **前端**：

```powershell
cd apps\erp\web
npm install
npm run dev
```

4. 浏览器打开 http://localhost:5173 ，用员工账号登录。

> PowerShell 里 `-D` 开头的 Maven 参数必须加引号，否则会被解析成命令参数。

## 页面范围

**基础功能（持续完善）**：工作台、学员、班级、一对一、排课、约课、记上课、课程、订单、财务、员工、教材（购买记录 / 库存 / 教材设置 / 杂费设置）、公众号、报表中心、校宝家（课堂点评 / 作业 / 成长记录）。

**营销类（判定为无用，后期删除，暂不投入开发）**：招生宝、招生表单、微校、抖音开店、超级裂变、转介绍、美团/抖音/小红书直连、入学测评、智能外呼、AI 听记、营销中心、学员风采、积分商城、增值服务（收银宝 / 安心宝 / 校宝商学）。

**无数据页面（已在页内标注原因）**：

- 赛事考级：校宝接口返回空
- 作业：校宝账号缺少作业查看权限（200033 / 200035）
- 约课：校宝端 0 个班 / 0 个日程 / 0 条预约记录

## 数据来源

所有真实数据由 `data/schoolpal/` 下的抓取 JSON 与 `scripts/import/` 下的导入脚本一次性导入 `huancai_erp` 库。目前没有定时增量同步，校宝登录态过期后数据会停留在最后一次快照。
