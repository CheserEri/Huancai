package cn.huancai.erp.module.order.vo;

import java.math.BigDecimal;

/** 本地办理结果，用于前端提示与刷新列表。 */
public record LocalOrderResultVO(
        Long orderId,
        String orderNo,
        String typeName,
        String summary,
        BigDecimal balance,
        BigDecimal remainLessons
) {
}
