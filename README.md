# 幻彩教育管理系统

面向教育培训机构的一体化管理平台，覆盖招生、办理、教务、成绩、家校、内部管理六大板块，包含管理后台（Web）、员工移动端（Flutter App）与家长端（微信小程序）三端。

系统采用「历史快照导入 + 新增业务本地闭环」的双轨模式：

- **历史数据**：由 `scripts/import/` 下的脚本从离线抓取快照一次性导入，仅做只读展示；
- **新增业务**：报名、续费、补费、充值与退费、转课、教材、积分、成绩、家校服务等全部在本系统内闭环完成，只写本地数据库，不调用任何外部系统接口。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.4、MyBatis-Plus 3.5.7、MySQL 8.0，端口 8080 |
| 管理后台 | Vue 3.5、TypeScript 6、Vite 8、ant-design-vue 4、dayjs（中文语言包），端口 5173 |
| 员工 App | Flutter 3.47 / Dart 3.13，Android release APK |
| 家长端 | 微信小程序（原生），目录 `apps/weixin/幻彩家长端` |
| 数据库 | MySQL 8.0.28（随项目内置在 `tools/`，数据目录 `tools/mysql-data`） |

前后端只通过 HTTP 通信：前端所有请求统一走 `apps/erp/web/src/api/client.ts`，以 `/api` 前缀经 Vite 代理转发到 8080；后端不渲染页面，前端不直连数据库。

## 目录结构

```
Huancai/
├─ apps/                         应用程序
│  ├─ erp/                       业务系统（前后端分离）
│  │  ├─ server/                 后端：Spring Boot + MyBatis-Plus
│  │  │  ├─ pom.xml
│  │  │  └─ src/main/
│  │  │     ├─ java/cn/huancai/erp/
│  │  │     │  ├─ common/        统一返回体 ApiResult<T>{code,message,data}、订单号生成器
│  │  │     │  ├─ config/        跨域、分页、接口权限拦截器
│  │  │     │  └─ module/        按业务域分包，一个模块 = controller + service + mapper + vo
│  │  │     │     ├─ student/    学员
│  │  │     │     ├─ classgroup/ 班级 + 一对一
│  │  │     │     ├─ schedule/   排课
│  │  │     │     ├─ course/     课程模板
│  │  │     │     ├─ teaching/   记上课 / 点评
│  │  │     │     ├─ order/      订单（历史快照 + 本地闭环办理）
│  │  │     │     ├─ finance/    财务
│  │  │     │     ├─ material/   教材 / 资料费 / 库存 / 购买记录
│  │  │     │     ├─ staff/      员工 / 登录 / 权限模板
│  │  │     │     ├─ grade/      成绩中心
│  │  │     │     ├─ homeservice/家校服务（消息、海报、相册、休课）
│  │  │     │     ├─ wxapp/      家长端小程序接口
│  │  │     │     ├─ points/     积分兑换
│  │  │     │     ├─ report/     报表
│  │  │     │     └─ system/     工作台等系统接口
│  │  │     └─ resources/
│  │  │        ├─ application.yml  ★ 数据库与上传目录配置
│  │  │        ├─ schema.sql       ★ 全量建表 DDL
│  │  │        └─ mapper/**/*.xml  MyBatis XML
│  │  ├─ web/                    管理后台：Vue 3 + TypeScript + Vite
│  │  │  ├─ vite.config.ts       /api 代理到 127.0.0.1:8080
│  │  │  └─ src/
│  │  │     ├─ api/client.ts     全部后端接口封装（唯一出口）
│  │  │     ├─ config/menu.ts    菜单与功能状态（ok / locked / external）
│  │  │     ├─ router/index.ts   hash 路由
│  │  │     ├─ layout/           主框架
│  │  │     └─ views/            各业务页面，一页一文件
│  │  ├─ app/                    员工端：Flutter
│  │  └─ tools/                  桌面端配套工具（随仓库提交）
│  │     └─ receipt-printer/     58mm 小票打印小助手（C# 源码 + exe）
│  └─ weixin/                    家长端：微信小程序（原生）
│     └─ 幻彩家长端/             小程序根（app.js / app.json / pages）
├─ apps/ledger/                  旧课时记账工具（Python 标准库单文件，独立运行）
├─ docs/
│  ├─ reference/                 业务功能说明与迁移分析（本地参考资料）
│  └─ prototypes/                升班功能 HTML 原型
├─ scripts/import/               历史数据导入脚本（读取 data/ 下的快照）
├─ data/                         历史抓取原始数据（JSON / XLS，含敏感信息，不提交）
├─ storage/uploads/              运行期上传文件（班级相册、活动海报、试卷图片）
├─ tools/                        本地运行环境（不提交）
│  ├─ mysql-8.0.28-winx64/       MySQL 程序
│  ├─ mysql-data/                ★ MySQL 数据目录（库文件在这里）
│  ├─ apache-maven-3.9.16/       Maven
│  └─ m2repo/                    Maven 本地仓库
├─ backups/                      数据库备份（不提交）
├─ screenshots/                  调试截图（不提交）
├─ tmp/                          临时脚本与产物（不提交）
├─ .trae/                        Trae IDE 配置
├─ .gitignore
└─ README.md
```

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

