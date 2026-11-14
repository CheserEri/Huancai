package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 考试分项（也可用于分项模板配置）。 */
@Data
public class GradePartForm {

    private String name;
    private BigDecimal weight;
    private BigDecimal fullScore;
    private Integer sort;
}
