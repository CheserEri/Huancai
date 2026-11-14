package cn.huancai.erp.module.order.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplate;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplateRule;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateMapper;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateRuleMapper;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.entity.CoursePrice;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.course.mapper.CoursePriceMapper;
import cn.huancai.erp.module.order.dto.LocalOrderRequests;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.entity.SpOrderItem;
import cn.huancai.erp.module.order.mapper.SpOrderItemMapper;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.order.vo.LessonConsumeResultVO;
import cn.huancai.erp.module.order.vo.LocalOrderResultVO;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplate;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplateCourse;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplatePrice;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplateCourseMapper;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplateMapper;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplatePriceMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.entity.LessonTxn;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.student.mapper.LessonTxnMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 本地闭环办理服务：报名/续费、余额充值、退费、转课。
 *
 * <p>校宝是正在使用的生产系统，本服务<b>不向校宝写入任何数据</b>。
 * 办理结果全部落在本地：sp_order（订单展示）、enrollment（报读与课时）、
 * lesson_txn（课时流水）、student（余额/欠费）。</p>
 */
@Service
public class LocalOrderService {

    private static final String STATUS_ACTIVE = "生效";
    private static final String STATUS_REFUNDED = "已退课";
    private static final String STATUS_TRANSFERRED = "已转出";
    private static final String STATUS_CLOSED = "结课";
    private static final List<String> PAY_METHODS =
            List.of("现金", "微信", "支付宝", "银行转账", "POS刷卡", "账户余额");

    private final SpOrderMapper spOrderMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;
    private final CourseMapper courseMapper;
    private final CoursePriceMapper coursePriceMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final LessonTxnMapper lessonTxnMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ActivityTemplateMapper activityTemplateMapper;
    private final ActivityTemplateRuleMapper activityTemplateRuleMapper;
    private final PriceTemplateMapper priceTemplateMapper;
    private final PriceTemplateCourseMapper priceTemplateCourseMapper;
    private final PriceTemplatePriceMapper priceTemplatePriceMapper;
    private final SpOrderItemMapper spOrderItemMapper;
    private final OperatorResolver operatorResolver;

    public LocalOrderService(SpOrderMapper spOrderMapper,
                             StudentMapper studentMapper,
                             CampusMapper campusMapper,
                             CourseMapper courseMapper,
                             CoursePriceMapper coursePriceMapper,
                             EnrollmentMapper enrollmentMapper,
                             LessonTxnMapper lessonTxnMapper,
                             ClassGroupMapper classGroupMapper,
                             ClassStudentMapper classStudentMapper,
                             ActivityTemplateMapper activityTemplateMapper,
                             ActivityTemplateRuleMapper activityTemplateRuleMapper,
                             PriceTemplateMapper priceTemplateMapper,
                             PriceTemplateCourseMapper priceTemplateCourseMapper,
                             PriceTemplatePriceMapper priceTemplatePriceMapper,
                             SpOrderItemMapper spOrderItemMapper,
                             OperatorResolver operatorResolver) {
        this.spOrderMapper = spOrderMapper;
        this.studentMapper = studentMapper;
        this.campusMapper = campusMapper;
        this.courseMapper = courseMapper;
        this.coursePriceMapper = coursePriceMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.lessonTxnMapper = lessonTxnMapper;
        this.classGroupMapper = classGroupMapper;
        this.classStudentMapper = classStudentMapper;
        this.activityTemplateMapper = activityTemplateMapper;
        this.activityTemplateRuleMapper = activityTemplateRuleMapper;
        this.priceTemplateMapper = priceTemplateMapper;
        this.priceTemplateCourseMapper = priceTemplateCourseMapper;
        this.priceTemplatePriceMapper = priceTemplatePriceMapper;
        this.spOrderItemMapper = spOrderItemMapper;
        this.operatorResolver = operatorResolver;
    }

    // ------------------------------------------------------------------
    // 查询：办理表单需要的下拉数据
    // ------------------------------------------------------------------