3. **管理后台**：

```powershell
cd apps\erp\web
npm install
npm run dev
```

4. 浏览器打开 http://localhost:5173 ，用员工账号登录。

5. **员工 App**（可选）：

```powershell
cd apps\erp\app
flutter pub get
flutter build apk --release
```

6. **家长端小程序**（可选）：用微信开发者工具打开 `apps/weixin/幻彩家长端`，配置 `config.js` 的 `baseUrl` 指向后端地址。

> PowerShell 里 `-D` 开头的 Maven 参数必须加引号，否则会被解析成命令参数。
>
> 后端依赖 MySQL 先启动，否则首次启动会直接退出；修改后端代码后必须重新打包并重启进程，新增接口才会生效。

## 服务器部署

线上一套部署（Linux）由「MySQL + 后端 jar + nginx 静态前端 + 下载目录」四部分组成，换服务器时照下面顺序走一遍即可。

### 线上目录与端口

| 项 | 路径 / 值 |
| --- | --- |
| 后端 jar | `/opt/huancai/server/erp-server.jar` |
| 后端日志 | `/opt/huancai/server/app.log` |
| 后端工作目录 | `/opt/huancai`（上传目录按相对路径解析，不能用别的工作目录启动） |
| 前端静态文件 | `/opt/huancai/web`（nginx 站点根目录） |
| 网页下载目录 | `/opt/huancai/web/downloads`（打印小助手安装包） |
| 上传文件目录 | `/opt/huancai/storage/uploads` |
| 安卓安装包 | `/opt/huancai/storage/app/huancai-app.apk` |
| 后端端口 | 8080（仅监听本机，由 nginx 反代） |
| 前端端口 | 80（nginx 托管静态文件并把 `/api` 转发到 8080） |
| 数据库 | MySQL 8，库 `huancai_erp`，账号 `hc`，端口 3306 |

### 安装包与产物位置

| 产物 | 仓库内位置 | 本地构建产物 | 服务器位置 / 访问地址 |
| --- | --- | --- | --- |
| 打印小助手（Windows） | 源码 `apps/erp/tools/receipt-printer/HcReceiptPrinter.cs`，可执行文件 `apps/erp/tools/receipt-printer/build/HcReceiptPrinter.exe`（约 21KB，随仓库提交） | 同左 | `/opt/huancai/web/downloads/HcReceiptPrinter.exe`，下载地址 `http://<服务器>/downloads/HcReceiptPrinter.exe` |
| 员工 App 安卓包 | 不入库，源码在 `apps/erp/app` | `apps/erp/app/build/app/outputs/flutter-apk/app-release.apk`（约 56MB） | `/opt/huancai/storage/app/huancai-app.apk`，网页「试用手机 App」下载 |
| 后端 jar | 源码 `apps/erp/server` | `apps/erp/server/target/erp-server-0.0.1-SNAPSHOT.jar`（约 31MB） | `/opt/huancai/server/erp-server.jar` |
| 前端静态包 | 源码 `apps/erp/web` | `apps/erp/web/dist` | `/opt/huancai/web` |
| 本地工具链 | 不入库 | `tools/`（JDK 17、Maven 3.9.16、MySQL 8.0.28、Node、Flutter、m2repo） | 换机时按下方「依赖版本」重新安装 |

