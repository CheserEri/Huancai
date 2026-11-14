package cn.huancai.erp.module.marketing.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.activitytemplate.entity.ActivityCategory;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplate;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityCategoryMapper;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateMapper;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.entity.SpOrderItem;
import cn.huancai.erp.module.order.mapper.SpOrderItemMapper;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 招生中心 - 活动效果：只读统计。
 *
 * <p>活动模板统一在「办理中心 - 缴费模板 - 活动模板」维护，本页只按订单明细里的
 * 活动模板快照聚合参与人数、订单数、课时与金额，不提供活动增删改。</p>
 */
@RestController
@RequestMapping("/api/marketing")
public class ActivityEffectController {

    /** 1 报名、10 补费：都算作活动带来的业绩。 */
    private static final List<Integer> ENROLL_TYPE_CODES = List.of(1, 10);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ActivityTemplateMapper templateMapper;
    private final ActivityCategoryMapper categoryMapper;
    private final SpOrderItemMapper orderItemMapper;
    private final SpOrderMapper orderMapper;
    private final StudentMapper studentMapper;

    public ActivityEffectController(ActivityTemplateMapper templateMapper,
                                    ActivityCategoryMapper categoryMapper,
                                    SpOrderItemMapper orderItemMapper,
                                    SpOrderMapper orderMapper,
                                    StudentMapper studentMapper) {
        this.templateMapper = templateMapper;
        this.categoryMapper = categoryMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderMapper = orderMapper;
        this.studentMapper = studentMapper;
    }

