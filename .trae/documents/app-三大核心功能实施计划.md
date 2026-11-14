# App 新增三大核心功能实施计划

## Context

手机端（`erp/app`，Flutter 3.47.6 / Dart 3.13.5）目前只覆盖工作台、学员、班级、课表、订单、成绩、家校服务（消息/作业/休课）等查询与轻量写操作。助教与财务在机构现场仍必须回到 PC 端才能完成三类高频动作：

1. 学生现场缴费（课时费、教材费、资料费、充值、欠费补缴）
2. 助教随堂拍摄班级相册并上传
3. 教材入库/出库与库存查看

PC 端（`erp/web`）与后端（`erp/server`）的基础能力已经完备，本次目标是把这三条链路在 App 上做通，业务口径与 PC 端保持一致，数据仍全部写入本地库，不调用校宝接口。

**已确认的需求取舍（用户选择）**

- 缴费覆盖全部 5 类：课时费（报名/续费）、教材费、资料费、余额充值、欠费补缴
- 课时费表单做「完整版」：含赠送课时、优惠金额、价格模板档位、活动模板、有效期、备注
- 相册支持现场拍照 + 相册多选批量上传

## 一、可复用的现有接口（后端零新增，仅补权限映射）

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 缴费选项 | `GET /api/local-orders/options?studentId=` | 校区/课程/班级/支付方式/报读/活动模板/价格模板（含档位） |
| 课时费 | `POST /api/local-orders/enroll` | 报名、续费（同一课程存在生效报读即自动判定续费） |
| 充值 | `POST /api/local-orders/recharge` | 充值优先冲抵欠费，余额入账 |
| 欠费补缴 | `POST /api/local-orders/subsidy` | 按报读补费，金额上限为该报读 `arrears` |
| 资料费 | `GET /api/data-fees/options?studentId=`、`POST /api/data-fees/payments` | 模板价格 + 周期，自动写订单 |
| 教材费 | `GET /api/materials/options`、`GET /api/materials/stock`、`GET /api/materials/settings?size=500`、`POST /api/materials/purchases` | 售价/成本价取 settings，库存余量取 stock |
| 相册 | `GET /api/classes/{id}/photos`、`POST /api/class-album/{classId}/photos`（multipart，字段名 `file`） | 限 JPG/PNG/WebP/GIF、单张 ≤10MB |
| 出入库 | `GET /api/materials/stock`、`GET /api/materials/stock/changes`、`GET /api/materials/stock/changes/options`、`POST /api/materials/stock/changes` | 出库时 `quantity` 传正数，后端存负 |

`creator` / `handler` 一律不传，由后端 `OperatorResolver` 自动取当前登录账号（符合「经办人默认当前账号」的项目约定）。