    public Map<String, Object> options(Long studentId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campuses", campusMapper.selectList(new LambdaQueryWrapper<Campus>()
                        .eq(Campus::getStatus, 1)
                        .orderByAsc(Campus::getId))
                .stream()
                .map(campus -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", campus.getId());
                    item.put("name", campus.getName());
                    return item;
                })
                .toList());
        data.put("courses", courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)
                        .orderByAsc(Course::getName))
                .stream()
                .map(course -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", course.getId());
                    item.put("name", course.getName());
                    item.put("campusId", course.getCampusId());
                    item.put("listPrice", course.getListPrice());
                    item.put("defaultPriceTemplateId", course.getDefaultPriceTemplateId());
                    return item;
                })
                .toList());
        data.put("payMethods", PAY_METHODS);
        data.put("classes", classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroup>()
                        .eq(ClassGroup::getStatus, "已开班")
                        .orderByAsc(ClassGroup::getName))
                .stream()
                .map(clazz -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", clazz.getId());
                    item.put("name", clazz.getName());
                    item.put("courseId", clazz.getCourseId());
                    item.put("campusId", clazz.getCampusId());
                    return item;
                })
                .toList());
        data.put("enrollments", studentId == null ? List.of() : listEnrollments(studentId));
        // 办理时只展示当前在有效期内的活动模板，避免选到已过期活动。
        List<ActivityTemplate> localActivityTemplates = activityTemplateMapper.selectList(
                new LambdaQueryWrapper<ActivityTemplate>()
                        .eq(ActivityTemplate::getStatus, 1)
                        .and(w -> w.isNull(ActivityTemplate::getStartDate)
                                .or().le(ActivityTemplate::getStartDate, LocalDate.now()))
                        .and(w -> w.isNull(ActivityTemplate::getEndDate)
                                .or().ge(ActivityTemplate::getEndDate, LocalDate.now()))
                        .orderByAsc(ActivityTemplate::getType)
                        .orderByAsc(ActivityTemplate::getId));
        Map<Long, List<ActivityTemplateRule>> localRules = localActivityTemplates.isEmpty()
                ? Map.of()
                : activityTemplateRuleMapper.selectList(new LambdaQueryWrapper<ActivityTemplateRule>()
                                .in(ActivityTemplateRule::getTemplateId,
                                        localActivityTemplates.stream().map(ActivityTemplate::getId).toList()))
                        .stream().collect(Collectors.groupingBy(ActivityTemplateRule::getTemplateId));
        data.put("activityTemplates", localActivityTemplates.stream().map(template -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", template.getId());
            item.put("name", template.getName());
            item.put("type", template.getType());
            item.put("startDate", template.getStartDate() == null ? null : template.getStartDate().toString());
            item.put("endDate", template.getEndDate() == null ? null : template.getEndDate().toString());
            List<Map<String, Object>> rules = new ArrayList<>();
            for (ActivityTemplateRule rule : localRules.getOrDefault(template.getId(), List.of())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lessons", rule.getLessons());
                row.put("discountMode", rule.getDiscountMode());
                row.put("discountValue", rule.getDiscountValue());
                rules.add(row);
            }
            item.put("rules", rules);
            return item;
        }).toList());
        data.put("priceTemplates", priceTemplateMapper.selectList(new LambdaQueryWrapper<PriceTemplate>()
                        .eq(PriceTemplate::getStatus, 1)
                        .orderByAsc(PriceTemplate::getName))
                .stream()
                .map(this::priceTemplateMap)
                .toList());
        return data;
    }

    /** 报读表单使用的价格模板快照（课程绑定 + 课时档位）。 */
    private Map<String, Object> priceTemplateMap(PriceTemplate template) {
        List<Long> courseIds = priceTemplateCourseMapper.selectList(
                        new LambdaQueryWrapper<PriceTemplateCourse>()
                                .eq(PriceTemplateCourse::getTemplateId, template.getId()))
                .stream().map(PriceTemplateCourse::getCourseId).toList();
        List<PriceTemplatePrice> tiers = priceTemplatePriceMapper.selectList(
                new LambdaQueryWrapper<PriceTemplatePrice>()
                        .eq(PriceTemplatePrice::getTemplateId, template.getId())
                        .orderByAsc(PriceTemplatePrice::getSortNo)
                        .orderByAsc(PriceTemplatePrice::getId));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", template.getId());
        item.put("name", template.getName());
        item.put("campusScope", template.getCampusScope());
        item.put("campusIds", template.getCampusIds());
        item.put("classType", template.getClassType());
        item.put("materialFee", template.getMaterialFee());
        item.put("validDays", template.getValidDays());
        item.put("courseIds", courseIds);
        item.put("prices", tiers.stream().map(tier -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lessons", tier.getLessons());
            row.put("unitPrice", tier.getUnitPrice());
            row.put("totalPrice", tier.getTotalPrice());
            return row;
        }).toList());
        return item;
    }

    /** 价格模板必须存在、启用、已绑定该课程，且校区范围匹配。 */
    private PriceTemplate resolvePriceTemplate(Long requestedId, Course course, Long campusId) {
        Long templateId = requestedId != null ? requestedId : course.getDefaultPriceTemplateId();
        if (templateId == null) {
            return null;
        }
        PriceTemplate template = priceTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("价格模板不存在");
        }
        if (template.getStatus() == null || template.getStatus() == 0) {
            throw new IllegalArgumentException("价格模板「" + template.getName() + "」已停用");
        }
        long bound = priceTemplateCourseMapper.selectCount(new LambdaQueryWrapper<PriceTemplateCourse>()
                .eq(PriceTemplateCourse::getTemplateId, templateId)
                .eq(PriceTemplateCourse::getCourseId, course.getId()));
        if (bound <= 0) {
            throw new IllegalArgumentException("价格模板「" + template.getName() + "」未绑定课程「" + course.getName() + "」");
        }
        if ("指定".equals(template.getCampusScope()) && campusId != null) {
            String ids = template.getCampusIds();
            boolean matched = ids != null && java.util.Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .anyMatch(item -> item.equals(String.valueOf(campusId)));
            if (!matched) {
                throw new IllegalArgumentException("价格模板「" + template.getName() + "」不适用于当前校区");
            }
        }
        return template;
    }

    private BigDecimal templateUnitPrice(PriceTemplate template, BigDecimal lessons) {
        List<PriceTemplatePrice> tiers = priceTemplatePriceMapper.selectList(
                new LambdaQueryWrapper<PriceTemplatePrice>()
                        .eq(PriceTemplatePrice::getTemplateId, template.getId())
                        .orderByAsc(PriceTemplatePrice::getSortNo)
                        .orderByAsc(PriceTemplatePrice::getId));
        if (tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (PriceTemplatePrice tier : tiers) {
            if (lessons.compareTo(nz(tier.getLessons())) == 0 && tier.getUnitPrice() != null) {
                return tier.getUnitPrice();
            }
        }
        return tiers.get(0).getUnitPrice() == null ? BigDecimal.ZERO : tiers.get(0).getUnitPrice();
    }

    /** 本系统活动模板页维护的优惠模板，办理时逐个校验状态与有效期。 */
    private List<ActivityTemplate> loadLocalActivityTemplates(List<Long> activityTemplateIds) {
        if (activityTemplateIds == null || activityTemplateIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<ActivityTemplate> result = new ArrayList<>();
        for (Long activityTemplateId : activityTemplateIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ActivityTemplate template = activityTemplateMapper.selectById(activityTemplateId);
            if (template == null) {
                throw new IllegalArgumentException("优惠模板不存在");
            }
            if (template.getStatus() == null || template.getStatus() != 1) {
                throw new IllegalArgumentException("优惠模板「" + template.getName() + "」已停用");
            }
            if (template.getStartDate() != null && today.isBefore(template.getStartDate())) {
                throw new IllegalArgumentException("优惠模板「" + template.getName() + "」未到开始日期");
            }
            if (template.getEndDate() != null && today.isAfter(template.getEndDate())) {
                throw new IllegalArgumentException("优惠模板「" + template.getName() + "」已过有效期");
            }
            result.add(template);
        }
        return result;
    }

    /** 订单明细保存模板明文快照，模板改名或停用不影响历史订单。 */
    private void writeOrderItem(SpOrder order, Enrollment enrollment, Course course,
                                PriceTemplate priceTemplate,
                                Long activityTemplateId, String activityTemplateName,
                                String activityTemplateIds,
                                BigDecimal lessons, BigDecimal unitPrice, BigDecimal baseAmount,
                                BigDecimal discount, BigDecimal materialAmount, BigDecimal amount) {
        SpOrderItem item = new SpOrderItem();
        item.setOrderId(order.getId());
        item.setEnrollmentId(enrollment == null ? null : enrollment.getId());
        item.setCourseId(course.getId());
        item.setCourseName(course.getName());
        item.setPriceTemplateId(priceTemplate == null ? null : priceTemplate.getId());
        item.setPriceTemplateName(priceTemplate == null ? null : priceTemplate.getName());
        item.setActivityTemplateId(activityTemplateId);
        item.setActivityTemplateName(activityTemplateName);
        item.setActivityTemplateIds(activityTemplateIds);
        item.setLessons(lessons);
        item.setUnitPrice(unitPrice);
        item.setBaseAmount(baseAmount);
        item.setDiscountAmount(discount);
        item.setMaterialAmount(materialAmount);
        item.setAmount(amount);
        spOrderItemMapper.insert(item);
    }

    /** 订单列表展开明细：查看每笔缴费使用了哪些价格模板/活动模板。 */
    public List<Map<String, Object>> orderItems(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        List<SpOrderItem> items = spOrderItemMapper.selectList(new LambdaQueryWrapper<SpOrderItem>()
                .in(SpOrderItem::getOrderId, orderIds)
                .orderByAsc(SpOrderItem::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SpOrderItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("orderId", item.getOrderId());
            row.put("enrollmentId", item.getEnrollmentId());
            row.put("courseId", item.getCourseId());
            row.put("courseName", item.getCourseName());
            row.put("priceTemplateId", item.getPriceTemplateId());
            row.put("priceTemplateName", item.getPriceTemplateName());
            row.put("activityTemplateId", item.getActivityTemplateId());
            row.put("activityTemplateName", item.getActivityTemplateName());
            row.put("activityTemplateIds", item.getActivityTemplateIds());
            row.put("lessons", item.getLessons());
            row.put("unitPrice", item.getUnitPrice());
            row.put("baseAmount", item.getBaseAmount());
            row.put("discountAmount", item.getDiscountAmount());
            row.put("materialAmount", item.getMaterialAmount());
            row.put("amount", item.getAmount());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> listEnrollments(Long studentId) {
        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .orderByDesc(Enrollment::getId));
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Course> courses = courseIds.isEmpty()
                ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Function.identity()));
        List<Long> templateIds = enrollments.stream()
                .map(Enrollment::getPriceTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> templateNames = templateIds.isEmpty()
                ? Map.of()
                : priceTemplateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(PriceTemplate::getId, PriceTemplate::getName));
        List<Map<String, Object>> result = new ArrayList<>();
        enrollments.stream()
                .sorted((a, b) -> Boolean.compare(!STATUS_ACTIVE.equals(b.getStatus()),
                        !STATUS_ACTIVE.equals(a.getStatus())))
                .forEach(enrollment -> {
                    Course course = enrollment.getCourseId() == null
                            ? null : courses.get(enrollment.getCourseId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", enrollment.getId());
                    item.put("courseId", enrollment.getCourseId());
                    item.put("courseName", course == null ? null : course.getName());
                    item.put("campusId", enrollment.getCampusId());
                    item.put("classId", enrollment.getClassId());
                    item.put("totalLessons", enrollment.getTotalLessons());
                    item.put("remainLessons", enrollment.getRemainLessons());
                    item.put("paidAmount", enrollment.getPaidAmount());
                    item.put("unitPrice", enrollment.getUnitPrice());
                    item.put("priceTemplateId", enrollment.getPriceTemplateId());
                    item.put("priceTemplateName", enrollment.getPriceTemplateId() == null
                            ? null : templateNames.get(enrollment.getPriceTemplateId()));
                    item.put("arrears", enrollmentArrears(enrollment));
                    item.put("accountBalance", accountBalance(enrollment));
                    item.put("expireDate", enrollment.getExpireDate());
                    item.put("status", enrollment.getStatus());
                    result.add(item);
                });
        return result;
    }

    // ------------------------------------------------------------------
    // 报名 / 续费
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO enroll(LocalOrderRequests.Enroll request) {
        Student student = requireStudent(request.studentId());
        Course course = requireCourse(request.courseId(), "报读课程");
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        String className = resolveClassName(request.classId(), course);

        BigDecimal lessons = positiveLessons(request.lessons(), "报读课时");
        BigDecimal gift = nonNegativeLessons(request.giftLessons(), "赠送课时");

        // 价格必须来自与课程绑定的基础价格模板；未指定时使用课程默认模板。
        PriceTemplate priceTemplate = resolvePriceTemplate(request.priceTemplateId(), course, campusId);
        List<ActivityTemplate> localActivityTemplates =
                loadLocalActivityTemplates(request.localActivityTemplateIds());
        Long activityTemplateId = localActivityTemplates.isEmpty()
                ? null : localActivityTemplates.get(0).getId();
        String activityTemplateName = localActivityTemplates.stream()
                .map(ActivityTemplate::getName)
                .collect(Collectors.joining("、"));
        String activityTemplateIds = localActivityTemplates.stream()
                .map(template -> String.valueOf(template.getId()))
                .collect(Collectors.joining(","));

        BigDecimal unitPrice = money(request.unitPrice()).max(BigDecimal.ZERO);
        if (priceTemplate != null && unitPrice.signum() <= 0) {
            unitPrice = templateUnitPrice(priceTemplate, lessons);
        }
        BigDecimal discount = money(request.discount()).max(BigDecimal.ZERO);
        BigDecimal materialAmount = materialAmount(request.materials());
        BigDecimal courseAmount = unitPrice.signum() > 0
                ? money(lessons.multiply(unitPrice))
                : money(request.shouldPay());
        // 未填单价时按应收折算，保证报读有单价，补费/课耗都能按应收核算。
        BigDecimal storedUnitPrice = unitPrice.signum() > 0
                ? unitPrice
                : (lessons.signum() > 0
                        ? money(courseAmount.divide(lessons, 2, RoundingMode.HALF_UP))
                        : BigDecimal.ZERO);
        BigDecimal should = money(courseAmount.add(materialAmount));
        BigDecimal payable = should.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal received = money(request.received());
        if (received.signum() == 0 && payable.signum() > 0) {
            received = payable;
        }
        // 应收为优惠前原价，实收必须等于应付（应收 - 优惠），不允许赊账。
        if (received.compareTo(payable) != 0) {
            throw new IllegalArgumentException("实收金额必须等于应付金额（应收￥" + plain(should)
                    + " - 优惠￥" + plain(discount) + " = 应付￥" + plain(payable) + "），不支持赊账");
        }
        BigDecimal arrearage = BigDecimal.ZERO;

        LocalDate expireDate = parseDate(request.expireDate());
        if (expireDate == null && priceTemplate != null
                && priceTemplate.getValidDays() != null && priceTemplate.getValidDays() > 0) {
            expireDate = LocalDate.now().plusDays(priceTemplate.getValidDays());
        }

        Enrollment current = findActiveEnrollment(student.getId(), course.getId());
        boolean renew = current != null;
        BigDecimal oldOverdraft = current == null ? BigDecimal.ZERO : overdraft(current);
        String typeName = renew ? "续费" : "报名";
        String content = buildEnrollContent(typeName, course.getName(), className,
                lessons, gift, should, received, arrearage, materialAmount, discount, request.materials());
        String remark = text(request.remark());
        String creator = operatorResolver.resolve(request.creator());
        String payMethod = text(request.payMethod());

        SpOrder order = insertOrder(student, campusId, campusName, 1, false,
                should, received, BigDecimal.ZERO, arrearage, payMethod, creator,
                content, remark, request.tags());
        if (materialAmount.signum() > 0) {
            order.setHasTextbook(true);
            spOrderMapper.updateById(order);
        }

        Enrollment enrollment = upsertEnrollment(student, course, campusId, request.classId(),
                lessons, gift, received, discount, storedUnitPrice, expireDate, order.getId(),
                priceTemplate == null ? null : priceTemplate.getId());
        writeTxn(enrollment, lessons.add(gift), "授予", order.getId(), content);
        writeOrderItem(order, enrollment, course, priceTemplate, activityTemplateId, activityTemplateName,
                activityTemplateIds,
                lessons, storedUnitPrice, courseAmount, discount, materialAmount, should);

        int active = refreshCourseCount(student.getId());
        student.setCourseCount(active);
        BigDecimal debt = nz(student.getDebt());
        // 同一课程续费时，实收会冲减该报读的超支，欠费同步减少。
        BigDecimal settled = oldOverdraft.subtract(overdraft(enrollment));
        if (settled.signum() > 0) {
            debt = debt.subtract(settled).max(BigDecimal.ZERO);
        }
        if (arrearage.signum() > 0) {
            debt = debt.add(arrearage);
        }
        student.setDebt(money(debt));
        studentMapper.updateById(student);

        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), typeName, content,
                student.getBalance(), enrollment.getRemainLessons());
    }

    /** 教材杂费合计：单价 × 数量。 */
    private static BigDecimal materialAmount(List<LocalOrderRequests.EnrollMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LocalOrderRequests.EnrollMaterial material : materials) {
            BigDecimal price = money(material.price()).max(BigDecimal.ZERO);
            BigDecimal quantity = nz(material.quantity()).max(BigDecimal.ZERO);
            total = total.add(money(price.multiply(quantity)));
        }
        return money(total);
    }

    private Enrollment upsertEnrollment(Student student, Course course, Long campusId, Long classId,
                                        BigDecimal lessons, BigDecimal gift, BigDecimal received,
                                        BigDecimal discount, BigDecimal unitPrice, LocalDate expireDate,
                                        Long orderId, Long priceTemplateId) {
        Enrollment enrollment = findActiveEnrollment(student.getId(), course.getId());
        Long previousClassId = enrollment == null ? null : enrollment.getClassId();
        if (enrollment == null) {
            enrollment = new Enrollment();
            enrollment.setCampusId(campusId);
            enrollment.setStudentId(student.getId());
            enrollment.setCourseId(course.getId());
            enrollment.setClassId(classId);
            enrollment.setOrderId(orderId);
            enrollment.setTotalLessons(lessons);
            enrollment.setGiftLessons(gift);
            enrollment.setUsedLessons(BigDecimal.ZERO);
            enrollment.setRemainLessons(lessons.add(gift));
            enrollment.setPaidAmount(received);
            enrollment.setDiscountAmount(discount);
            enrollment.setUnitPrice(unitPrice.signum() > 0 ? unitPrice : null);
            enrollment.setPriceTemplateId(priceTemplateId);
            enrollment.setExpireDate(expireDate);
            enrollment.setStatus(STATUS_ACTIVE);
            enrollment.setCreatedAt(LocalDateTime.now());
            enrollment.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.insert(enrollment);
            syncClassStudent(enrollment, previousClassId);
            return enrollment;
        }
        enrollment.setTotalLessons(nz(enrollment.getTotalLessons()).add(lessons));
        enrollment.setGiftLessons(nz(enrollment.getGiftLessons()).add(gift));
        enrollment.setRemainLessons(nz(enrollment.getRemainLessons()).add(lessons).add(gift));
        enrollment.setPaidAmount(nz(enrollment.getPaidAmount()).add(received));
        enrollment.setDiscountAmount(nz(enrollment.getDiscountAmount()).add(discount));
        if (classId != null) {
            enrollment.setClassId(classId);
        }
        if (unitPrice.signum() > 0) {
            enrollment.setUnitPrice(unitPrice);
        }
        if (priceTemplateId != null) {
            enrollment.setPriceTemplateId(priceTemplateId);
        }
        if (expireDate != null) {
            enrollment.setExpireDate(expireDate);
        }
        enrollment.setStatus(STATUS_ACTIVE);
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);
        syncClassStudent(enrollment, previousClassId);
        return enrollment;
    }

    /** 报名/消课/退费/转课后，把课时的最新值同步到班级成员。 */
    private void syncClassStudent(Enrollment enrollment, Long previousClassId) {
        Long classId = enrollment.getClassId();
        if (previousClassId != null && !Objects.equals(previousClassId, classId)) {
            classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, previousClassId)
                    .eq(ClassStudent::getStudentId, enrollment.getStudentId()));
            refreshClassEnrolled(previousClassId);
        }
        if (classId == null) {
            return;
        }
        // 报读已转出、已退课或已结课：同步清退班级成员，避免班级人数继续统计已离班学员
        if (STATUS_TRANSFERRED.equals(enrollment.getStatus())
                || STATUS_REFUNDED.equals(enrollment.getStatus())
                || STATUS_CLOSED.equals(enrollment.getStatus())) {
            classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, classId)
                    .eq(ClassStudent::getStudentId, enrollment.getStudentId()));
            refreshClassEnrolled(classId);
            return;
        }
        ClassStudent member = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, classId)
                        .eq(ClassStudent::getStudentId, enrollment.getStudentId()))
                .stream().findFirst().orElse(null);
        BigDecimal total = nz(enrollment.getTotalLessons()).add(nz(enrollment.getGiftLessons()));
        BigDecimal used = nz(enrollment.getUsedLessons());
        BigDecimal remain = nz(enrollment.getRemainLessons());
        if (member == null) {
            member = new ClassStudent();
            member.setCampusId(enrollment.getCampusId());
            member.setClassId(classId);
            member.setStudentId(enrollment.getStudentId());
            member.setStatus("在读");
            member.setJoinDate(LocalDateTime.now());
            member.setTotalLessons(total);
            member.setUsedLessons(used);
            member.setRemainLessons(remain);
            classStudentMapper.insert(member);
        } else {
            member.setTotalLessons(total);
            member.setUsedLessons(used);
            member.setRemainLessons(remain);
            classStudentMapper.updateById(member);
        }
        refreshClassEnrolled(classId);
    }

    /** 报名自动分班或转入转出后，刷新班级人数与满班率，避免班级列表显示 0 人。 */
    private void refreshClassEnrolled(Long classId) {
        ClassGroup clazz = classGroupMapper.selectById(classId);
        if (clazz == null) {
            return;
        }
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classId));
        int enrolled = count == null ? 0 : count.intValue();
        clazz.setEnrolledCount(enrolled);
        Integer capacity = clazz.getCapacity();
        clazz.setStuRate(capacity == null || capacity <= 0
                ? null
                : BigDecimal.valueOf(enrolled * 100.0 / capacity).setScale(2, RoundingMode.HALF_UP));
        classGroupMapper.updateById(clazz);
    }

    // ------------------------------------------------------------------
    // 余额充值
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO recharge(LocalOrderRequests.Recharge request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        BigDecimal amount = positiveMoney(request.amount(), "充值金额");

        // 有欠费时优先冲抵，剩余部分才计入余额。
        BigDecimal debtBefore = money(nz(student.getDebt()));
        BigDecimal debtOffset = amount.min(debtBefore);
        BigDecimal balanceAdd = amount.subtract(debtOffset);
        if (debtOffset.signum() > 0) {
            settleOverdraft(student.getId(), debtOffset);
            student.setDebt(money(debtBefore.subtract(debtOffset)));
        }
        BigDecimal balance = money(nz(student.getBalance()).add(balanceAdd));
        student.setBalance(balance);
        studentMapper.updateById(student);

        String payMethod = text(request.payMethod());
        String content = "余额充值：￥" + plain(amount) + (payMethod == null ? "" : "（" + payMethod + "）");
        if (debtOffset.signum() > 0) {
            content = content + "，冲抵欠费￥" + plain(debtOffset)
                    + (balanceAdd.signum() > 0 ? "，入账余额￥" + plain(balanceAdd) : "");
        }
        String creator = operatorResolver.resolve(request.creator());

        SpOrder order = insertOrder(student, campusId, campusName, 2, false,
                amount, amount, balanceAdd, BigDecimal.ZERO, payMethod, creator,
                content, text(request.remark()), request.tags());

        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "余额充值", content,
                balance, null);
    }

    // ------------------------------------------------------------------
    // 补费
    // ------------------------------------------------------------------

    /** 补费：按报读科目冲减该报读的待补金额，欠费同步减少，金额不进账户余额。 */
    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO subsidy(LocalOrderRequests.Subsidy request) {
        Student student = requireStudent(request.studentId());
        Enrollment enrollment = requireEnrollment(request.enrollmentId(), student.getId());
        Course course = courseMapper.selectById(enrollment.getCourseId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        BigDecimal amount = positiveMoney(request.amount(), "补费金额");

        BigDecimal arrears = enrollmentArrears(enrollment);
        if (arrears.signum() <= 0) {
            throw new IllegalArgumentException("该报读没有待补费用");
        }
        if (amount.compareTo(arrears) > 0) {
            throw new IllegalArgumentException("补费金额不能大于该报读待补费用（￥" + plain(arrears) + "）");
        }

        enrollment.setPaidAmount(money(nz(enrollment.getPaidAmount()).add(amount)));
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);

        BigDecimal debt = money(nz(student.getDebt()).subtract(amount)).max(BigDecimal.ZERO);
        student.setDebt(debt);
        studentMapper.updateById(student);

        String courseName = course == null ? "报读课程" : course.getName();
        String content = "补费：" + courseName + "，补￥" + plain(amount)
                + "（原待补￥" + plain(arrears) + "）";
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());

        SpOrder order = insertOrder(student, campusId, campusName, 10, false,
                amount, amount, BigDecimal.ZERO, debt, payMethod, creator,
                content, text(request.remark()), request.tags());

        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "补费", content,
                student.getBalance(), enrollment.getRemainLessons());
    }

    /** 充值冲抵欠费：优先冲减各报读的超支（已课耗金额超过报读实收的部分）。 */
    private void settleOverdraft(Long studentId, BigDecimal offset) {
        BigDecimal remaining = offset;
        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .orderByAsc(Enrollment::getId));
        for (Enrollment enrollment : enrollments) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal overdraft = overdraft(enrollment);
            if (overdraft.signum() <= 0) {
                continue;
            }
            BigDecimal applied = remaining.min(overdraft);
            enrollment.setPaidAmount(money(nz(enrollment.getPaidAmount()).add(applied)));
            enrollment.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.updateById(enrollment);
            remaining = remaining.subtract(applied);
        }
    }

    // ------------------------------------------------------------------
    // 退费
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO refund(LocalOrderRequests.Refund request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        BigDecimal amount = positiveMoney(request.amount(), "退费金额");
        BigDecimal cutLessons = nonNegativeLessons(request.lessons(), "扣减课时");

        Enrollment enrollment = null;
        Course course = null;
        if (request.enrollmentId() != null) {
            enrollment = requireEnrollment(request.enrollmentId(), student.getId());
            course = courseMapper.selectById(enrollment.getCourseId());
            if (cutLessons.signum() > 0) {
                BigDecimal remain = nz(enrollment.getRemainLessons());
                if (remain.compareTo(cutLessons) < 0) {
                    throw new IllegalArgumentException("扣减课时不能大于剩余课时（剩余 "
                            + plain(remain) + " 课时）");
                }
                enrollment.setRemainLessons(remain.subtract(cutLessons));
                enrollment.setTotalLessons(nz(enrollment.getTotalLessons()).subtract(cutLessons)
                        .max(BigDecimal.ZERO));
            }
            enrollment.setPaidAmount(nz(enrollment.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO));
            if (cutLessons.signum() > 0 && nz(enrollment.getRemainLessons()).signum() == 0) {
                enrollment.setStatus(STATUS_REFUNDED);
            }
            enrollment.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.updateById(enrollment);
            syncClassStudent(enrollment, null);
        }

        String reason = text(request.reason());
        String content = "退费：" + (course == null ? "" : course.getName() + "，")
                + (cutLessons.signum() > 0 ? "退" + plain(cutLessons) + "课时，" : "")
                + "退￥" + plain(amount)
                + (reason == null ? "" : "，原因：" + reason);
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());

        SpOrder order = insertOrder(student, campusId, campusName, 7, true,
                amount.negate(), amount.negate(), BigDecimal.ZERO, BigDecimal.ZERO,
                payMethod, creator, content, reason, null);

        if (enrollment != null && cutLessons.signum() > 0) {
            writeTxn(enrollment, cutLessons.negate(), "退课", order.getId(), content);
        }
        student.setCourseCount(refreshCourseCount(student.getId()));
        studentMapper.updateById(student);
        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "退费", content,
                student.getBalance(), enrollment == null ? null : enrollment.getRemainLessons());
    }

    /**
     * 批量退费：整单退掉选中的报读（退剩余课时、冲减绑定学费），并可同时退账户剩余未用学费。
     * 支持只退一门课程、只退账户余额，或多选一次性全退。
     */
    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO refundBatch(LocalOrderRequests.BatchRefund request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        List<Long> enrollmentIds = request.enrollmentIds() == null ? List.of()
                : request.enrollmentIds().stream().filter(Objects::nonNull).distinct().toList();
        BigDecimal balanceAmount = nonNegativeMoney(request.balanceAmount(), "退账户余额");
        if (enrollmentIds.isEmpty() && balanceAmount.signum() <= 0) {
            throw new IllegalArgumentException("请选择要退费的课程或账户余额");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        List<Map.Entry<Enrollment, BigDecimal>> refunded = new ArrayList<>();
        for (Long enrollmentId : enrollmentIds) {
            Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
            if (enrollment == null || !Objects.equals(enrollment.getStudentId(), student.getId())) {
                throw new IllegalArgumentException("请选择该学员的报读记录");
            }
            if (!STATUS_ACTIVE.equals(enrollment.getStatus()) && !"停课".equals(enrollment.getStatus())) {
                throw new IllegalArgumentException("报读记录状态为「" + enrollment.getStatus() + "」，不能退费");
            }
            Course course = courseMapper.selectById(enrollment.getCourseId());
            String courseName = course == null ? "报读#" + enrollment.getId() : course.getName();
            // 可退学费 = 报读实收 - 已课耗金额
            BigDecimal amount = money(nz(enrollment.getPaidAmount())
                    .subtract(nz(enrollment.getConsumedAmount())).max(BigDecimal.ZERO));
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("报读「" + courseName + "」没有可退学费");
            }
            BigDecimal lessons = nz(enrollment.getRemainLessons()).max(BigDecimal.ZERO);
            // 退剩余课时并冲减绑定学费，使该报读账户余额归零
            enrollment.setTotalLessons(nz(enrollment.getTotalLessons()).subtract(lessons).max(BigDecimal.ZERO));
            enrollment.setRemainLessons(BigDecimal.ZERO);
            enrollment.setPaidAmount(nz(enrollment.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO));
            enrollment.setStatus(STATUS_REFUNDED);
            enrollment.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.updateById(enrollment);
            syncClassStudent(enrollment, null);
            refunded.add(Map.entry(enrollment, lessons));
            total = total.add(amount);
            parts.add(courseName + "（退" + plain(lessons) + "课时，￥" + plain(amount) + "）");
        }

        BigDecimal pool = money(nz(student.getBalance()));
        if (balanceAmount.compareTo(pool) > 0) {
            throw new IllegalArgumentException("退费金额不能大于账户剩余未用学费（可用 ￥" + plain(pool) + "）");
        }
        if (balanceAmount.signum() > 0) {
            student.setBalance(money(pool.subtract(balanceAmount)));
            total = total.add(balanceAmount);
            parts.add("账户未用学费￥" + plain(balanceAmount));
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("所选项目可退金额为 0，无需退费");
        }

        String reason = text(request.reason());
        String content = "退费：" + String.join("；", parts)
                + (reason == null ? "" : "；原因：" + reason);
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());
        SpOrder order = insertOrder(student, campusId, campusName, 7, true,
                total.negate(), total.negate(), BigDecimal.ZERO, BigDecimal.ZERO,
                payMethod, creator, content, reason, null);
        for (Map.Entry<Enrollment, BigDecimal> line : refunded) {
            writeTxn(line.getKey(), line.getValue().negate(), "退课", order.getId(), content);
        }
        student.setCourseCount(refreshCourseCount(student.getId()));
        studentMapper.updateById(student);
        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "退费", content,
                student.getBalance(), null);
    }

    /** 退账户剩余未用学费：从学员账户余额中退款，生成退费订单。 */
    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO refundBalance(LocalOrderRequests.BalanceRefund request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        BigDecimal amount = positiveMoney(request.amount(), "退费金额");
        BigDecimal pool = money(nz(student.getBalance()));
        if (amount.compareTo(pool) > 0) {
            throw new IllegalArgumentException("退费金额不能大于账户剩余未用学费（可用 ￥" + plain(pool) + "）");
        }
        String reason = text(request.reason());
        String content = "退账户未用学费 ￥" + plain(amount) + (reason == null ? "" : "，原因：" + reason);
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());

        SpOrder order = insertOrder(student, campusId, campusName, 7, true,
                amount.negate(), amount.negate(), BigDecimal.ZERO, BigDecimal.ZERO,
                payMethod, creator, content, reason, null);

        student.setBalance(money(pool.subtract(amount)));
        studentMapper.updateById(student);
        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "退未用学费", content,
                student.getBalance(), null);
    }

    // ------------------------------------------------------------------
    // 转课
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO transfer(LocalOrderRequests.Transfer request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        Enrollment from = requireEnrollment(request.fromEnrollmentId(), student.getId());
        Course fromCourse = courseMapper.selectById(from.getCourseId());
        Course toCourse = requireCourse(request.toCourseId(), "转入课程");
        if (Objects.equals(from.getCourseId(), toCourse.getId())) {
            throw new IllegalArgumentException("转入课程不能与转出课程相同");
        }

        boolean transferAll = "全部".equals(text(request.transferType()));
        BigDecimal remain = nz(from.getRemainLessons()).max(BigDecimal.ZERO);
        if (remain.signum() <= 0) {
            throw new IllegalArgumentException("转出课程没有可转出的剩余课时");
        }
        BigDecimal outLessons = transferAll
                ? remain
                : positiveLessons(request.lessons(), "本次转出课时");
        if (outLessons.compareTo(remain) > 0) {
            throw new IllegalArgumentException("本次转出课时不能大于剩余课时（剩余 "
                    + plain(remain) + " 课时）");
        }

        // 转出金额按原报读单价折算，与校宝「本次转出金额」口径一致。
        BigDecimal fromUnitPrice = resolveEnrollmentUnitPrice(from);
        BigDecimal outAmount = money(outLessons.multiply(fromUnitPrice));

        boolean valueTransfer = request.inLessons() == null || request.inLessons().signum() <= 0;
        BigDecimal inLessons = valueTransfer
                ? outLessons
                : positiveLessons(request.inLessons(), "转入课时");
        PriceTemplate toTemplate = findCoursePriceTemplate(toCourse, campusId);
        BigDecimal inUnitPrice;
        if (request.inUnitPrice() != null && request.inUnitPrice().signum() > 0) {
            inUnitPrice = positiveMoney(request.inUnitPrice(), "转入单价");
        } else if (valueTransfer) {
            inUnitPrice = fromUnitPrice;
        } else if (toTemplate != null && templateUnitPrice(toTemplate, inLessons).signum() > 0) {
            inUnitPrice = money(templateUnitPrice(toTemplate, inLessons));
        } else if (nz(toCourse.getListPrice()).signum() > 0) {
            inUnitPrice = money(toCourse.getListPrice());
        } else {
            inUnitPrice = fromUnitPrice;
        }
        BigDecimal discount = nonNegativeMoney(request.discount(), "转入优惠");
        BigDecimal baseInAmount = money(inLessons.multiply(inUnitPrice));
        BigDecimal inAmount = baseInAmount.subtract(discount).max(BigDecimal.ZERO);

        // 转出金额与转入应收的差额：大于 0 应退，小于 0 应收。
        BigDecimal diff = money(outAmount.subtract(inAmount));
        BigDecimal surplus = diff.signum() > 0 ? diff : BigDecimal.ZERO;
        BigDecimal shortfall = diff.signum() < 0 ? diff.negate() : BigDecimal.ZERO;
        BigDecimal refundCash = nonNegativeMoney(request.refundCash(), "实退金额");
        if (refundCash.compareTo(surplus) > 0) {
            throw new IllegalArgumentException("实退金额不能大于应退总计￥" + plain(surplus));
        }
        // 应退差额未实退的部分统一计入账户剩余未用学费（学员余额），后续可单独退费
        BigDecimal balanceAdd = money(surplus.subtract(refundCash));
        BigDecimal received = nonNegativeMoney(request.extraPay(), "实收金额");
        BigDecimal unpaid = shortfall.subtract(received).max(BigDecimal.ZERO);

        String toClassName = resolveClassName(request.toClassId(), toCourse);
        LocalDate expireDate = parseDate(request.expireDate());

        // 原报读按转出的课时与金额同步扣减，保证剩余学费与欠费口径一致。
        from.setRemainLessons(remain.subtract(outLessons));
        from.setTotalLessons(nz(from.getTotalLessons()).subtract(outLessons).max(BigDecimal.ZERO));
        from.setPaidAmount(nz(from.getPaidAmount()).subtract(outAmount).max(BigDecimal.ZERO));
        if (from.getRemainLessons().signum() == 0) {
            from.setStatus(STATUS_TRANSFERRED);
        }
        from.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(from);
        syncClassStudent(from, null);

        Enrollment to = findActiveEnrollment(student.getId(), toCourse.getId());
        Long previousClassId = to == null ? null : to.getClassId();
        if (to == null) {
            to = new Enrollment();
            to.setCampusId(campusId);
            to.setStudentId(student.getId());
            to.setCourseId(toCourse.getId());
            to.setClassId(request.toClassId());
            to.setTotalLessons(inLessons);
            to.setGiftLessons(BigDecimal.ZERO);
            to.setUsedLessons(BigDecimal.ZERO);
            to.setRemainLessons(inLessons);
            to.setPaidAmount(inAmount);
            to.setDiscountAmount(discount);
            to.setConsumedAmount(BigDecimal.ZERO);
            to.setUnitPrice(inUnitPrice);
            to.setPriceTemplateId(toTemplate == null ? null : toTemplate.getId());
            to.setExpireDate(expireDate);
            to.setStatus(STATUS_ACTIVE);
            to.setCreatedAt(LocalDateTime.now());
            to.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.insert(to);
        } else {
            to.setTotalLessons(nz(to.getTotalLessons()).add(inLessons));
            to.setRemainLessons(nz(to.getRemainLessons()).add(inLessons));
            to.setPaidAmount(nz(to.getPaidAmount()).add(inAmount));
            to.setDiscountAmount(nz(to.getDiscountAmount()).add(discount));
            to.setUnitPrice(inUnitPrice);
            if (request.toClassId() != null) {
                to.setClassId(request.toClassId());
            }
            if (expireDate != null) {
                to.setExpireDate(expireDate);
            }
            if (toTemplate != null) {
                to.setPriceTemplateId(toTemplate.getId());
            }
            to.setStatus(STATUS_ACTIVE);
            to.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.updateById(to);
        }
        syncClassStudent(to, previousClassId);

        if (balanceAdd.signum() > 0) {
            student.setBalance(money(nz(student.getBalance()).add(balanceAdd)));
        }
        if (unpaid.signum() > 0) {
            student.setDebt(money(nz(student.getDebt()).add(unpaid)));
        }

        String content = buildTransferContent(fromCourse, toCourse, toClassName,
                outLessons, fromUnitPrice, outAmount, inLessons, inUnitPrice, discount, inAmount,
                surplus, refundCash, balanceAdd, shortfall, received, unpaid);
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());
        // 应收费为转入课程金额，实收费只统计本次实际新收的现金（余下由转出课程价值冲抵）
        // 转课是「用原报读价值冲抵新报读」，主金额为转入应收，不应标记为退单
        SpOrder order = insertOrder(student, campusId, campusName, 3, false,
                inAmount, received, balanceAdd, unpaid, payMethod, creator,
                content, text(request.remark()), request.tags());
        String outerRemark = text(request.outerRemark());
        if (outerRemark != null) {
            order.setCommentOuter(outerRemark);
            spOrderMapper.updateById(order);
        }
        writeOrderItem(order, to, toCourse, toTemplate, null, null, null,
                inLessons, inUnitPrice, baseInAmount, discount, BigDecimal.ZERO, inAmount);

        writeTxn(from, outLessons.negate(), "转出", order.getId(), content);
        writeTxn(to, inLessons, "转入", order.getId(), content);

        student.setCourseCount(refreshCourseCount(student.getId()));
        studentMapper.updateById(student);

        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "转课", content,
                student.getBalance(), to.getRemainLessons());
    }

    /** 转课订单摘要：与校宝「转出/转入 + 应退或应收」的展示口径一致。 */
    private String buildTransferContent(Course fromCourse, Course toCourse, String toClassName,
                                        BigDecimal outLessons, BigDecimal fromUnitPrice,
                                        BigDecimal outAmount, BigDecimal inLessons,
                                        BigDecimal inUnitPrice, BigDecimal discount,
                                        BigDecimal inAmount, BigDecimal surplus,
                                        BigDecimal refundCash, BigDecimal balanceAdd,
                                        BigDecimal shortfall, BigDecimal received,
                                        BigDecimal unpaid) {
        StringBuilder text = new StringBuilder("转课：")
                .append(fromCourse == null ? "原课程" : fromCourse.getName())
                .append(" → ").append(toCourse.getName());
        if (toClassName != null) {
            text.append("（").append(toClassName).append("）");
        }
        text.append("，转出").append(plain(outLessons)).append("课时×￥").append(plain(fromUnitPrice))
                .append("=").append(plain(outAmount))
                .append("；转入").append(plain(inLessons)).append("课时×￥").append(plain(inUnitPrice));
        if (discount.signum() > 0) {
            text.append("，优惠￥").append(plain(discount));
        }
        text.append("，应收￥").append(plain(inAmount));
        if (surplus.signum() > 0) {
            text.append("，应退￥").append(plain(surplus));
            if (refundCash.signum() > 0) {
                text.append("，实退￥").append(plain(refundCash));
            }
            if (balanceAdd.signum() > 0) {
                text.append("，增加余额￥").append(plain(balanceAdd));
            }
        }
        if (shortfall.signum() > 0) {
            text.append("，应收差额￥").append(plain(shortfall))
                    .append("，实收￥").append(plain(received));
            if (unpaid.signum() > 0) {
                text.append("，挂账￥").append(plain(unpaid));
            }
        }
        return text.toString();
    }

    // ------------------------------------------------------------------
    // 升期（按课时价值换算）
    // ------------------------------------------------------------------

    /** 升期预报价：原报读剩余价值 ÷ 目标课程单价 = 自动换算课时，并给出应补差价。 */
    public Map<String, Object> quoteUpgrade(Long studentId, Long fromEnrollmentId, Long toCourseId,
                                            BigDecimal toLessons, BigDecimal toUnitPrice) {
        Student student = requireStudent(studentId);
        Enrollment from = requireEnrollment(fromEnrollmentId, student.getId());
        Course toCourse = requireCourse(toCourseId, "升期课程");
        if (Objects.equals(from.getCourseId(), toCourse.getId())) {
            throw new IllegalArgumentException("升期课程不能与原课程相同");
        }
        BigDecimal remain = nz(from.getRemainLessons()).max(BigDecimal.ZERO);
        BigDecimal fromUnitPrice = resolveEnrollmentUnitPrice(from);
        BigDecimal fromValue = money(fromUnitPrice.multiply(remain));
        Long campusId = resolveCampusId(null, student);
        BigDecimal targetUnitPrice = resolveTargetUnitPrice(toCourse, campusId, toUnitPrice);
        BigDecimal autoLessons = targetUnitPrice.signum() > 0
                ? fromValue.divide(targetUnitPrice, 2, RoundingMode.DOWN)
                : BigDecimal.ZERO;
        BigDecimal targetLessons = toLessons != null && toLessons.signum() > 0
                ? money(toLessons)
                : autoLessons;
        BigDecimal targetValue = money(targetLessons.multiply(targetUnitPrice));
        BigDecimal supplement = money(targetValue.subtract(fromValue)).max(BigDecimal.ZERO);
        BigDecimal surplus = money(fromValue.subtract(targetValue)).max(BigDecimal.ZERO);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fromRemainLessons", remain);
        data.put("fromUnitPrice", fromUnitPrice);
        data.put("fromValue", fromValue);
        data.put("toUnitPrice", targetUnitPrice);
        data.put("autoLessons", autoLessons);
        data.put("targetLessons", targetLessons);
        data.put("targetValue", targetValue);
        data.put("supplement", supplement);
        data.put("surplus", surplus);
        return data;
    }

    /** 升期办理：原报读全部剩余课时按价值转出，目标课时按价值换算，未付差价计入欠费。 */
    @Transactional(rollbackFor = Exception.class)
    public LocalOrderResultVO upgrade(LocalOrderRequests.Upgrade request) {
        Student student = requireStudent(request.studentId());
        Long campusId = resolveCampusId(request.campusId(), student);
        String campusName = requireCampusName(campusId);
        Enrollment from = requireEnrollment(request.fromEnrollmentId(), student.getId());
        Course fromCourse = courseMapper.selectById(from.getCourseId());
        Course toCourse = requireCourse(request.toCourseId(), "升期课程");
        if (Objects.equals(from.getCourseId(), toCourse.getId())) {
            throw new IllegalArgumentException("升期课程不能与原课程相同");
        }
        BigDecimal remain = nz(from.getRemainLessons()).max(BigDecimal.ZERO);
        if (remain.signum() <= 0) {
            throw new IllegalArgumentException("原报读没有可升期的剩余课时");
        }
        BigDecimal fromUnitPrice = resolveEnrollmentUnitPrice(from);
        BigDecimal fromValue = money(fromUnitPrice.multiply(remain));

        PriceTemplate toTemplate = findCoursePriceTemplate(toCourse, campusId);
        BigDecimal toUnitPrice = resolveTargetUnitPrice(toCourse, campusId, request.toUnitPrice());
        BigDecimal toLessons = request.toLessons() != null && request.toLessons().signum() > 0
                ? money(request.toLessons())
                : fromValue.divide(toUnitPrice, 2, RoundingMode.DOWN);
        if (toLessons.signum() <= 0) {
            throw new IllegalArgumentException("换算后的升期课时为 0，请先确认目标课程单价");
        }
        BigDecimal targetValue = money(toLessons.multiply(toUnitPrice));
        BigDecimal supplement = money(targetValue.subtract(fromValue)).max(BigDecimal.ZERO);
        BigDecimal extraPay = money(request.extraPay()).max(BigDecimal.ZERO);
        BigDecimal unpaid = supplement.subtract(extraPay).max(BigDecimal.ZERO);

        // 原报读剩余课时全部转出并按价值结算。
        from.setRemainLessons(BigDecimal.ZERO);
        from.setTotalLessons(nz(from.getTotalLessons()).subtract(remain).max(BigDecimal.ZERO));
        // 转出的课本价值同步计入原报读已结算金额，避免原报读仍保留同一笔剩余学费（重复计入其他课程）
        from.setConsumedAmount(nz(from.getConsumedAmount()).add(fromValue));
        from.setStatus(STATUS_TRANSFERRED);
        from.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(from);
        syncClassStudent(from, null);

        BigDecimal carriedValue = money(fromValue.add(extraPay));
        Enrollment to = findActiveEnrollment(student.getId(), toCourse.getId());
        if (to == null) {
            to = new Enrollment();
            to.setCampusId(campusId);
            to.setStudentId(student.getId());
            to.setCourseId(toCourse.getId());
            to.setTotalLessons(toLessons);
            to.setGiftLessons(BigDecimal.ZERO);
            to.setUsedLessons(BigDecimal.ZERO);
            to.setRemainLessons(toLessons);
            to.setPaidAmount(carriedValue);
            to.setConsumedAmount(BigDecimal.ZERO);
            to.setUnitPrice(toUnitPrice);
            to.setPriceTemplateId(toTemplate == null ? null : toTemplate.getId());
            to.setStatus(STATUS_ACTIVE);
            to.setCreatedAt(LocalDateTime.now());
            to.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.insert(to);
        } else {
            to.setTotalLessons(nz(to.getTotalLessons()).add(toLessons));
            to.setRemainLessons(nz(to.getRemainLessons()).add(toLessons));
            to.setPaidAmount(nz(to.getPaidAmount()).add(carriedValue));
            to.setUnitPrice(toUnitPrice);
            if (toTemplate != null) {
                to.setPriceTemplateId(toTemplate.getId());
            }
            to.setStatus(STATUS_ACTIVE);
            to.setUpdatedAt(LocalDateTime.now());
            enrollmentMapper.updateById(to);
        }
        syncClassStudent(to, null);

        String content = "升期：" + (fromCourse == null ? "原课程" : fromCourse.getName())
                + "（" + plain(remain) + "课时×￥" + plain(fromUnitPrice) + "）→ "
                + toCourse.getName() + "（" + plain(toLessons) + "课时×￥" + plain(toUnitPrice) + "）"
                + (supplement.signum() > 0 ? "，应补￥" + plain(supplement) : "")
                + (extraPay.signum() > 0 ? "，实收￥" + plain(extraPay) : "")
                + (unpaid.signum() > 0 ? "，挂账￥" + plain(unpaid) : "");
        String payMethod = text(request.payMethod());
        String creator = operatorResolver.resolve(request.creator());

        SpOrder order = insertOrder(student, campusId, campusName, 3, false,
                supplement, extraPay, BigDecimal.ZERO, unpaid, payMethod, creator,
                content, text(request.remark()), null);

        writeTxn(from, remain.negate(), "升期转出", order.getId(), content);
        writeTxn(to, toLessons, "升期转入", order.getId(), content);

        if (unpaid.signum() > 0) {
            student.setDebt(money(nz(student.getDebt()).add(unpaid)));
        }
        student.setCourseCount(refreshCourseCount(student.getId()));
        studentMapper.updateById(student);

        return new LocalOrderResultVO(order.getId(), order.getOrderNo(), "升期", content,
                student.getBalance(), to.getRemainLessons());
    }

    /** 原报读课时单价：报读单价优先，其次按实收/总课时折算。未设置时报错。 */
    private BigDecimal resolveEnrollmentUnitPrice(Enrollment enrollment) {
        Object value = resolveLessonPrice(enrollment).get("unitPrice");
        if (value == null) {
            throw new IllegalArgumentException("原报读未设置课时单价，无法按价值换算升期课时");
        }
        return money((BigDecimal) value);
    }

    /** 目标课程单价：优先使用调用方传入值，其次价格模板档位，最后课程挂牌价。 */
    private BigDecimal resolveTargetUnitPrice(Course course, Long campusId, BigDecimal requested) {
        if (requested != null && requested.signum() > 0) {
            return money(requested);
        }
        PriceTemplate template = findCoursePriceTemplate(course, campusId);
        if (template != null) {
            BigDecimal price = templateUnitPrice(template, BigDecimal.ZERO);
            if (price.signum() > 0) {
                return money(price);
            }
        }
        if (nz(course.getListPrice()).signum() > 0) {
            return money(course.getListPrice());
        }
        throw new IllegalArgumentException("升期课程「" + course.getName() + "」未配置价格模板或挂牌价，无法换算课时");
    }

    /** 课程价格模板：默认模板优先，未设置默认模板时取任一已绑定且启用的模板。 */
    private PriceTemplate findCoursePriceTemplate(Course course, Long campusId) {
        if (course.getDefaultPriceTemplateId() != null) {
            return resolvePriceTemplate(course.getDefaultPriceTemplateId(), course, campusId);
        }
        List<PriceTemplateCourse> bindings = priceTemplateCourseMapper.selectList(
                new LambdaQueryWrapper<PriceTemplateCourse>()
                        .eq(PriceTemplateCourse::getCourseId, course.getId())
                        .orderByAsc(PriceTemplateCourse::getTemplateId));
        for (PriceTemplateCourse binding : bindings) {
            try {
                return resolvePriceTemplate(binding.getTemplateId(), course, campusId);
            } catch (IllegalArgumentException ignored) {
                // 模板停用或校区不匹配时尝试下一个
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 记课时（消课）
    // ------------------------------------------------------------------

    /**
     * 记扣课时：允许扣成负数。课时费从报读实收中扣除，超出部分转为欠费。
     * 单价优先取报读单价，其次按报读实收/总课时折算。
     */
    @Transactional(rollbackFor = Exception.class)
    public LessonConsumeResultVO consume(LocalOrderRequests.Consume request) {
        Student student = requireStudent(request.studentId());
        Enrollment enrollment = requireEnrollment(request.enrollmentId(), student.getId());
        BigDecimal lessons = positiveLessons(request.lessons(), "扣减课时");

        BigDecimal oldArrears = enrollmentArrears(enrollment);
        BigDecimal unitPrice;
        String priceSource;
        if (request.unitPrice() != null) {
            unitPrice = positiveMoney(request.unitPrice(), "课时单价");
            priceSource = "手动填写";
        } else {
            Map<String, Object> resolved = resolveLessonPrice(enrollment);
            Object value = resolved.get("unitPrice");
            if (value == null) {
                throw new IllegalArgumentException("未找到课时单价：请先填写报读单价，或手动填写单价");
            }
            unitPrice = money((BigDecimal) value);
            priceSource = String.valueOf(resolved.get("source"));
        }

        // 多报课优惠只覆盖报读课时：超出剩余课时的部分按课程原价计费。
        BigDecimal remainBefore = nz(enrollment.getRemainLessons()).max(BigDecimal.ZERO);
        BigDecimal originalPrice = resolveCourseOriginalUnitPrice(enrollment);
        BigDecimal overflowPrice = originalPrice == null ? unitPrice : originalPrice.max(unitPrice);
        BigDecimal withinLessons = lessons.min(remainBefore);
        BigDecimal overflowLessons = lessons.subtract(withinLessons);
        BigDecimal withinAmount = money(withinLessons.multiply(unitPrice));
        BigDecimal overflowAmount = money(overflowLessons.multiply(overflowPrice));
        BigDecimal amount = money(withinAmount.add(overflowAmount));

        enrollment.setRemainLessons(nz(enrollment.getRemainLessons()).subtract(lessons));
        enrollment.setUsedLessons(nz(enrollment.getUsedLessons()).add(lessons));
        enrollment.setConsumedAmount(money(nz(enrollment.getConsumedAmount()).add(amount)));
        if (enrollment.getUnitPrice() == null) {
            enrollment.setUnitPrice(unitPrice);
        }
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);
        syncClassStudent(enrollment, null);

        // 欠费按「待补金额」增量维护：既覆盖应收未收，也覆盖课耗超支，避免与报名/升期挂账重复计欠。
        BigDecimal newArrears = enrollmentArrears(enrollment);
        BigDecimal debtDelta = newArrears.subtract(oldArrears);
        if (debtDelta.signum() != 0) {
            student.setDebt(money(nz(student.getDebt()).add(debtDelta)));
        }
        student.setCourseCount(refreshCourseCount(student.getId()));
        studentMapper.updateById(student);

        String remark = text(request.remark());
        if (withinLessons.signum() > 0) {
            String note = "记扣" + plain(withinLessons) + "课时，单价￥" + plain(unitPrice)
                    + "（" + priceSource + "），金额￥" + plain(withinAmount);
            if (overflowLessons.signum() <= 0 && remark != null) {
                note = note + "，备注：" + remark;
            }
            insertConsumeTxn(enrollment, withinLessons, unitPrice, withinAmount,
                    remainBefore.subtract(withinLessons), note);
        }
        if (overflowLessons.signum() > 0) {
            String note = "超课时扣减" + plain(overflowLessons) + "课时，超出剩余部分按课程原价￥"
                    + plain(overflowPrice) + "计费，金额￥" + plain(overflowAmount);
            if (remark != null) {
                note = note + "，备注：" + remark;
            }
            insertConsumeTxn(enrollment, overflowLessons, overflowPrice, overflowAmount,
                    enrollment.getRemainLessons(), note);
        }

        return new LessonConsumeResultVO(enrollment.getId(), enrollment.getRemainLessons(),
                money(amount.divide(lessons, 2, RoundingMode.HALF_UP)), amount,
                accountBalance(enrollment), student.getDebt());
    }

    /** 记一笔扣减流水（课耗与超课时原价扣减分别落账，保证单价口径可追溯）。 */
    private void insertConsumeTxn(Enrollment enrollment, BigDecimal lessons, BigDecimal unitPrice,
                                  BigDecimal amount, BigDecimal balanceAfter, String note) {
        LessonTxn txn = new LessonTxn();
        txn.setCampusId(enrollment.getCampusId());
        txn.setEnrollmentId(enrollment.getId());
        txn.setStudentId(enrollment.getStudentId());
        txn.setType("扣减");
        txn.setLessons(lessons.negate());
        txn.setUnitPrice(unitPrice);
        txn.setAmount(amount);
        txn.setBalanceAfter(balanceAfter);
        txn.setBizType("consume");
        txn.setNote(note);
        txn.setCreatedAt(LocalDateTime.now());
        lessonTxnMapper.insert(txn);
    }

    // ------------------------------------------------------------------
    // 通用落库
    // ------------------------------------------------------------------

    /** 账户余额 = 报读实收 - 已课耗金额，可为负数。 */
    private static BigDecimal accountBalance(Enrollment enrollment) {
        return money(nz(enrollment.getPaidAmount()).subtract(nz(enrollment.getConsumedAmount())));
    }

    /** 超支金额：已课耗金额超过实收的部分。 */
    private static BigDecimal overdraft(Enrollment enrollment) {
        BigDecimal amount = accountBalance(enrollment);
        return amount.signum() < 0 ? amount.negate() : BigDecimal.ZERO;
    }

    /**
     * 报读待补金额：优惠后应收（课时 × 单价 - 优惠）与已课耗金额取较大者，减去已实收。
     * 优惠不计入欠费，欠费只来自应收未收或课耗超支。
     */
    private static BigDecimal enrollmentArrears(Enrollment enrollment) {
        BigDecimal gross = money(nz(enrollment.getTotalLessons()).multiply(nz(enrollment.getUnitPrice())));
        BigDecimal payable = gross.subtract(nz(enrollment.getDiscountAmount())).max(BigDecimal.ZERO);
        BigDecimal should = payable.max(money(nz(enrollment.getConsumedAmount())));
        if (should.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return money(should.subtract(nz(enrollment.getPaidAmount()))).max(BigDecimal.ZERO);
    }

    /** 校验所选班级：必须存在且属于报读课程，返回班级名称。 */
    private String resolveClassName(Long classId, Course course) {
        if (classId == null) {
            return null;
        }
        ClassGroup clazz = classGroupMapper.selectById(classId);
        if (clazz == null) {
            throw new IllegalArgumentException("所选班级不存在");
        }
        if (clazz.getCourseId() != null && !Objects.equals(clazz.getCourseId(), course.getId())) {
            throw new IllegalArgumentException("所选班级不属于该课程");
        }
        return clazz.getName();
    }

    /**
     * 写入本地订单。本地订单号从 900000001 起，sp_id 为空（区别于校宝订单）。
     * synchronized 保证单实例下取号与插入不并发冲突。
     */
    private synchronized SpOrder insertOrder(Student student, Long campusId, String campusName,
                                             int typeCode, boolean isReturn, BigDecimal should,
                                             BigDecimal received, BigDecimal remain, BigDecimal arrearage,
                                             String payMethod, String creator, String content,
                                             String comment, String tags) {
        LocalDateTime now = LocalDateTime.now();
        SpOrder order = new SpOrder();
        order.setOrderNo(String.valueOf(spOrderMapper.selectNextLocalOrderNo()));
        order.setCampusId(campusId);
        order.setCampusName(campusName);
        order.setStudentId(student.getId());
        order.setStuName(student.getName());
        order.setStuTel(student.getPhone());
        order.setStuNamePinyin(student.getName());
        order.setTypeCode(typeCode);
        order.setContent(content);
        order.setShouldPay(should);
        order.setRealPay(received);
        order.setRemain(remain);
        order.setArrearage(arrearage);
        order.setCredit(BigDecimal.ZERO);
        order.setSourceType(1);
        order.setCreator(creator);
        order.setIsReturn(isReturn);
        order.setStatusCode(arrearage.signum() > 0 ? 1 : 2);
        order.setContractStatus(0);
        order.setPayMethod(payMethod);
        order.setComment(comment);
        order.setTags(text(tags));
        order.setHasConfirmedDetail(received.signum() > 0);
        order.setHasTextbook(false);
        order.setIsOnlinePayment(false);
        order.setHasMultiLessonEnroll(false);
        order.setDealDate(now);
        order.setCreatedTime(now);
        spOrderMapper.insert(order);
        return order;
    }

    private void writeTxn(Enrollment enrollment, BigDecimal lessons, String type, Long orderId, String note) {
        LessonTxn txn = new LessonTxn();
        txn.setCampusId(enrollment.getCampusId());
        txn.setEnrollmentId(enrollment.getId());
        txn.setStudentId(enrollment.getStudentId());
        txn.setType(type);
        txn.setLessons(lessons);
        txn.setBalanceAfter(enrollment.getRemainLessons());
        txn.setBizType("本地办理");
        txn.setBizId(orderId);
        txn.setNote(note);
        lessonTxnMapper.insert(txn);
    }

    /**
     * 记课时单价：报读单价优先，其次按报读实收/总课时折算。
     * originalUnitPrice 为课程原价，仅用于超出报读课时后的扣费。
     */
    public Map<String, Object> resolveLessonPrice(Enrollment enrollment) {
        Map<String, Object> data = new LinkedHashMap<>();
        Object unitPrice;
        if (nz(enrollment.getUnitPrice()).signum() > 0) {
            unitPrice = money(enrollment.getUnitPrice());
            data.put("unitPrice", unitPrice);
            data.put("source", "报读单价");
        } else if (nz(enrollment.getTotalLessons()).signum() > 0 && nz(enrollment.getPaidAmount()).signum() > 0) {
            unitPrice = nz(enrollment.getPaidAmount())
                    .divide(nz(enrollment.getTotalLessons()), 2, RoundingMode.HALF_UP);
            data.put("unitPrice", unitPrice);
            data.put("source", "报读实收折算");
        } else {
            unitPrice = null;
            data.put("unitPrice", null);
            data.put("source", "未设置");
        }
        BigDecimal original = resolveCourseOriginalUnitPrice(enrollment);
        if (original == null) {
            data.put("originalUnitPrice", unitPrice);
        } else {
            data.put("originalUnitPrice", unitPrice == null
                    ? original : original.max((BigDecimal) unitPrice));
        }
        return data;
    }

    /**
     * 课程原价（标准课时单价）：取课程定价档位中最高的课时单价，即优惠最少的一档。
     * 多报课产生的低价只属于优惠价，不能作为课程原价。
     */
    private BigDecimal resolveCourseOriginalUnitPrice(Enrollment enrollment) {
        Course course = enrollment.getCourseId() == null
                ? null : courseMapper.selectById(enrollment.getCourseId());
        if (course == null) {
            return null;
        }
        LambdaQueryWrapper<CoursePrice> wrapper = new LambdaQueryWrapper<CoursePrice>()
                .eq(CoursePrice::getCourseId, course.getId());
        if (enrollment.getCampusId() != null) {
            wrapper.and(w -> w.isNull(CoursePrice::getCampusId)
                    .or().eq(CoursePrice::getCampusId, enrollment.getCampusId()));
        }
        BigDecimal best = null;
        for (CoursePrice price : coursePriceMapper.selectList(wrapper)) {
            if (nz(price.getLessons()).signum() <= 0 || nz(price.getTotalPrice()).signum() <= 0) {
                continue;
            }
            BigDecimal unit = price.getTotalPrice().divide(price.getLessons(), 2, RoundingMode.HALF_UP);
            if (best == null || unit.compareTo(best) > 0) {
                best = unit;
            }
        }
        if (best == null) {
            BigDecimal listPrice = nz(course.getListPrice());
            BigDecimal totalLessons = nz(course.getTotalLessons());
            if (listPrice.signum() > 0) {
                best = totalLessons.signum() > 0
                        ? listPrice.divide(totalLessons, 2, RoundingMode.HALF_UP) : listPrice;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private Student requireStudent(Long studentId) {
        Student student = studentId == null ? null : studentMapper.selectById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("请选择学员");
        }
        return student;
    }

    private Course requireCourse(Long courseId, String label) {
        Course course = courseId == null ? null : courseMapper.selectById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("请选择" + label);
        }
        return course;
    }

    private Enrollment requireEnrollment(Long enrollmentId, Long studentId) {
        Enrollment enrollment = enrollmentId == null ? null : enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || !Objects.equals(enrollment.getStudentId(), studentId)) {
            throw new IllegalArgumentException("请选择该学员的报读记录");
        }
        if (!STATUS_ACTIVE.equals(enrollment.getStatus())) {
            throw new IllegalArgumentException("该报读记录状态为「" + enrollment.getStatus() + "」，无法办理");
        }
        return enrollment;
    }

    private int refreshCourseCount(Long studentId) {
        return Math.toIntExact(enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getStatus, STATUS_ACTIVE)));
    }

    private Enrollment findActiveEnrollment(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            return null;
        }
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getStatus, STATUS_ACTIVE)
                .orderByDesc(Enrollment::getId)
                .last("LIMIT 1"));
    }

    private Long resolveCampusId(Long requested, Student student) {
        Long campusId = requested != null ? requested : student.getCampusId();
        if (campusId == null) {
            throw new IllegalArgumentException("请选择经办校区");
        }
        return campusId;
    }

    private String requireCampusName(Long campusId) {
        Campus campus = campusId == null ? null : campusMapper.selectById(campusId);
        if (campus == null) {
            throw new IllegalArgumentException("经办校区不存在");
        }
        return campus.getName();
    }

    private String buildEnrollContent(String typeName, String courseName, String className,
                                      BigDecimal lessons, BigDecimal gift, BigDecimal should,
                                      BigDecimal received, BigDecimal arrearage,
                                      BigDecimal materialAmount, BigDecimal discount,
                                      List<LocalOrderRequests.EnrollMaterial> materials) {
        StringBuilder text = new StringBuilder(typeName).append("：").append(courseName)
                .append("，").append(plain(lessons)).append("课时");
        if (className != null) {
            text.append("，班级：").append(className);
        }
        if (gift.signum() > 0) {
            text.append("，赠送").append(plain(gift)).append("课时");
        }
        if (materialAmount.signum() > 0 && materials != null) {
            text.append("，教材杂费￥").append(plain(materialAmount)).append("（")
                    .append(materials.stream()
                            .filter(item -> item.name() != null && !item.name().isBlank())
                            .map(item -> item.name() + "×" + plain(item.quantity()))
                            .collect(Collectors.joining("、")))
                    .append("）");
        }
        if (discount.signum() > 0) {
            text.append("，优惠￥").append(plain(discount));
        }
        text.append("，应收￥").append(plain(should));
        text.append("，实收￥").append(plain(received));
        if (arrearage.signum() > 0) {
            text.append("，欠费￥").append(plain(arrearage));
        }
        return text.toString();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim().length() > 10 ? value.trim().substring(0, 10) : value.trim());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal positiveMoney(BigDecimal value, String label) {
        BigDecimal amount = money(value);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        return amount;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String label) {
        BigDecimal amount = money(value);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
        return amount;
    }

    private static BigDecimal positiveLessons(BigDecimal value, String label) {
        BigDecimal lessons = scaleLessons(value);
        if (lessons.signum() <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        return lessons;
    }

    private static BigDecimal nonNegativeLessons(BigDecimal value, String label) {
        BigDecimal lessons = scaleLessons(value);
        if (lessons.signum() < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
        return lessons;
    }

    private static BigDecimal scaleLessons(BigDecimal value) {
        return nz(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String textOrDefault(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }
}
