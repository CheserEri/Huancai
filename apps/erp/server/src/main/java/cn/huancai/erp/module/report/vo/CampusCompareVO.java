package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CampusCompareVO {

    private String campusName;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal payments;
    private BigDecimal lessons;
    private Long activeStudents;
}
