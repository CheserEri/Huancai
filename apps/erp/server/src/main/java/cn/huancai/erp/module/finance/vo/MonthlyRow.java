package cn.huancai.erp.module.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyRow {

    private String month;
    private Long count;
    private BigDecimal amount;
}
