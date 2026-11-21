package cn.huancai.erp.module.order.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.order.dto.SpOrderQuery;
import cn.huancai.erp.module.order.dto.UpdateOrderRequest;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.entity.SpOrderItem;
import cn.huancai.erp.module.order.mapper.SpOrderItemMapper;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.order.vo.SpOrderPageVO;
import cn.huancai.erp.module.order.vo.SpOrderSummaryVO;
import cn.huancai.erp.module.order.vo.SpOrderVO;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.entity.StudentOperationLog;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.student.mapper.StudentOperationLogMapper;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 历史订单镜像查询。数据由一次性迁移脚本导入，写操作只落本地库。 */
@Service
public class SpOrderService {

    public static final int EXPORT_LIMIT = 5000;

    private static final Map<Integer, String> TYPE_NAMES = Map.ofEntries(
            Map.entry(1, "报名"), Map.entry(2, "账户余额变动"), Map.entry(3, "转课"), Map.entry(4, "停课"),
            Map.entry(5, "复课"), Map.entry(6, "结课"), Map.entry(7, "退费"), Map.entry(8, "教材杂费"),
            Map.entry(9, "积分"), Map.entry(10, "补费"), Map.entry(11, "资料费")
    );
    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            1, "待支付", 2, "已完成", 3, "已作废", 4, "已失效"
    );
    private static final Map<Integer, String> SOURCE_NAMES = Map.of(
            0, "其他", 1, "线下办理", 2, "展示页", 3, "导入", 4, "家长端", 5, "线上办理"
    );
    private static final Set<String> SORT_FIELDS = Set.of("orderNo", "stuName", "dealDate");

    private final SpOrderMapper orderMapper;
    private final CampusMapper campusMapper;
    private final SpOrderItemMapper orderItemMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentOperationLogMapper studentOperationLogMapper;
    private final OperatorResolver operatorResolver;

    public SpOrderService(SpOrderMapper orderMapper,
                          CampusMapper campusMapper,
                          SpOrderItemMapper orderItemMapper,
                          EnrollmentMapper enrollmentMapper,
                          ClassStudentMapper classStudentMapper,
                          StudentOperationLogMapper studentOperationLogMapper,
                          OperatorResolver operatorResolver) {
        this.orderMapper = orderMapper;
        this.campusMapper = campusMapper;
        this.orderItemMapper = orderItemMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentOperationLogMapper = studentOperationLogMapper;
        this.operatorResolver = operatorResolver;
    }

    public SpOrderPageVO query(SpOrderQuery query) {
        normalize(query);
        long total = orderMapper.countByQuery(query);
        SpOrderPageVO page = new SpOrderPageVO();
        page.setTotal(total);
        int offset = (query.getPageIndex() - 1) * query.getPageSize();
        page.setList(enrich(orderMapper.selectPageByQuery(query, offset, query.getPageSize())));
        page.setSummary(emptyIfNull(orderMapper.selectSummary(query)));
        return page;
    }

    public List<SpOrderVO> export(SpOrderQuery query) {
        normalize(query);
        return enrich(orderMapper.selectPageByQuery(query, 0, EXPORT_LIMIT));
    }

    public SpOrderVO detail(Long id) {
        SpOrderVO row = orderMapper.selectDetail(id);
        if (row == null) {
            return null;
        }
        enrich(List.of(row));
        row.setItems(orderItemMapper.selectList(new LambdaQueryWrapper<SpOrderItem>()
                .eq(SpOrderItem::getOrderId, id)
                .orderByAsc(SpOrderItem::getId)));
        return row;
    }

    /** 作废订单：状态置为已作废并记录原因，作废后不可还原。 */
    public SpOrderVO voidOrder(Long id, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 100) {
            throw new IllegalArgumentException("作废原因需填写 5~100 个字");
        }
        SpOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatusCode() != null && order.getStatusCode() == 3) {
            throw new IllegalArgumentException("该订单已作废，无需重复操作");
        }
        order.setStatusCode(3);
        order.setVoidReason(trimmed);
        order.setVoidAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return detail(id);
    }

    /**
     * 修改订单：基础信息 / 金额 / 明细课时按权限逐项校验，课时差值同步报读与班级成员，
     * 并写入学员操作日志（含操作人与修改前后差异）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SpOrderVO updateOrder(Long id, UpdateOrderRequest request) {
        SpOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatusCode() != null && order.getStatusCode() == 3) {
            throw new IllegalArgumentException("已作废订单不可修改");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 5 || reason.length() > 100) {
            throw new IllegalArgumentException("修改原因需填写 5~100 个字");
        }

        List<SpOrderItem> currentItems = orderItemMapper.selectList(new LambdaQueryWrapper<SpOrderItem>()
                .eq(SpOrderItem::getOrderId, id)
                .orderByAsc(SpOrderItem::getId));
        List<String> changes = new ArrayList<>();

        String content = trimToNull(request.content());
        String payMethod = trimToNull(request.payMethod());
        LocalDateTime dealDate = request.dealDate();
        BigDecimal shouldPay = request.shouldPay();
        BigDecimal realPay = request.realPay();

        boolean basicChanged = false;
        if (content != null && !content.equals(order.getContent())) {
            changes.add("交易内容「" + textOrDash(order.getContent()) + "」→「" + content + "」");
            basicChanged = true;
        }
        if (payMethod != null && !payMethod.equals(order.getPayMethod())) {
            changes.add("付款方式「" + textOrDash(order.getPayMethod()) + "」→「" + payMethod + "」");
            basicChanged = true;
        }
        if (dealDate != null && !dealDate.equals(order.getDealDate())) {
            changes.add("交易时间 " + timeText(order.getDealDate()) + " → " + timeText(dealDate));
            basicChanged = true;
        }

        boolean amountChanged = false;
        if (shouldPay != null && shouldPay.compareTo(nz(order.getShouldPay())) != 0) {
            changes.add("应收/应退 " + money(order.getShouldPay()) + " → " + money(shouldPay));
            amountChanged = true;
        }
        if (realPay != null && realPay.compareTo(nz(order.getRealPay())) != 0) {
            changes.add("实收/实退 " + money(order.getRealPay()) + " → " + money(realPay));
            amountChanged = true;
        }

        Map<Long, BigDecimal> lessonsChanges = new LinkedHashMap<>();
        if (request.items() != null) {
            for (UpdateOrderRequest.ItemLessons line : request.items()) {
                if (line == null || line.id() == null) {
                    continue;
                }
                SpOrderItem item = currentItems.stream()
                        .filter(row -> row.getId().equals(line.id()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("订单明细不存在，无法修改课时"));
                if (line.lessons() == null) {
                    continue;
                }
                if (line.lessons().signum() < 0) {
                    throw new IllegalArgumentException("课程课时不能为负数");
                }
                if (line.lessons().compareTo(nz(item.getLessons())) == 0) {
                    continue;
                }
                changes.add("课程「" + textOrDash(item.getCourseName()) + "」课时 "
                        + plain(item.getLessons()) + " → " + plain(line.lessons()));
                lessonsChanges.put(item.getId(), line.lessons());
            }
        }

        if (changes.isEmpty()) {
            throw new IllegalArgumentException("未检测到任何修改");
        }
        if (basicChanged && !operatorResolver.hasPermission("/order/edit")) {
            throw new IllegalArgumentException("缺少修改订单权限，请联系管理员开通");
        }
        if (amountChanged && !operatorResolver.hasPermission("/order/edit-amount")) {
            throw new IllegalArgumentException("缺少修改订单金额权限，请联系管理员开通");
        }
        if (!lessonsChanges.isEmpty() && !operatorResolver.hasPermission("/order/edit-lessons")) {
            throw new IllegalArgumentException("缺少修改订单课时权限，请联系管理员开通");
        }

        if (basicChanged || amountChanged) {
            if (content != null) {
                order.setContent(content);
            }
            if (payMethod != null) {
                order.setPayMethod(payMethod);
            }
            if (dealDate != null) {
                order.setDealDate(dealDate);
            }
            if (shouldPay != null) {
                order.setShouldPay(shouldPay);
            }
            if (realPay != null) {
                order.setRealPay(realPay);
            }
            if (amountChanged) {
                if (Boolean.TRUE.equals(order.getIsReturn())) {
                    order.setArrearage(BigDecimal.ZERO);
                } else {
                    BigDecimal arrearage = nz(order.getShouldPay())
                            .subtract(nz(order.getRealPay()))
                            .subtract(nz(order.getCredit()));
                    order.setArrearage(arrearage.max(BigDecimal.ZERO));
                }
            }
            orderMapper.updateById(order);
        }

        if (!lessonsChanges.isEmpty()) {
            for (SpOrderItem item : currentItems) {
                BigDecimal newLessons = lessonsChanges.get(item.getId());
                if (newLessons == null) {
                    continue;
                }
                BigDecimal delta = newLessons.subtract(nz(item.getLessons()));
                item.setLessons(newLessons);
                orderItemMapper.updateById(item);
                syncEnrollmentLessons(item.getEnrollmentId(), delta);
            }
        }

        // 只要有字段或课时变化，就记录最后修改时间，供详情页展示
        order.setUpdatedTime(LocalDateTime.now());
        orderMapper.updateById(order);

        writeOperationLog(order, "订单 " + order.getOrderNo() + " 修改："
                + String.join("；", changes) + "；原因：" + reason);
        return detail(id);
    }

    /** 课时差值同步报读与班级成员，remain 允许为负。 */
    private void syncEnrollmentLessons(Long enrollmentId, BigDecimal delta) {
        if (enrollmentId == null || delta.signum() == 0) {
            return;
        }
        Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null) {
            return;
        }
        enrollment.setTotalLessons(nz(enrollment.getTotalLessons()).add(delta));
        enrollment.setRemainLessons(nz(enrollment.getRemainLessons()).add(delta));
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);
        if (enrollment.getClassId() == null || enrollment.getStudentId() == null) {
            return;
        }
        ClassStudent member = classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, enrollment.getClassId())
                .eq(ClassStudent::getStudentId, enrollment.getStudentId())
                .last("LIMIT 1"));
        if (member == null) {
            return;
        }
        member.setTotalLessons(nz(member.getTotalLessons()).add(delta));
        member.setRemainLessons(nz(member.getRemainLessons()).add(delta));
        classStudentMapper.updateById(member);
    }

    private void writeOperationLog(SpOrder order, String content) {
        StudentOperationLog log = new StudentOperationLog();
        log.setCampusId(order.getCampusId());
        log.setStudentId(order.getStudentId());
        log.setType("订单修改");
        log.setContent(content);
        log.setOperatorId(operatorResolver.currentStaffId());
        log.setOperatorName(operatorResolver.resolve(null));
        studentOperationLogMapper.insert(log);
    }

    public Map<String, Object> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campuses", campusMapper.selectList(
                        new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> Map.of("id", c.getId(), "name", c.getName())).toList());
        data.put("creators", orderMapper.selectCreators());
        data.put("salesmen", orderMapper.selectSalesmen());
        data.put("courses", orderMapper.selectCourseNames());
        data.put("items", orderMapper.selectMaterialNames());
        data.put("tags", orderMapper.selectTags());
        // 历史订单没有回写售前人员类型，保留筛选入口但无选项。
        data.put("followUpTypes", List.of());
        return data;
    }

    private void normalize(SpOrderQuery query) {
        if (query.getPageIndex() == null || query.getPageIndex() < 1) {
            query.setPageIndex(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(50);
        }
        if (query.getPageSize() > 200) {
            query.setPageSize(200);
        }
        query.setDealStart(dateOrNull(query.getDealStart()));
        query.setDealEnd(dateOrNull(query.getDealEnd()));
        query.setKeyword(trimToNull(query.getKeyword()));
        if (!SORT_FIELDS.contains(query.getSortField())) {
            query.setSortField("orderNo");
        }
        if (!"asc".equalsIgnoreCase(query.getSortOrder())) {
            query.setSortOrder("desc");
        }
        query.setKeywordType(trimToNull(query.getKeywordType()));
    }

    private List<SpOrderVO> enrich(List<SpOrderVO> rows) {
        for (SpOrderVO row : rows) {
            row.setTypeName(TYPE_NAMES.getOrDefault(row.getTypeCode(), "-"));
            row.setStatusName(STATUS_NAMES.getOrDefault(row.getStatusCode(), "-"));
            row.setSourceName(SOURCE_NAMES.getOrDefault(row.getSourceType(), "-"));
        }
        return rows;
    }

    private SpOrderSummaryVO emptyIfNull(SpOrderSummaryVO summary) {
        if (summary != null) {
            return summary;
        }
        SpOrderSummaryVO empty = new SpOrderSummaryVO();
        empty.setCount(0L);
        empty.setShouldPay(BigDecimal.ZERO);
        empty.setRealPay(BigDecimal.ZERO);
        empty.setRemain(BigDecimal.ZERO);
        empty.setArrearage(BigDecimal.ZERO);
        empty.setCredit(BigDecimal.ZERO);
        return empty;
    }

    private String dateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches("\\d{4}-\\d{2}-\\d{2}") ? trimmed : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }

    private String money(BigDecimal value) {
        return "￥" + nz(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String timeText(LocalDateTime value) {
        return value == null ? "-" : value.toString().replace('T', ' ');
    }

    private String textOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
