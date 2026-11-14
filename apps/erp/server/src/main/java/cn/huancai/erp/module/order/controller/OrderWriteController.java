package cn.huancai.erp.module.order.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.order.dto.OrderForm;
import cn.huancai.erp.module.order.dto.PaymentForm;
import cn.huancai.erp.module.order.entity.Payment;
import cn.huancai.erp.module.order.entity.SalesOrder;
import cn.huancai.erp.module.order.mapper.OrderMapper;
import cn.huancai.erp.module.order.mapper.PaymentMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderWriteController {

    private static final List<String> PAY_METHODS = List.of("微信", "支付宝", "现金", "银行卡", "POS", "转账");

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;

    public OrderWriteController(OrderMapper orderMapper,
                                PaymentMapper paymentMapper,
                                StudentMapper studentMapper,
                                CampusMapper campusMapper) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.studentMapper = studentMapper;
        this.campusMapper = campusMapper;
    }

    @GetMapping("/options")
    public ApiResult<Map<String, Object>> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campuses", campusMapper.selectList(new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> Map.of("id", c.getId(), "name", c.getName())).toList());
        data.put("types", List.of("报名", "续费", "补费", "退费", "转课", "充值"));
        data.put("payMethods", PAY_METHODS);
        return ApiResult.ok(data);
    }

    @PostMapping
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody OrderForm form) {
        if (form.getStudentId() == null) {
            return ApiResult.fail("请选择学员");
        }
        Student student = studentMapper.selectById(form.getStudentId());
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        Long campusId = form.getCampusId() != null ? form.getCampusId() : student.getCampusId();
        if (campusId == null || campusMapper.selectById(campusId) == null) {
            return ApiResult.fail("经办校区不存在");
        }
        String type = form.getType() == null || form.getType().isBlank() ? "报名" : form.getType().trim();
        BigDecimal receivable = nz(form.getReceivable());
        if (receivable.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResult.fail("应收金额必须大于 0");
        }
        BigDecimal discount = nz(form.getDiscount());
        BigDecimal received = nz(form.getReceived());
        if (received.compareTo(receivable) > 0) {
            return ApiResult.fail("实收金额不能大于应收金额");
        }
        if (discount.compareTo(receivable.subtract(received)) > 0) {
            return ApiResult.fail("优惠金额不能超过应收与实收的差额");
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNo(nextOrderNo());
        order.setCampusId(campusId);
        order.setStudentId(student.getId());
        order.setType(type);
        order.setContent(trimToNull(form.getContent()));
        order.setReceivable(receivable);
        order.setDiscount(discount);
        order.setReceived(received);
        order.setDebt(debtOf(receivable, discount, received));
        order.setPayMethod(pickMethod(form.getPayMethod(), received));
        order.setStatus(order.getDebt().compareTo(BigDecimal.ZERO) > 0 ? "部分支付" : "已完成");
        order.setPaidAt(received.compareTo(BigDecimal.ZERO) > 0 ? LocalDateTime.now() : null);
        order.setRemark(trimToNull(form.getRemark()));
        orderMapper.insert(order);

        if (received.compareTo(BigDecimal.ZERO) > 0) {
            insertPayment(order, received, order.getPayMethod(), order.getRemark());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("debt", order.getDebt());
        return ApiResult.ok(data);
    }

    /** 收款：累加实收、减少欠费，欠费清零后订单完成。 */
    @PostMapping("/{id}/pay")
    @Transactional
    public ApiResult<Map<String, Object>> pay(@PathVariable Long id, @RequestBody PaymentForm form) {
        SalesOrder order = orderMapper.selectById(id);
        if (order == null) {
            return ApiResult.fail("订单不存在");
        }
        if ("已作废".equals(order.getStatus())) {
            return ApiResult.fail("订单已作废，不能收款");
        }
        BigDecimal amount = nz(form.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResult.fail("收款金额必须大于 0");
        }
        BigDecimal debt = nz(order.getDebt());
        if (debt.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(debt) > 0) {
            return ApiResult.fail("收款金额不能大于当前欠费 " + debt.toPlainString());
        }
        String method = pickMethod(form.getMethod(), amount);
        order.setReceived(nz(order.getReceived()).add(amount));
        order.setDebt(debt.subtract(amount));
        order.setPayMethod(method);
        order.setPaidAt(LocalDateTime.now());
        order.setStatus(order.getDebt().compareTo(BigDecimal.ZERO) <= 0 ? "已完成" : "部分支付");
        orderMapper.updateById(order);
        insertPayment(order, amount, method, trimToNull(form.getRemark()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("received", order.getReceived());
        data.put("debt", order.getDebt());
        data.put("status", order.getStatus());
        return ApiResult.ok(data);
    }

    /** 退费：生成一条退费订单（金额为负）并记录负数收款流水，原订单不改动。 */
    @PostMapping("/{id}/refund")
    @Transactional
    public ApiResult<Map<String, Object>> refund(@PathVariable Long id, @RequestBody PaymentForm form) {
        SalesOrder origin = orderMapper.selectById(id);
        if (origin == null) {
            return ApiResult.fail("订单不存在");
        }
        if ("退费".equals(origin.getType())) {
            return ApiResult.fail("退费订单不能再次退费");
        }
        BigDecimal amount = nz(form.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResult.fail("退费金额必须大于 0");
        }
        BigDecimal received = nz(origin.getReceived());
        if (amount.compareTo(received) > 0) {
            return ApiResult.fail("退费金额不能大于该订单实收 " + received.toPlainString());
        }
        String method = pickMethod(form.getMethod(), amount);

        SalesOrder refund = new SalesOrder();
        refund.setOrderNo(nextOrderNo());
        refund.setCampusId(origin.getCampusId());
        refund.setStudentId(origin.getStudentId());
        refund.setType("退费");
        refund.setContent("退费（原订单 " + origin.getOrderNo() + "）");
        refund.setReceivable(amount.negate());
        refund.setDiscount(BigDecimal.ZERO);
        refund.setReceived(amount.negate());
        refund.setDebt(BigDecimal.ZERO);
        refund.setPayMethod(method);
        refund.setStatus("已退款");
        refund.setPaidAt(LocalDateTime.now());
        refund.setRemark(trimToNull(form.getRemark()));
        orderMapper.insert(refund);

        insertPayment(refund, amount.negate(), method, trimToNull(form.getRemark()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", refund.getId());
        data.put("orderNo", refund.getOrderNo());
        return ApiResult.ok(data);
    }

    private void insertPayment(SalesOrder order, BigDecimal amount, String method, String remark) {
        Payment payment = new Payment();
        payment.setCampusId(order.getCampusId());
        payment.setOrderId(order.getId());
        payment.setStudentId(order.getStudentId());
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRemark(remark);
        paymentMapper.insert(payment);
    }

    /** 与校宝一致的纯数字订单号：接续已有纯数字最大值；历史订单为 QB-E-xxx 时从 33661 起。 */
    private String nextOrderNo() {
        Long max = orderMapper.selectMaxNumericOrderNo();
        long next = (max == null || max < 33660L ? 33660L : max) + 1;
        return String.valueOf(next);
    }

    private BigDecimal debtOf(BigDecimal receivable, BigDecimal discount, BigDecimal received) {
        BigDecimal debt = receivable.subtract(discount).subtract(received);
        return debt.compareTo(BigDecimal.ZERO) > 0 ? debt : BigDecimal.ZERO;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String pickMethod(String requested, BigDecimal amount) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return amount.compareTo(BigDecimal.ZERO) == 0 ? null : "微信";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
