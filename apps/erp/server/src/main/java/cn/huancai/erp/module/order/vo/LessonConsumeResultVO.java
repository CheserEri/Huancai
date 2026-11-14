package cn.huancai.erp.module.order.vo;

import java.math.BigDecimal;

/** 记课时（消课）结果。accountBalance = 实收 - 已课耗金额，可为负数。 */
public record LessonConsumeResultVO(
        Long enrollmentId,
        BigDecimal remainLessons,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal accountBalance,
        BigDecimal debt
) {
}