依赖版本：JDK 17、Maven 3.9.x、MySQL 8.0、Node 20+、Flutter 3.x（仅打 App 用）、nginx。

### 首次部署步骤

1. **装依赖**：JDK 17、MySQL 8、nginx、Node 20+。
2. **建库导表**：

```bash
mysql -uroot -p -e "CREATE DATABASE huancai_erp DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p huancai_erp < apps/erp/server/src/main/resources/schema.sql
mysql -uroot -p -e "CREATE USER 'hc'@'%' IDENTIFIED BY '<密码>'; GRANT ALL ON huancai_erp.* TO 'hc'@'%';"
```

3. **打包后端**（仓库内不带 `tools/`，用本机 Maven 即可）：

```bash
mvn -f apps/erp/server/pom.xml clean package -DskipTests
```

4. **上传并启动后端**：

```bash
mkdir -p /opt/huancai/server /opt/huancai/web/downloads /opt/huancai/storage/uploads /opt/huancai/storage/app
scp erp-server-0.0.1-SNAPSHOT.jar <用户>@<服务器>:/opt/huancai/server/erp-server.jar
ssh <用户>@<服务器> "cd /opt/huancai && setsid nohup java -Xms512m -Xmx2048m -jar /opt/huancai/server/erp-server.jar >> /opt/huancai/server/app.log 2>&1 < /dev/null &"
```

5. **打包并上传前端**：

```bash
cd apps/erp/web && npm ci && npm run build
tar -czf dist.tar.gz -C dist .
scp dist.tar.gz <用户>@<服务器>:/tmp/
ssh <用户>@<服务器> "tar -xzf /tmp/dist.tar.gz -C /opt/huancai/web"
```

6. **nginx 站点**（关键片段，其余保持默认）：

```nginx
server {
    listen 80;
    server_name _;
    root /opt/huancai/web;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

7. **放安装包**：把 `HcReceiptPrinter.exe` 放到 `/opt/huancai/web/downloads/`，把 App 的 `app-release.apk` 放到 `/opt/huancai/storage/app/huancai-app.apk`。
8. **验证**：浏览器打开服务器地址登录，左下角能看到「打印小助手下载」即可。

### 日常升级

```bash
# 后端：替换 jar 后重启（当前进程直接 java -jar 启动，无 systemd 托管）
pkill -f erp-server.jar
cd /opt/huancai && setsid nohup java -Xms512m -Xmx2048m -jar /opt/huancai/server/erp-server.jar >> /opt/huancai/server/app.log 2>&1 < /dev/null &

# 前端：重新 build 后覆盖 /opt/huancai/web，index.html 的 hash 变化即生效
```

增量表结构变更必须手工执行，`schema.sql` 只在首次建库时使用。已执行过的变更：

```sql
-- 2026-11 订单详情新增「最后修改时间」
ALTER TABLE sp_order ADD COLUMN updated_time DATETIME NULL COMMENT '最后修改时间' AFTER created_time;
```

### 打印小助手（Windows 收银电脑）

1. 收银电脑登录系统，点左下角「打印小助手下载」；
2. 双击 `HcReceiptPrinter.exe`，弹窗提示已启动即完成安装（自动复制到 `%LOCALAPPDATA%\HcReceiptPrinter\` 并写入开机自启）；
3. 之后网页点「打印」，订单小票 / 听课证直接从 58mm 小票机出纸，不再弹浏览器打印框。

排障：`http://127.0.0.1:18666/health` 能打开说明小助手在运行；日志 `%LOCALAPPDATA%\HcReceiptPrinter\printer.log`；卸载执行 `HcReceiptPrinter.exe --uninstall`。换 USB 口或换打印机型号时，如自动识别失败，可在 `%LOCALAPPDATA%\HcReceiptPrinter\device.txt` 里写设备路径覆盖。

重新编译（Windows 自带 .NET Framework，无需装 SDK；源码含中文，必须加 `/codepage:65001` 否则中文会乱码）：

