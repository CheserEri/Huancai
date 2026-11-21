package cn.huancai.erp.module.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单修改请求：字段为 null 表示不修改。
 *
 * <p>基础信息需要 /order/edit，金额需要 /order/edit-amount，
 * 课时需要 /order/edit-lessons；修改原因必填 5~100 字。</p>
 */
public record UpdateOrderRequest(
        String content,
        String payMethod,
        LocalDateTime dealDate,
        BigDecimal shouldPay,
        BigDecimal realPay,
        List<ItemLessons> items,
        String reason
) {

    /** 订单明细课时调整。 */
    public record ItemLessons(Long id, BigDecimal lessons) {
    }
}
