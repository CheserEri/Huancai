package cn.huancai.erp.module.datafee.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.datafee.dto.DataFeeRequests;
import cn.huancai.erp.module.datafee.entity.DataFeePayment;
import cn.huancai.erp.module.datafee.entity.DataFeeTemplate;
import cn.huancai.erp.module.datafee.mapper.DataFeePaymentMapper;
import cn.huancai.erp.module.datafee.mapper.DataFeeTemplateMapper;
import cn.huancai.erp.module.datafee.vo.DataFeeOptionsVO;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 资料费缴纳：按课程（报读）收取 A4 打印资料费，与教材不同，不涉及库存。
 * 模板维护价格与缴纳周期；每次缴纳生成订单，并在学员课程旁展示上次缴纳时间。
 */
@RestController
@RequestMapping("/api/data-fees")
public class DataFeeController {

    private static final List<String> PAY_METHODS =
            List.of("现金", "微信", "支付宝", "银行转账", "POS刷卡", "账户余额");
    private static final Set<String> PAYABLE_STATUSES = Set.of("生效", "停课");
    private static final int TYPE_DATA_FEE = 11;

    private final DataFeeTemplateMapper templateMapper;
    private final DataFeePaymentMapper paymentMapper;
    private final StudentMapper studentMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final CourseMapper courseMapper;
    private final SpOrderMapper spOrderMapper;
    private final OperatorResolver operatorResolver;

    public DataFeeController(DataFeeTemplateMapper templateMapper,
                             DataFeePaymentMapper paymentMapper,
                             StudentMapper studentMapper,
                             EnrollmentMapper enrollmentMapper,
                             CourseMapper courseMapper,
                             SpOrderMapper spOrderMapper,
                             OperatorResolver operatorResolver) {
        this.templateMapper = templateMapper;
        this.paymentMapper = paymentMapper;
        this.studentMapper = studentMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.courseMapper = courseMapper;
        this.spOrderMapper = spOrderMapper;
        this.operatorResolver = operatorResolver;
    }

    // ---------------- 模板维护 ----------------

    @GetMapping("/templates")
    public ApiResult<List<DataFeeTemplate>> templates() {
        return ApiResult.ok(templateMapper.selectList(new LambdaQueryWrapper<DataFeeTemplate>()
                .orderByAsc(DataFeeTemplate::getId)));
    }

    @PostMapping("/templates")
    public ApiResult<DataFeeTemplate> createTemplate(@RequestBody DataFeeRequests.TemplateForm form) {
        return run(() -> {
            DataFeeTemplate template = new DataFeeTemplate();
            applyTemplate(template, form);
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            templateMapper.insert(template);
            return template;
        });
    }

    @PutMapping("/templates/{id}")
    public ApiResult<DataFeeTemplate> updateTemplate(@PathVariable Long id,
                                                     @RequestBody DataFeeRequests.TemplateForm form) {
        return run(() -> {
            DataFeeTemplate template = templateMapper.selectById(id);
            if (template == null) {
                throw new IllegalArgumentException("资料费模板不存在");
            }
            applyTemplate(template, form);
            template.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(template);
            return template;
        });
    }

    @DeleteMapping("/templates/{id}")
    public ApiResult<Void> deleteTemplate(@PathVariable Long id) {
        return run(() -> {
            long used = paymentMapper.selectCount(new LambdaQueryWrapper<DataFeePayment>()
                    .eq(DataFeePayment::getTemplateId, id));
            if (used > 0) {
                throw new IllegalArgumentException("该模板已有缴费记录，不能删除，可改为停用");
            }
            templateMapper.deleteById(id);
            return null;
        });
    }