```powershell
& "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /codepage:65001 /target:winexe /optimize+ `
  /r:System.Web.Extensions.dll /out:build\HcReceiptPrinter.exe HcReceiptPrinter.cs
```

接口：`GET /health` 健康检查；`POST /print/receipt` 打订单小票；`POST /print/ticket` 打听课证；`POST /print/test` 打自检票；`POST /print/preview` 只渲染不打印（返回 Base64 字节，用于核对中文编码）。

## 功能模块

| 分组 | 模块 | 说明 |
| --- | --- | --- |
| 工作台 | 工作台 | 在读学员、在读报读、剩余课时、本月实收、应收未收、到期预警、快捷入口 |
| 招生中心 | 活动效果 | 活动参与学员、新老生占比、订单数、报读课时、应收 / 实收 / 优惠金额统计与明细 |
| 办理中心 | 订单列表 | 历史订单快照 + 本地闭环订单，支持筛选、汇总、作废、导出当日订单、小票打印 |
| 办理中心 | 缴费模板 | 基础价格模板（单节课单价）与活动模板（节日活动 / 报课优惠，支持分类层级） |
| 办理中心 | 教师课时 | 按教师统计上课课时与工作量 |
| 教务中心 | 学员 | 学员档案、报读、课时、账户余额、积分、订单、成绩、相册 |
| 教务中心 | 班级 | 班级列表 / 详情、分班、结班、升班、上课名单打印、相册查看 |
| 教务中心 | 排课 | 周课表、单双周、冲突检测、调课 |
| 教务中心 | 记上课 | 点名、扣课时、超出课时按原价计费、补记上课 |
| 教务中心 | 课程模板 | 课程主数据与默认价格模板，仅保留按课时收费 |
| 教务中心 | 磁卡考勤 | 依赖外部硬件，暂未开放 |
| 成绩中心 | 考试管理 / 成绩录入 / 成绩查询 / 成绩分析 | 本地闭环，自绘 SVG 图表，单场对比、班级趋势、学员波动 |
| 家校服务 | 消息推送 | 按年级 / 班级 / 学员定向推送文本通知，统计触达与已读 |
| 家校服务 | 活动海报推送 | 1~9 张海报定向推送，家长端竖滑浏览、横滑切换、点击放大 |
| 家校服务 | 班级休课 | 按班级批量休课并通知家长 |
| 家校服务 | 课堂点评 / 作业 / 班级相册 | 课堂点评、作业发布、班级相册上传与家长查看 |
| 内部管理 | 组织架构 | 校区 → 角色组织树、员工卡片、菜单权限分配 |
| 内部管理 | 员工 | 员工档案、主角色 + 兼任角色、离职 / 复职 |
| 内部管理 | 财务 | 收款流水、账户余额、退费记录 |
| 内部管理 | 教材 | 教材设置、库存、购买记录、出入库记录 |
| 内部管理 | 积分兑换 | 兑换物维护、积分兑换记录 |
| 内部管理 | 资料费模板 | 按课程配置资料费价格与缴费周期 |

## 核心业务规则

### 价格与优惠

- 课程价格模板只有**单一课时单价**，不再支持多档位；所有优惠统一由活动模板承担。
- 活动模板分「节日活动」与「报课优惠」两类，支持无限级分类；被引用过的模板只能停用、不能删除。
- 报名页活动模板支持多选，优惠叠加：折扣按课程本金计算、立减直接相加、送课时相加，总优惠封顶课程本金。
- 报名页单价自动带出（课程默认模板 → 该课程第一个模板 → 课程原价），有模板时单价只读；价格模板的教材费计入应收并随单保存。
- 订单明细冗余保存价格模板、活动模板的 ID 与名称快照，模板后续变更不影响历史订单。
- 优惠单价只对报读课时范围内有效，超出剩余课时的欠费部分按课程原价计费，并单独生成「超课时扣减」课时流水。

### 课时与账户

- 课时账户按「学员 × 课程」建立，同一学员可同时存在多门课程的报读，课时数据相互独立。
- 允许超出剩余课时扣减，账户余额、剩余课时可为负数，欠费按超出部分累加。
- 报名时选择班级会自动分班并同步课时；转课、退费、结课会自动清退对应班级成员并更新人数统计。
- 记上课弹窗自动拆分「剩余课时内」与「超出部分」的单价，超出时明确提示按原价计费。
- 余额充值默认优先冲抵欠费，剩余金额计入账户余额；冲抵金额同步回写对应报读的实收记录。
- 学员账户的剩余未用学费与原有账户余额合并为统一字段，通过独立的退费按钮操作退款。
- 结课时剩余课时清零，剩余学费转入账户剩余未用学费，课时流水记为「结课转余额」；结课为终态，不可恢复上课。

### 转课

- 转课订单的实收金额只统计本次实际新收取的现金，无新增现金时为 0，不得把转出课程剩余价值或差额冲抵记为收款。
- 应退差额未实际退现的部分统一计入账户剩余未用学费，不自动生成退款记录；转课订单不标记为退单类型。

### 订单与收款

- 本地订单号接续旧系统的最新订单号递增（纯数字，如 33753、33754），历史导入订单保持原有日期型长号不变；教材订单与办理中心共用同一号段。
- 订单列表「经办日期」列直接展示订单的经办日期（精确到天），支持排序。
- 订单详情展示「创建时间」与「最后修改时间」；订单被编辑过才会写入最后修改时间，未编辑过显示「未修改」。
- 历史快照订单与本地订单通过 `sp_id` 是否为空区分，本地订单在列表中显示「本地」标签。
- 订单列表默认查询最近 30 天经办日期，提供今天 / 近 7 天 / 近 30 天 / 本月 / 全部快捷筛选。
- 「导出当日订单」固定导出当天数据，严格套用既定 XLSX 模板的字体、版式、合并单元格与 A4 打印参数，全程 UTF-8 防姓名乱码。
- 缴费成功后使用不可外部关闭的强提示弹窗展示订单号与实收金额，同时清空表单并防连点，避免重复下单。
- 经办人默认取当前登录账号，仅手动切换时使用指定值。

### 教材与积分

- 购买教材不会自动抵扣账户余额，需在弹窗中手动选择是否抵扣，金额不超过教材金额与可用余额，订单分别记录抵扣金额与现金支付金额。
- 积分与现金、账户余额口径严格区分，学员详情页头部直接展示当前积分，兑换入口统一在积分管理模块与订单页。

### 权限

- 组织架构权限做三层拦截：前端菜单隐藏、路由守卫拦截、后端接口权限校验。
- 管理员账号固定拥有全部权限且不可修改，仅管理员可分配员工菜单权限。
- 员工支持「主角色 + 兼任角色」，兼任角色仅用于展示与统计，不参与权限判定。
- 新增接口需在 `ApiPermissionInterceptor` 的 `RULES` 中登记前缀映射，否则默认对所有已登录账号开放。

### 成绩中心

- 所有功能本地闭环，不调用外部接口；图表使用自绘 SVG 组件，不引入第三方图表库。
- 成绩发布后按班级、校区维度自动计算排名，同分并列；解锁操作自动追加操作人与时间到备注留痕。

### 家校服务与小程序

- 班级页面只负责查看，所有编辑操作统一在家校服务模块完成。
- 家长端接口做绑定校验，仅能看到自己已绑定学员所在班级的相册、海报与消息。
- 订阅消息采用一次性订阅模板，额度表记录授权次数与剩余可发条数，发送前先扣额度，额度为 0 时不再调用微信接口。
- 订阅授权仅在用户主动点击的回调中拉起；`access_token` 全局缓存并提前 5 分钟过期。
- 海报、相册图片禁止在 swiper 内使用 `lazy-load`，并对加载失败做本地下载重渲染的降级处理。

### 打印

- 58mm 小票（订单收据、听课证）优先由本机「打印小助手」通过 ESC/POS 指令直写 USB 打印设备，绕过系统打印队列与驱动；本机没装小助手时依次退回后端直发、浏览器小票预览。
- 打印小助手为单文件 `HcReceiptPrinter.exe`，常驻监听 `127.0.0.1:18666`，源码与可执行文件见 `apps/erp/tools/receipt-printer/`；网页左下角提供下载入口，双击即安装（自动写入开机自启），日志在 `%LOCALAPPDATA%\HcReceiptPrinter\printer.log`。
- 上课名单使用 A4 横向版式，固定预留 100 个学员签名位（5 列 × 20 行）与 10 个试听签名位。

## 数据导入

历史快照数据由 `scripts/import/` 下的脚本导入，全部脚本可幂等重跑，不同来源通过 `source` 字段（如「历史教材设置」「本地录入」）区分。

- 数据源目录：`data/`（抓取快照，含敏感信息，不提交）
- 幂等依据：`ext_id` 保存源系统主键，重复执行不会产生重复数据
- 导入顺序建议：学员花名册 → 报读 → 课程 / 班级 → 班级学员 → 排课 → 记上课 / 考勤 / 点评 → 订单 / 收款 → 教材 / 库存

历史数据为一次性离线快照，没有定时增量同步；快照之后的变化只体现在本系统的新增业务中。

## 子应用：旧课时记账工具

`apps/ledger` 是早期的私用课时记账工具，零第三方依赖（Python 标准库），数据存单个 SQLite 文件 `data.db`，双击 `启动.bat` 后访问 `http://127.0.0.1:8000`。功能仅覆盖「课时 + 订单 + 退费」三件事，退费口径为：