**唯一建议的后端改动**：在 [ApiPermissionInterceptor.java](file:///d:/Code/Projects/Huancai/apps/erp/server/src/main/java/cn/huancai/erp/config/ApiPermissionInterceptor.java#L36-L65) 的 `RULES` 中补一条 `/api/data-fees` → `Set.of("/dataFeeTemplate", "/student", "/order")`。当前该前缀未登记，等于对所有已登录账号开放，补齐后与其它财务类接口口径一致。

## 二、网络层与依赖

**1. [pubspec.yaml](file:///d:/Code/Projects/Huancai/apps/erp/app/pubspec.yaml)**
新增 `image_picker: ^1.2.0`（拍照 + 相册多选）。

**2. [api.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/core/api.dart)**
现有 `_send` 只支持 JSON，需补充 multipart：

- 抽出统一的响应处理（401 触发 `onUnauthorized`、403 文案、`code != 0` 抛 `ApiException`、解包 `data`），供 JSON 与上传共用
- 新增 `Api.upload<T>(String path, {required File file, String field = 'file'})`：用 `http.MultipartRequest`，**不手动设置 Content-Type**（boundary 自动生成），只加 `Authorization`，超时放宽到 90 秒
- 上传前在页面层校验类型与大小，失败给出明确提示

**3. Android 配置**
`AndroidManifest.xml` 已含 `INTERNET` 与 `usesCleartextTraffic="true"`，无需改动。`image_picker` 走系统相机/相册 Intent，默认不声明 `CAMERA`、`READ_MEDIA_IMAGES`；若个别机型拍照无响应，再补 `CAMERA` 权限并做运行时申请（作为兜底，不在首版加入）。

## 三、财务缴费

**入口**
- [dashboard_page.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/pages/dashboard_page.dart#L87-L136) 新增 `QuickEntry('缴费')`，权限 `appState.can(['/order', '/student'])`
- [student_detail_page.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/pages/student_detail_page.dart#L100-L184) 头部卡片增加「缴费」胶囊按钮，携带当前 `studentId` 直达页面

**页面结构**（建议拆分，避免单文件过大）

```
lib/pages/payment_page.dart          学员选择 + 五个缴费类型 PillTabs + 提交后刷新
lib/pages/payment/enroll_form.dart   课时费（完整版）
lib/pages/payment/material_form.dart 教材费
lib/pages/payment/data_fee_form.dart 资料费
lib/pages/payment/recharge_form.dart 余额充值
lib/pages/payment/arrears_form.dart  欠费补缴
```

**课时费表单（完整版）**
- 课程下拉（限定 `courseIds` 含该课程的价格模板）；选课程后自动选中课程默认模板 + 第 1 档，带出课时与单价
- 价格模板下拉 + 档位下拉，切换后重算单价、课时、优惠
- 可编辑：报读课时、单价、赠送课时、优惠金额、有效期、备注
- 活动模板下拉（默认「不使用优惠」），按 PC 端口径计算：
  - `折扣`：`优惠 = 课程金额 × (1 - 折扣值/10)`
  - `立减`：`优惠 = min(立减额, 课程金额)`
  - 其余类型按「送课时」写入 `giftLessons`；「报课优惠」取满足课时门槛的最高档
- 应收 = 课时 × 单价 − 优惠；实收手填；差额由后端记欠费
- 提交字段：`studentId/campusId/courseId/classId/lessons/giftLessons/unitPrice/discount/shouldPay/received/expireDate/payMethod/remark/priceTemplateId/localActivityTemplateId`

**教材费表单**
- 教材下拉取 `/materials/options`，售价/成本价按名称从 `/materials/settings?size=500` 建映射自动带出
- 选教材后展示所选仓库的可用库存（`/materials/stock` 的 `warehouse/bayuquan/xinmin`）
- 出库仓库、数量、单价（可改）、账户余额抵扣（≤ 教材金额、≤ 学员可用余额）、备注
- 提交 `POST /api/materials/purchases`，后端扣库存 + 写购买记录 + 生成订单

**资料费表单**
- `/data-fees/options?studentId=` 取启用模板与可缴报读（含 `lastPaidAt`）
- 选报读 + 模板（自动带出价格与周期），支付方式、备注
- 提交 `POST /api/data-fees/payments`

**充值表单**：金额、支付方式、备注 → `/local-orders/recharge`

**欠费补缴表单**
- 报读下拉来自 `options.enrollments`，展示 `courseName / remainLessons / arrears`
- 金额默认填 `arrears`、上限 `arrears`；支付方式、备注 → `/local-orders/subsidy`

**通用**：金额统一保留 2 位小数；`_submitting` 期间禁用按钮防重复提交；成功后 `showToast` 展示订单号并 `pop(true)` 触发学员详情刷新。

## 四、班级相册（家校服务）

在 [home_service_page.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/pages/home_service_page.dart#L20-L215) 的 `_tabs` 中新增第 4 个页签「班级相册」，权限 `appState.can(['/classAlbum'])`：

- 顶部班级选择（复用现有 `_SingleClassPicker` 与 `/schedules/options` 的班级列表）
- 网格展示 `/api/classes/{classId}/photos`，点击查看大图（复用班级详情页的 `InteractiveViewer` 弹窗写法）
- 右上角「拍照/上传」：底部弹层选择「拍照」或「从相册选择（多选）」
  - `image_picker`：`ImageSource.camera` 单张；`pickMultiImage` 多选
  - 选图后 `maxWidth: 1600`、`imageQuality: 80` 压缩，再校验 ≤10MB
  - 逐张 `Api.upload('/class-album/$classId/photos')`，显示「第 n/总数」进度，失败项单独提示
- [class_detail_page.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/pages/class_detail_page.dart#L308-L360) 相册页签保持只读（符合「班级页面仅查看、编辑归家校服务」约定），仅把空态文案改为指向家校服务。

## 五、教材出入库

新增 `lib/pages/stock_page.dart`，入口为工作台 `QuickEntry('教材库存')`，权限 `appState.can(['/storage'])`。

- 顶部两个页签：**库存** / **出入库记录**
- 库存：`/materials/stock?keyword=&page=1&size=50`，每行显示 总库 / 鲅鱼圈 / 新民 / 合计，支持关键词搜索与低库存筛选
- 出入库记录：`/materials/stock/changes?page=1&size=50&bizType=`，展示仓库、教材、类型、数量（正负色）、单价、日期、经办人、备注
- 「新增出入库」底部按钮 → 弹出表单：
  - 仓库（`/materials/stock/changes/options` 的 `rooms`）
  - 教材（`/materials/options` 的 `materials`，只列启用项）
  - 类型（入库/出库）、数量（正数）、单价（入库默认成本价、出库默认售价，可改）、日期、备注
  - 提交 `POST /api/materials/stock/changes`，成功后同时刷新库存与记录

## 六、模型与复用

- 新增轻量模型（放入 [models.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/core/models.dart)）：`MaterialStockRow`、`StockChangeRow`、`PriceTemplateOption`、`EnrollOption`、`DataFeeTemplateOption`
- 统一复用 [widgets.dart](file:///d:/Code/Projects/Huancai/apps/erp/app/lib/ui/widgets.dart) 的 `SurfaceCard / SectionTitle / InfoRow / MetricTile / PillTabs / EmptyState / ErrorView / LoadingView / showToast / confirmDialog`，不引入新的 UI 库
- 表单内下拉统一使用「点击唤起底部选择器 + 搜索框」的现有写法（参考 `_SingleClassPicker`），避免 `DropdownButton` 在长列表下的可用性问题

## 七、验证方案

1. `flutter analyze` 无 error/warning
2. `flutter build apk --release` 构建成功，产物复制到 `C:\Users\chese\Downloads\幻彩学堂-release.apk`
3. 后端可写接口用只读方式预检：登录拿 token 后校验 `local-orders/options`、`data-fees/options`、`materials/options`、`materials/stock`、`materials/settings`、`class-album` 必填参数缺失时的报错分支
4. 真机逐项走通（用户执行，App 内验证）：
   - 新学员报名（含价格档位/活动模板/赠送课时）→ 学员详情课时与欠费变化、订单列表出现本地订单
   - 教材费购买（含余额抵扣）→ 库存减少、购买记录、订单
   - 资料费缴纳 → 订单 + 该课程 `lastPaidAt` 更新
   - 充值 / 欠费补缴 → 余额、欠费、报读 `arrears` 变化
   - 拍照与相册多选上传 → 家校服务与班级详情页相册均能看到新图
   - 入库、出库各一笔 → 库存数量与记录同步变化
5. 测试产生的业务数据由用户确认后清理，不在生产学员上做验证