    private void applyTemplate(DataFeeTemplate template, DataFeeRequests.TemplateForm form) {
        String name = text(form.name());
        if (name == null) {
            throw new IllegalArgumentException("请填写资料费模板名称");
        }
        BigDecimal price = form.price();
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("资料费价格必须大于 0");
        }
        Integer cycle = form.cycleMonths();
        if (cycle == null || cycle < 1 || cycle > 36) {
            throw new IllegalArgumentException("缴纳周期需为 1-36 个月");
        }
        template.setName(name);
        template.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
        template.setCycleMonths(cycle);
        template.setStatus(form.status() != null && "停用".equals(form.status().trim()) ? "停用" : "启用");
        template.setRemark(text(form.remark()));
    }

    // ---------------- 缴纳表单数据 ----------------

    @GetMapping("/options")
    public ApiResult<DataFeeOptionsVO> options(@RequestParam(required = false) Long studentId) {
        DataFeeOptionsVO data = new DataFeeOptionsVO();
        data.setPayMethods(PAY_METHODS);
        data.setTemplates(templateMapper.selectList(new LambdaQueryWrapper<DataFeeTemplate>()
                .eq(DataFeeTemplate::getStatus, "启用")
                .orderByAsc(DataFeeTemplate::getId)));
        if (studentId == null) {
            data.setEnrollments(List.of());
            return ApiResult.ok(data);
        }
        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .in(Enrollment::getStatus, PAYABLE_STATUSES)
                .orderByDesc(Enrollment::getId));
        Map<Long, String> courseNames = enrollments.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(enrollments.stream().map(Enrollment::getCourseId).toList())
                .stream().collect(Collectors.toMap(Course::getId, Course::getName));
        Map<Long, LocalDateTime> lastPaid = paymentMapper.selectList(new LambdaQueryWrapper<DataFeePayment>()
                        .eq(DataFeePayment::getStudentId, studentId))
                .stream()
                .filter(item -> item.getEnrollmentId() != null && item.getPaidAt() != null)
                .collect(Collectors.toMap(DataFeePayment::getEnrollmentId, DataFeePayment::getPaidAt,
                        BinaryOperator.maxBy((a, b) -> a.isAfter(b) ? 1 : a.isEqual(b) ? 0 : -1)));
        data.setEnrollments(enrollments.stream().map(enrollment -> {
            DataFeeOptionsVO.EnrollmentOption option = new DataFeeOptionsVO.EnrollmentOption();
            option.setId(enrollment.getId());
            option.setCourseId(enrollment.getCourseId());
            option.setCourseName(courseNames.getOrDefault(enrollment.getCourseId(), "—"));
            option.setLastPaidAt(lastPaid.get(enrollment.getId()));
            return option;
        }).toList());
        return ApiResult.ok(data);
    }

    // ---------------- 缴纳 ----------------

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/payments")
    public ApiResult<Map<String, Object>> pay(@RequestBody DataFeeRequests.PaymentForm form) {
        return run(() -> {
            if (form.studentId() == null) {
                throw new IllegalArgumentException("请选择学员");
            }
            Student student = studentMapper.selectById(form.studentId());
            if (student == null) {
                throw new IllegalArgumentException("学员不存在");
            }
            if (form.enrollmentId() == null) {
                throw new IllegalArgumentException("请选择缴纳资料费的课程");
            }
            Enrollment enrollment = enrollmentMapper.selectById(form.enrollmentId());
            if (enrollment == null || !enrollment.getStudentId().equals(form.studentId())) {
                throw new IllegalArgumentException("该学员没有对应的报读课程");
            }
            if (!PAYABLE_STATUSES.contains(enrollment.getStatus())) {
                throw new IllegalArgumentException("该课程已" + enrollment.getStatus() + "，不能缴纳资料费");
            }
            if (form.templateId() == null) {
                throw new IllegalArgumentException("请选择资料费模板");
            }
            DataFeeTemplate template = templateMapper.selectById(form.templateId());
            if (template == null || "停用".equals(template.getStatus())) {
                throw new IllegalArgumentException("资料费模板不存在或已停用");
            }
            BigDecimal price = template.getPrice() == null ? BigDecimal.ZERO : template.getPrice();
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("资料费模板价格未设置");
            }
            Course course = courseMapper.selectById(enrollment.getCourseId());
            String courseName = course == null ? "—" : course.getName();
            int cycleMonths = template.getCycleMonths() == null ? 1 : template.getCycleMonths();

            DataFeePayment last = paymentMapper.selectOne(new LambdaQueryWrapper<DataFeePayment>()
                    .eq(DataFeePayment::getEnrollmentId, enrollment.getId())
                    .orderByDesc(DataFeePayment::getPaidAt)
                    .last("LIMIT 1"));
            LocalDate today = LocalDate.now();
            LocalDate periodStart = today;
            if (last != null && last.getPeriodEnd() != null && last.getPeriodEnd().isAfter(today)) {
                periodStart = last.getPeriodEnd().plusDays(1);
            }
            LocalDate periodEnd = periodStart.plusMonths(cycleMonths).minusDays(1);

            String handler = operatorResolver.resolve(form.handler());
            String payMethod = text(form.payMethod());
            String remark = text(form.remark());
            String content = "资料费缴纳：" + courseName + "（" + template.getName() + "）";
            if (remark != null) {
                content = content + "，" + remark;
            }

            LocalDateTime now = LocalDateTime.now();
            SpOrder order = new SpOrder();
            order.setOrderNo(String.valueOf(spOrderMapper.selectNextLocalOrderNo()));
            order.setCampusId(student.getCampusId());
            order.setCampusName(student.getCampusText());
            order.setStudentId(student.getId());
            order.setStuName(student.getName());
            order.setStuTel(student.getPhone());
            order.setStuNamePinyin(student.getName());
            order.setTypeCode(TYPE_DATA_FEE);
            order.setContent(content);
            order.setShouldPay(price);
            order.setRealPay(price);
            order.setRemain(BigDecimal.ZERO);
            order.setArrearage(BigDecimal.ZERO);
            order.setCredit(BigDecimal.ZERO);
            order.setSourceType(1);
            order.setCreator(handler);
            order.setIsReturn(false);
            order.setStatusCode(2);
            order.setContractStatus(0);
            order.setComment("资料费周期：" + periodStart + " 至 " + periodEnd);
            order.setHasConfirmedDetail(true);
            order.setHasTextbook(false);
            order.setIsOnlinePayment(false);
            order.setHasMultiLessonEnroll(false);
            order.setDealDate(now);
            order.setCreatedTime(now);
            spOrderMapper.insert(order);

            DataFeePayment payment = new DataFeePayment();
            payment.setCampusId(student.getCampusId());
            payment.setCampusName(student.getCampusText());
            payment.setStudentId(student.getId());
            payment.setStudentName(student.getName());
            payment.setEnrollmentId(enrollment.getId());
            payment.setCourseId(enrollment.getCourseId());
            payment.setCourseName(courseName);
            payment.setTemplateId(template.getId());
            payment.setTemplateName(template.getName());
            payment.setPrice(price);
            payment.setCycleMonths(cycleMonths);
            payment.setPeriodStart(periodStart);
            payment.setPeriodEnd(periodEnd);
            payment.setPaidAt(now);
            payment.setPayMethod(payMethod == null ? "现金" : payMethod);
            payment.setHandler(handler);
            payment.setOrderId(order.getId());
            payment.setOrderNo(order.getOrderNo());
            payment.setRemark(remark);
            payment.setCreatedAt(now);
            paymentMapper.insert(payment);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderNo", order.getOrderNo());
            result.put("orderId", order.getId());
            result.put("courseName", courseName);
            result.put("templateName", template.getName());
            result.put("price", price);
            result.put("cycleMonths", cycleMonths);
            result.put("periodStart", periodStart.toString());
            result.put("periodEnd", periodEnd.toString());
            result.put("paidAt", now.toString());
            result.put("lastPaidAt", now.toString());
            return result;
        });
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> ApiResult<T> run(Supplier<T> action) {
        try {
            return ApiResult.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
