package cn.huancai.erp.module.student.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentSummaryVO {

    private Long count;
    private BigDecimal balance;
    private BigDecimal debt;
    private Long points;
}
