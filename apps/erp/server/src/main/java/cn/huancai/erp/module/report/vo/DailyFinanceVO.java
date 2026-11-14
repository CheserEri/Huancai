package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DailyFinanceVO {

    private String date;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal refund;
    private BigDecimal debt;
}
