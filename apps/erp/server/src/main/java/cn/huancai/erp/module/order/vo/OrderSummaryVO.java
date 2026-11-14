package cn.huancai.erp.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderSummaryVO {

    private Integer count;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal discount;
    private BigDecimal debt;
}
