# 任务清单：课程价格模板与活动优惠重构

- [x] T1 数据迁移：收敛 `price_template_price` 为单行
  - 文件/操作：本机 MySQL `huancai_erp`
  - 执行 spec 3.1.1 的 DELETE + UPDATE
  - 验收：每个 `template_id` 仅 1 行，`lessons=1`、`total_price=unit_price`；模板 1 单价 60

- [x] T2 `sp_order_item` 增加 `activity_template_ids`
  - 文件：`apps/erp/server/src/main/resources/schema.sql`、`apps/erp/server/src/main/java/cn/huancai/erp/module/order/entity/SpOrderItem.java`
  - 改动：CREATE TABLE 增加字段；实体增加 `String activityTemplateIds`
  - 数据库：执行 `ALTER TABLE sp_order_item ADD COLUMN activity_template_ids ...`
  - 验收：重启后插入订单不报 SQL 错误，字段可查询

- [x] T3 基础价格模板页改为单一课时单价
  - 文件：`apps/erp/web/src/views/PriceTemplates.vue`
  - 改动：按 spec 3.2 删除档位状态/函数，表单改单输入，列表列改「课时单价」
  - 验收：新建/编辑只有单价；列表显示 `￥60.00/课时`；payload 为单档位 `lessons=1`

- [x] T4 课程页内嵌价格模板弹窗改为单一课时单价
  - 文件：`apps/erp/web/src/views/Courses.vue`
  - 改动：按 spec 3.2 `templateForm`、校验、payload、弹窗模板
  - 验收：弹窗只有「课时单价」，保存成功且不丢失课程表单

- [x] T5 报名页价格自动带出 + 活动多选
  - 文件：`apps/erp/web/src/views/EnrollOrder.vue`
  - 改动：按 spec 3.3 删除模板/档位选择器、单价只读、活动 `number[]` 多选与叠加算价
  - 验收：选课程自动带价且单价只读；多活动立减相加、折扣按本金、赠课相加、封顶本金

- [x] T6 快捷报名弹窗活动多选
  - 文件：`apps/erp/web/src/components/LocalOrderModal.vue`
  - 改动：按 spec 3.4 字段改数组、汇总算价、多选控件、payload
  - 验收：多选活动后优惠汇总正确，提交字段为 `localActivityTemplateIds`

- [x] T7 client.ts 类型调整
  - 文件：`apps/erp/web/src/api/client.ts`
  - 改动：`LocalEnrollPayload` 改为 `localActivityTemplateIds?: number[]`
  - 验收：`npx vue-tsc -b` 无类型错误

- [x] T8 后端多活动校验与快照
  - 文件：`apps/erp/server/src/main/java/cn/huancai/erp/module/order/dto/LocalOrderRequests.java`、`.../service/LocalOrderService.java`
  - 改动：DTO 改 `List<Long>`；新增 `loadLocalActivityTemplates`；`enroll` 拼接 id/名称；`writeOrderItem`/`orderItems` 支持多 id
  - 验收：停用/过期活动提交报错；订单明细保存全部活动 id 与名称

- [x] T9 员工 App 接口适配
  - 文件：`apps/erp/app/lib/pages/payment/enroll_form.dart`
  - 改动：提交 `'localActivityTemplateIds': [_activityId]`
  - 验收：App 报名请求成功（人工/编译验证）

- [x] T10 编译与浏览器验证
  - 命令：`apps/erp/web` 执行 `npx vue-tsc -b`；`apps/erp/server` 执行 Maven 打包并重启 8080
  - 浏览器：报名页、快捷报名弹窗、价格模板页、课程页逐项验证 spec 第 4 节
