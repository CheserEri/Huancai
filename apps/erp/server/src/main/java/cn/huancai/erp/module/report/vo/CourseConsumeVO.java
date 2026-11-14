package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseConsumeVO {

    private String courseName;
    private Long students;
    private Long records;
    private BigDecimal lessons;
}
