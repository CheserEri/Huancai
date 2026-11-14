package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopCourseVO {

    private String courseName;
    private Long students;
    private BigDecimal remainLessons;
}
