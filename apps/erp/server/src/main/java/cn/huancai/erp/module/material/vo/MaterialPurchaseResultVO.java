package cn.huancai.erp.module.material.vo;

import java.math.BigDecimal;

/** 多教材购买的结算结果。 */
public record MaterialPurchaseResultVO(
        String extOrderNo,
        int itemCount,
        int quantity,
        BigDecimal totalAmount,
        BigDecimal balanceAmount,
        BigDecimal cashPay,
        String payMethod) {
}
