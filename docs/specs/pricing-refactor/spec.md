# 课程价格模板与活动优惠重构 Spec

## 1. 背景与目标

当前价格模板支持「1 节 60 元 / 10 节 40 元」多档位，报名页还需要同时选模板与档位，且优惠模板只能单选，导致：

- 课程模板本身承担了阶梯优惠，与活动优惠重复叠加、口径混乱；
- 报名页操作路径长（选课程 → 选价格模板 → 选档位 → 选活动）；
- 活动模板页维护的优惠无法组合使用。

本次重构目标（用户明确要求）：

1. **课程模板只保留一个基础价格**：一节课固定单价多少钱，不再有多档位。
2. **所有优惠统一走活动模板**：价格模板不再包含任何优惠逻辑。
3. **活动模板在报名页面支持多选**：多个活动按规则同时生效，优惠可叠加。

## 2. 方案总览

| 层 | 变更 |
| --- | --- |
| 数据 | `price_template_price` 每个模板收敛为单行（`lessons=1`，`total_price=unit_price`）；`sp_order_item` 新增 `activity_template_ids` |
| 基础价格模板页 | 档位编辑器改为「课时单价」单输入框；列表展示 `￥X/课时` |
| 课程页内嵌模板弹窗 | 同上 |
| 报名页 `EnrollOrder.vue` | 去掉价格模板/档位选择器，自动使用课程默认模板，单价只读；活动改多选并汇总 |
| 快捷报名 `LocalOrderModal.vue` | 活动改多选并汇总 |
| 后端 | 报名请求由 `localActivityTemplateId` 改为 `localActivityTemplateIds: List<Long>`，逐个校验启用/有效期，订单快照记录多活动 |
| 员工 App | 提交字段改为数组 |

**不做的事**：不新增活动规则类型；不改变活动规则 `lessons` 门槛语义；不改动价格模板与课程的绑定方式；不重构 `price_template_price` 表结构（继续复用单行）。

## 3. 详细设计

### 3.1 数据层

#### 3.1.1 存量价格模板收敛

对现有库执行一次迁移，每个 `template_id` 只保留 `lessons` 最小、`id` 最小的一行，并统一为「1 课时」：

```sql
DELETE p FROM price_template_price p
JOIN price_template_price q
  ON q.template_id = p.template_id
 AND (q.lessons < p.lessons OR (q.lessons = p.lessons AND q.id < p.id));

UPDATE price_template_price
SET lessons = 1, total_price = unit_price, sort_no = 0;
```

结果：模板 1（1 节 60 / 10 节 40）保留 60，变为 `1 课时 × ￥60`。

#### 3.1.2 订单明细多活动快照

`schema.sql` 的 `sp_order_item`（当前 706–727 行）在 `activity_template_name` 后新增：

```sql
activity_template_ids  VARCHAR(255)  DEFAULT NULL COMMENT '使用的活动模板ID（逗号分隔）',
```

存量库同步执行：

```sql
ALTER TABLE sp_order_item
  ADD COLUMN activity_template_ids VARCHAR(255) DEFAULT NULL
  COMMENT '使用的活动模板ID（逗号分隔）' AFTER activity_template_name;
```

`activity_template_id` / `activity_template_name` 继续保留：单选时写首个 id，名称始终写全部活动名（`、` 拼接），`activity_template_ids` 写全部 id（`,` 拼接）。

### 3.2 前端：基础价格模板维护

#### `apps/erp/web/src/views/PriceTemplates.vue`

- 状态：删除 `form.prices` 数组，新增 `form.unitPrice: number | null`（默认 0）。
- 删除 `addTier`、`removeTier`、`syncTotal`（164–177 行）。
- `openCreate`（128 行）/ `openEdit`（143 行）：改为初始化/读取 `row.prices[0]?.unitPrice`。
- `submit`（179 行）：校验 `unitPrice > 0`；payload 固定提交单档位：
  ```ts
  prices: [{ lessons: 1, unitPrice: Number(form.unitPrice), totalPrice: Number(form.unitPrice) }]
  ```
- 列表列（71 行）标题「课时档位」→「课时单价」；`tierText`（83 行）改为读取首个档位，输出 `￥60.00/课时`，无档位输出 `—`。
- 表单（937–971 行）整块替换为：
  ```
  课时单价 [ 数字输入 ] 元/课时
  所有优惠请在「活动模板」中配置，价格模板仅记录基础单价。
  ```

#### `apps/erp/web/src/views/Courses.vue`

- `templateForm`（约 566–580 行）：删除 `prices` 数组，改为 `unitPrice: number | null`。
- 删除 `addTemplateTier`、`removeTemplateTier`、`syncTemplateTotal`（609–621 行）。
- `tierText`（623 行）改为 `￥X/课时`。
- 校验（636–650 行）改为只校验 `unitPrice > 0`。
- payload（654 行）同样固定 `prices: [{ lessons: 1, unitPrice, totalPrice: unitPrice }]`。
- 弹窗模板（1503–1536 行）替换为单个「课时单价」输入 + 说明文案。

### 3.3 前端：报名页 `apps/erp/web/src/views/EnrollOrder.vue`

#### 3.3.1 基础价格自动带出

- 删除 `priceTemplateId` 选择器、`tierIndex`、`priceTemplateOptions`、`tierOptions`、`selectedTier`、`onPriceTemplateChange`、`onTierChange`。
- 保留 `availableTemplates`，`selectCourse` 中自动取：
  1. 课程 `defaultPriceTemplateId` 对应且可用的模板；
  2. 否则取该课程第一个可用模板；
  3. 都没有则回退课程 `listPrice`。