    /** 活动效果列表：一行一个活动模板，零参与的模板也会返回。 */
    @GetMapping("/activity-stats")
    public ApiResult<Map<String, Object>> stats(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);
        Set<Long> categoryIds = categoryId == null ? null : categoryWithChildren(categoryId);
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ActivityCategory::getId, ActivityCategory::getName, (a, b) -> a));

        List<ActivityTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<ActivityTemplate>()
                .orderByDesc(ActivityTemplate::getId));
        Snapshot snapshot = snapshot();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityTemplate template : templates) {
            if (!matchesKeyword(template, keyword) || !matchesType(template, type)
                    || !matchesCategory(template, categoryIds) || !matchesPeriod(template, from, to)) {
                continue;
            }
            Agg agg = aggregate(snapshot, template.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", template.getId());
            row.put("name", template.getName());
            row.put("categoryName", template.getCategoryId() == null
                    ? "未分类" : categoryNames.getOrDefault(template.getCategoryId(), "未分类"));
            row.put("type", template.getType());
            row.put("startDate", dateText(template.getStartDate()));
            row.put("endDate", dateText(template.getEndDate()));
            row.put("status", statusText(template));
            row.put("studentCount", agg.studentIds.size());
            row.put("newStudentCount", agg.newStudents);
            row.put("oldStudentCount", agg.oldStudents);
            row.put("orderCount", agg.orderIds.size());
            row.put("lessons", agg.lessons);
            row.put("shouldAmount", agg.shouldAmount);
            row.put("receivedAmount", agg.receivedAmount);
            row.put("discountAmount", agg.discountAmount);
            row.put("remark", template.getRemark());
            rows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("rows", rows);
        return ApiResult.ok(data);
    }

    /** 某个活动的参与学员明细：按学员汇总其在该活动下的全部报名/补费订单。 */
    @GetMapping("/activity-stats/{id}/students")
    public ApiResult<Map<String, Object>> students(@PathVariable Long id,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        if (templateMapper.selectById(id) == null) {
            return ApiResult.fail("活动模板不存在");
        }
        Snapshot snapshot = snapshot();
        Map<Long, StudentAgg> grouped = new LinkedHashMap<>();
        for (SpOrderItem item : snapshot.itemsByTemplate.getOrDefault(id, List.of())) {
            SpOrder order = snapshot.orders.get(item.getOrderId());
            if (order == null || order.getStudentId() == null) {
                continue;
            }
            StudentAgg agg = grouped.computeIfAbsent(order.getStudentId(), StudentAgg::new);
            agg.lessons = agg.lessons.add(nz(item.getLessons()));
            agg.shouldAmount = agg.shouldAmount.add(nz(item.getAmount()));
            agg.discountAmount = agg.discountAmount.add(nz(item.getDiscountAmount()));
            if (agg.orderIds.add(order.getId())) {
                agg.receivedAmount = agg.receivedAmount.add(nz(order.getRealPay()));
            }
            if (item.getCourseName() != null && !item.getCourseName().isBlank()) {
                agg.courseNames.add(item.getCourseName());
            }
            if (agg.firstOrderId == null || order.getId() < agg.firstOrderId) {
                agg.firstOrderId = order.getId();
                agg.firstJoinTime = order.getCreatedTime();
            }
            agg.campusName = agg.campusName == null ? order.getCampusName() : agg.campusName;
        }

        Map<Long, Student> studentMap = grouped.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(grouped.keySet()).stream()
                .collect(Collectors.toMap(Student::getId, student -> student, (a, b) -> a));

        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> all = new ArrayList<>();
        BigDecimal totalLessons = BigDecimal.ZERO;
        BigDecimal totalShould = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        int totalOrders = 0;
        for (Map.Entry<Long, StudentAgg> entry : grouped.entrySet()) {
            StudentAgg agg = entry.getValue();
            Student student = studentMap.get(entry.getKey());
            String name = student != null ? student.getName() : "";
            String phone = student != null && student.getPhone() != null ? student.getPhone() : "";
            String campusName = student != null && student.getCampusText() != null
                    ? student.getCampusText() : (agg.campusName == null ? "" : agg.campusName);
            if (!kw.isEmpty() && !name.contains(kw) && !phone.contains(kw) && !campusName.contains(kw)) {
                continue;
            }
            totalLessons = totalLessons.add(agg.lessons);
            totalShould = totalShould.add(agg.shouldAmount);
            totalReceived = totalReceived.add(agg.receivedAmount);
            totalDiscount = totalDiscount.add(agg.discountAmount);
            totalOrders += agg.orderIds.size();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", entry.getKey());
            row.put("studentName", name);
            row.put("phone", phone);
            row.put("campusName", campusName);
            row.put("courseNames", String.join("、", agg.courseNames));
            row.put("orderCount", agg.orderIds.size());
            row.put("lessons", agg.lessons);
            row.put("shouldAmount", agg.shouldAmount);
            row.put("receivedAmount", agg.receivedAmount);
            row.put("discountAmount", agg.discountAmount);
            row.put("firstJoinTime", agg.firstJoinTime == null ? null : agg.firstJoinTime.format(MINUTE));
            row.put("isNew", isFirstOrder(snapshot, agg));
            all.add(row);
        }
        all.sort(Comparator.comparing(
                (Map<String, Object> row) -> (String) row.get("firstJoinTime"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 200);
        int fromIndex = (int) Math.min((current - 1) * pageSize, all.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, all.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", all.size());
        summary.put("orderCount", totalOrders);
        summary.put("lessons", totalLessons);
        summary.put("shouldAmount", totalShould);
        summary.put("receivedAmount", totalReceived);
        summary.put("discountAmount", totalDiscount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", all.size());
        data.put("rows", all.subList(fromIndex, toIndex));
        data.put("summary", summary);
        return ApiResult.ok(data);
    }

    // ------------------------------------------------------------------
    // 聚合
    // ------------------------------------------------------------------

    /** 一次性读取活动订单快照，避免每个活动都查一次库。 */
    private Snapshot snapshot() {
        Snapshot snapshot = new Snapshot();
        List<SpOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<SpOrderItem>()
                .isNotNull(SpOrderItem::getActivityTemplateId));
        if (items.isEmpty()) {
            return snapshot;
        }
        Set<Long> orderIds = items.stream().map(SpOrderItem::getOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<SpOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SpOrder>()
                .in(SpOrder::getId, orderIds)
                .in(SpOrder::getTypeCode, ENROLL_TYPE_CODES)
                .isNull(SpOrder::getVoidAt));
        for (SpOrder order : orders) {
            snapshot.orders.put(order.getId(), order);
            if (order.getStudentId() != null) {
                snapshot.firstOrderByStudent.merge(order.getStudentId(), order.getId(), Math::min);
            }
        }
        for (SpOrderItem item : items) {
            if (snapshot.orders.containsKey(item.getOrderId())) {
                snapshot.itemsByTemplate.computeIfAbsent(item.getActivityTemplateId(), key -> new ArrayList<>())
                        .add(item);
            }
        }
        return snapshot;
    }

    private Agg aggregate(Snapshot snapshot, Long templateId) {
        Agg agg = new Agg();
        for (SpOrderItem item : snapshot.itemsByTemplate.getOrDefault(templateId, List.of())) {
            SpOrder order = snapshot.orders.get(item.getOrderId());
            if (order == null) {
                continue;
            }
            if (order.getStudentId() != null) {
                agg.studentIds.add(order.getStudentId());
                agg.firstOrderByStudent.merge(order.getStudentId(), order.getId(), Math::min);
            }
            if (agg.orderIds.add(order.getId())) {
                agg.receivedAmount = agg.receivedAmount.add(nz(order.getRealPay()));
            }
            agg.lessons = agg.lessons.add(nz(item.getLessons()));
            agg.shouldAmount = agg.shouldAmount.add(nz(item.getAmount()));
            agg.discountAmount = agg.discountAmount.add(nz(item.getDiscountAmount()));
        }
        for (Map.Entry<Long, Long> entry : agg.firstOrderByStudent.entrySet()) {
            if (entry.getValue().equals(snapshot.firstOrderByStudent.get(entry.getKey()))) {
                agg.newStudents++;
            } else {
                agg.oldStudents++;
            }
        }
        return agg;
    }

    /** 该学员在系统里的首笔报名订单就是这笔活动订单时，记为新生。 */
    private static boolean isFirstOrder(Snapshot snapshot, StudentAgg agg) {
        Long first = snapshot.firstOrderByStudent.get(agg.studentId);
        return first != null && first.equals(agg.firstOrderId);
    }

    // ------------------------------------------------------------------
    // 筛选与格式化
    // ------------------------------------------------------------------

    private static boolean matchesKeyword(ActivityTemplate template, String keyword) {
        return keyword == null || keyword.isBlank() || template.getName().contains(keyword.trim());
    }

    private static boolean matchesType(ActivityTemplate template, String type) {
        return type == null || type.isBlank() || type.equals(template.getType());
    }

    private static boolean matchesCategory(ActivityTemplate template, Set<Long> categoryIds) {
        return categoryIds == null || categoryIds.contains(template.getCategoryId());
    }

    /** 活动有效期与查询区间有交集即返回。 */
    private static boolean matchesPeriod(ActivityTemplate template, LocalDate from, LocalDate to) {
        if (to != null && template.getStartDate() != null && template.getStartDate().isAfter(to)) {
            return false;
        }
        return from == null || template.getEndDate() == null || !template.getEndDate().isBefore(from);
    }

    private static String statusText(ActivityTemplate template) {
        if (template.getStatus() != null && template.getStatus() == 0) {
            return "已停用";
        }
        LocalDate today = LocalDate.now();
        if (template.getStartDate() != null && today.isBefore(template.getStartDate())) {
            return "未开始";
        }
        if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
            return "已结束";
        }
        return "生效中";
    }

    /** 含子分类，按分类筛选时把下级活动一起带出。 */
    private Set<Long> categoryWithChildren(Long categoryId) {
        List<ActivityCategory> all = categoryMapper.selectList(null);
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(categoryId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ActivityCategory category : all) {
                if (category.getParentId() != null && ids.contains(category.getParentId())
                        && ids.add(category.getId())) {
                    changed = true;
                }
            }
        }
        return ids;
    }

    private static String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ------------------------------------------------------------------
    // 内部数据结构
    // ------------------------------------------------------------------

    private static final class Snapshot {
        /** 有效的报名/补费订单，按订单 id 索引。 */
        private final Map<Long, SpOrder> orders = new HashMap<>();
        /** 活动模板 id -> 订单明细。 */
        private final Map<Long, List<SpOrderItem>> itemsByTemplate = new HashMap<>();
        /** 学员 id -> 首笔报名订单 id，用于区分新老生。 */
        private final Map<Long, Long> firstOrderByStudent = new HashMap<>();
    }

    private static final class Agg {
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<Long> studentIds = new LinkedHashSet<>();
        private final Map<Long, Long> firstOrderByStudent = new HashMap<>();
        private BigDecimal lessons = BigDecimal.ZERO;
        private BigDecimal shouldAmount = BigDecimal.ZERO;
        private BigDecimal receivedAmount = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private int newStudents;
        private int oldStudents;
    }

    private static final class StudentAgg {
        private final Long studentId;
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<String> courseNames = new LinkedHashSet<>();
        private BigDecimal lessons = BigDecimal.ZERO;
        private BigDecimal shouldAmount = BigDecimal.ZERO;
        private BigDecimal receivedAmount = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private Long firstOrderId;
        private LocalDateTime firstJoinTime;
        private String campusName;

        private StudentAgg(Long studentId) {
            this.studentId = studentId;
        }
    }
}
