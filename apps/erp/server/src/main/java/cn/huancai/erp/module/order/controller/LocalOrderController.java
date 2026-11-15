package cn.huancai.erp.module.order.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.order.dto.LocalOrderRequests;
import cn.huancai.erp.module.order.service.LocalOrderService;
import cn.huancai.erp.module.order.vo.LessonConsumeResultVO;
import cn.huancai.erp.module.order.vo.LocalOrderResultVO;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 本地闭环办理接口：报名/续费、余额充值、退费、转课。
 * 所有写操作只落本地库，不调用原系统任何接口。
 */
@RestController
@RequestMapping("/api/local-orders")
public class LocalOrderController {

    private final LocalOrderService localOrderService;
    private final EnrollmentMapper enrollmentMapper;

    public LocalOrderController(LocalOrderService localOrderService,
                                EnrollmentMapper enrollmentMapper) {
        this.localOrderService = localOrderService;
        this.enrollmentMapper = enrollmentMapper;
    }

    @GetMapping("/options")
    public ApiResult<Map<String, Object>> options(@RequestParam(required = false) Long studentId) {
        return ApiResult.ok(localOrderService.options(studentId));
    }

    /** 订单明细：查看每笔缴费使用的价格模板/活动模板。 */
    @GetMapping("/items")
    public ApiResult<List<Map<String, Object>>> items(@RequestParam String orderIds) {
        List<Long> ids = Arrays.stream(orderIds.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ApiResult.ok(localOrderService.orderItems(ids));
    }

    @PostMapping("/enroll")
    public ApiResult<LocalOrderResultVO> enroll(@RequestBody LocalOrderRequests.Enroll request) {
        return run(() -> localOrderService.enroll(request));
    }

    @PostMapping("/recharge")
    public ApiResult<LocalOrderResultVO> recharge(@RequestBody LocalOrderRequests.Recharge request) {
        return run(() -> localOrderService.recharge(request));
    }

    @PostMapping("/subsidy")
    public ApiResult<LocalOrderResultVO> subsidy(@RequestBody LocalOrderRequests.Subsidy request) {
        return run(() -> localOrderService.subsidy(request));
    }

    @PostMapping("/refund")
    public ApiResult<LocalOrderResultVO> refund(@RequestBody LocalOrderRequests.Refund request) {
        return run(() -> localOrderService.refund(request));
    }

    @PostMapping("/batch-refund")
    public ApiResult<LocalOrderResultVO> batchRefund(@RequestBody LocalOrderRequests.BatchRefund request) {
        return run(() -> localOrderService.refundBatch(request));
    }

    @PostMapping("/balance-refund")
    public ApiResult<LocalOrderResultVO> balanceRefund(@RequestBody LocalOrderRequests.BalanceRefund request) {
        return run(() -> localOrderService.refundBalance(request));
    }

    @PostMapping("/transfer")
    public ApiResult<LocalOrderResultVO> transfer(@RequestBody LocalOrderRequests.Transfer request) {
        return run(() -> localOrderService.transfer(request));
    }

    /** 升期预报价：按原剩余价值自动换算目标课时，并计算应补差价。 */
    @GetMapping("/upgrade-quote")
    public ApiResult<Map<String, Object>> upgradeQuote(@RequestParam Long studentId,
                                                       @RequestParam Long fromEnrollmentId,
                                                       @RequestParam Long toCourseId,
                                                       @RequestParam(required = false) BigDecimal toLessons,
                                                       @RequestParam(required = false) BigDecimal toUnitPrice) {
        return run(() -> localOrderService.quoteUpgrade(studentId, fromEnrollmentId, toCourseId,
                toLessons, toUnitPrice));
    }

    @PostMapping("/upgrade")
    public ApiResult<LocalOrderResultVO> upgrade(@RequestBody LocalOrderRequests.Upgrade request) {
        return run(() -> localOrderService.upgrade(request));
    }

    @PostMapping("/consume-lessons")
    public ApiResult<LessonConsumeResultVO> consumeLessons(@RequestBody LocalOrderRequests.Consume request) {
        return run(() -> localOrderService.consume(request));
    }

    /** 记课时弹窗预填单价：报读单价 > 报读实收/总课时折算。 */
    @GetMapping("/lesson-price")
    public ApiResult<Map<String, Object>> lessonPrice(@RequestParam Long studentId,
                                                      @RequestParam Long enrollmentId) {
        return run(() -> {
            Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
            if (enrollment == null || !enrollment.getStudentId().equals(studentId)) {
                throw new IllegalArgumentException("报读记录不存在");
            }
            return localOrderService.resolveLessonPrice(enrollment);
        });
    }

    private <T> ApiResult<T> run(Supplier<T> action) {
        try {
            return ApiResult.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
