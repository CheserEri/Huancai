package cn.huancai.erp.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 订单列表底部汇总（与原系统列表口径一致）。 */
@Data
public class SpOrderSummaryVO {

    private Long count;
    private BigDecimal shouldPay;
    private BigDecimal realPay;
    private BigDecimal remain;
    private BigDecimal arrearage;
    private BigDecimal credit;
}