```
折后单价   = 实收金额 ÷ 总课时（赠送课时不计入分母）
已消耗金额 = 已消耗课时 × 折后单价
应退金额   = 实收金额 − 已消耗金额
```

该工具与主系统无数据交互，仅作历史留存。

## 变更记录

### 目录与结构

- 根目录按 `apps / docs / scripts / data / storage / tools` 职责整理；`erp/` 迁至 `apps/erp/`，`ledger/` 迁至 `apps/ledger/`。
- `.tools/`、`.data/`、`.tmp/`、`.screenshots/`、`backup/` 分别更名为 `tools/`、`data/`、`tmp/`、`screenshots/`、`backups/`。
- 数据导入脚本统一迁至 `scripts/import/`；后端上传目录调整为 `storage/uploads`。
- 新增 `.gitignore`，排除本地运行环境、构建产物、备份与抓取数据。

### 业务迭代

- 价格模板收敛为单一课时单价，优惠统一由活动模板承担，报名页活动支持多选叠加。
- 新增成绩中心（考试管理、成绩录入、成绩查询、成绩分析），本地闭环并支持排名。
- 新增家校服务：活动海报推送、班级相册、班级休课通知、课堂点评、作业。
- 家长端小程序新增活动海报、班级相册、订阅消息提醒。
- 员工 App 打通五类缴费（课时费、教材费、资料费、充值、欠费补缴）、班级相册拍摄上传、教材出入库。
- 组织架构支持员工主角色 + 兼任角色，新增菜单权限模板与管理员权限分配。
- 订单模块新增本地闭环办理（报名 / 续费、充值、补费、转课、退费）与统一订单号生成器。
- 全部日期 / 日历组件统一为中文展示（Web 端 dayjs 语言包、App 端 flutter_localizations）。

## 开发约定

- 所有页面必须使用真实后端接口数据，禁止硬编码模拟数据；数据为空时在页面内标注原因。
- 空状态统一使用「暂无 + 对象」句式：列表无结果用「当前筛选条件下暂无 XX」，通用场景用「暂无数据」。
- 新增业务模块需同时创建 Controller、Service、Mapper 与 Mapper.xml，遵循现有分包结构。
- 新增或调整后端接口需同步登记接口权限映射；共享接口加入 `SHARED_PREFIXES`。
- 管理端登录态使用 `staff_session` 表中的 token（7 天有效），前端权限存 `localStorage` 并在进入系统时通过 `/api/auth/me` 刷新。
- 前端所有页面需通过 `vue-tsc` 类型检查，浏览器控制台无红色报错；App 端需通过 `flutter analyze`。
- `a-select` 禁止使用 `option-filter-prop="children"`，统一使用扁平 `options` 并按 `label` 中文匹配；学员下拉采用服务端模糊搜索，禁止前端全量拉取。
- 全局 HTTP 错误提示优先读取后端返回的 `message` 字段。
- 课程删除存在硬性保护：存在关联班级或报读记录时禁止删除。
