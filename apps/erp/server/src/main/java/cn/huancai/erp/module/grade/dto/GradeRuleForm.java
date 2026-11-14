package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeRuleForm {

    private String name;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private Integer sort;
}
