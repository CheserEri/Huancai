package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseSummaryVO {

    private Long total;
    private Long active;
    /** 正在开班/课的课程数（已有班级的课程）。 */
    private Long opening;
    private Long enrollCount;
    private BigDecimal remainLessons;
    private BigDecimal paidAmount;
    private BigDecimal debt;
}
