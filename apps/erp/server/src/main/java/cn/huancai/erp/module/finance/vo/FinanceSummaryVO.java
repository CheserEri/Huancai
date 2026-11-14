package cn.huancai.erp.module.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceSummaryVO {

    private BigDecimal totalReceived;
    private BigDecimal monthReceived;
    private BigDecimal lastMonthReceived;
    private Long paymentCount;
    private Long studentCount;
    private Long orderCount;
    private BigDecimal debt;
}