- `applyPriceTemplate()` 改为：取模板首个档位 `prices[0]`，设置 `unitPrice`；无模板时用 `listPrice`。
- 单价输入框 `:disabled="!!selectedPriceTemplate"`（有模板时只读），课时数继续可编辑。
- 价格区域展示模板名（静态文本），删除档位下拉。

#### 3.3.2 活动多选与叠加

- `localActivityTemplateId: number | null` → `localActivityTemplateIds: number[]`。
- 活动选择器改 `mode="multiple"`，`allow-clear`，`max-tag-count="responsive"`。
- 算价函数（现 `applyActivityTemplate`，320–344 行）改为遍历已选活动：
  ```
  discount = 0, giftLessons = 0
  对每个已选且在当前有效期内的活动：
    报课优惠：取 lessons <= 本次课时 的最大门槛规则；无匹配则跳过
    节日活动：取第一条规则
    折扣：discount += courseAmount * (1 - value / 10)
    立减：discount += value
    赠送课时：giftLessons += value
  discount = min(round2(discount), courseAmount)   // 封顶本金
  ```
- `watch([localActivityTemplateIds, courseAmount, lessons])` 重新算价。
- 删除课程/重新选课程时清空为 `[]`。
- 提交（427–446 行）改为 `localActivityTemplateIds`；`giftLessons` 仍提交汇总值。
- 活动区文案改为「可多选，优惠叠加计算」。

### 3.4 前端：快捷报名 `apps/erp/web/src/components/LocalOrderModal.vue`

- `form.localActivityTemplateId` → `form.localActivityTemplateIds: number[]`。
- `selectedActivity` → `selectedActivities`（过滤已选 id）。
- `applyActivityPricing`（252–276 行）按 3.3.2 同一规则汇总：折扣按本金分别计算后相加，立减相加，赠课相加，总优惠封顶本金。
- `watch` 改为监听数组（深监听），`form.shouldPay = max(courseAmount - 总优惠, 0)`。
- 选择器（759 行）改 `mode="multiple"`；活动优惠展示（767–770 行）展示汇总金额/赠课与活动名（`、` 拼接）。
- 提交（594–606 行）字段改为 `localActivityTemplateIds`。
- 课程切换/重置（307、409 行）清空为 `[]`。

### 3.5 前端类型 `apps/erp/web/src/api/client.ts`

- `LocalEnrollPayload.localActivityTemplateId?: number` → `localActivityTemplateIds?: number[]`（831–850 行）。

### 3.6 后端

#### `LocalOrderRequests.java`（18–39 行）

`Long localActivityTemplateId` → `List<Long> localActivityTemplateIds`。

#### `LocalOrderService.java`

- `loadLocalActivityTemplate(Long)`（289–308 行）改为 `loadLocalActivityTemplates(List<Long>)`：
  - 空/null 返回空列表；
  - 去重；
  - 每个都校验存在、`status == 1`、未开始/已过期；
  - 任一失败直接抛 `IllegalArgumentException`（保持现有中文提示）；
  - 按传入顺序返回。
- `enroll`（435–438 行）：
  ```java
  List<ActivityTemplate> activities = loadLocalActivityTemplates(request.localActivityTemplateIds());
  String activityTemplateIds = activities.stream().map(a -> String.valueOf(a.getId())).collect(joining(","));
  String activityTemplateName = activities.stream().map(ActivityTemplate::getName).collect(joining("、"));
  ```
- `writeOrderItem` 增加 `String activityTemplateIds` 参数并写入实体；`orderItems()` 输出该字段。
- `templateUnitPrice` 语义不变（单行时走 fallback 返回唯一档位单价），无需修改。

#### `SpOrderItem.java`

新增 `private String activityTemplateIds;`（映射 `activity_template_ids`）。

### 3.7 员工 App `apps/erp/app/lib/pages/payment/enroll_form.dart`

- 提交（308 行）改为：
  ```dart
  if (_activityId != null) 'localActivityTemplateIds': [_activityId],
  ```
- 页面仍保持单选（本次仅做接口适配），档位选择器在单档位下自然只剩一个选项。

## 4. 验证清单

1. 数据库执行 3.1.1，确认 `SELECT template_id, COUNT(*) FROM price_template_price GROUP BY template_id` 全部为 1，模板 1 单价 60。
2. `apps/erp/web`：`npx vue-tsc -b` 通过。
3. 价格模板页：新建/编辑只显示「课时单价」；列表显示 `￥60.00/课时`；保存后库中单行。
4. 课程页内嵌「新建价格模板」：同样只有单价。
5. 报名页（`/enrollOrder`）：
   - 选课程后单价自动带出且不可编辑，无模板/档位下拉；
   - 活动可多选，两个立减活动金额相加；折扣活动按本金计算；赠课相加；优惠不超本金；
   - 停用或过期活动不出现在选项中；直接构造请求提交停用活动时后端报错。
6. 快捷报名弹窗：同样多选并正确汇总。
7. 提交后 `sp_order_item` 的 `activity_template_ids` 为逗号分隔 id，`activity_template_name` 为顿号拼接名称。
8. 员工 App 编译通过（`flutter analyze` 可选）。

## 5. 风险与约束

- 后端需先执行 `ALTER TABLE sp_order_item` 再启动，否则 MyBatis-Plus 因实体新增字段报 SQL 错误。
- 迁移会删除历史多档位数据，仅保留最低课时档位单价；如后续需要追溯历史优惠，以订单明细快照为准。
- 停用活动校验在后端保留，前端过滤只作为体验优化，不作为安全边界。
