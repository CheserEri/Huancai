package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ScreenReportVO {

    private Long studentsTotal;
    private Long activeStudents;
    private Long classes;
    private Long todayClasses;
    private Long monthOrders;
    private Long monthConsults;
    private BigDecimal monthRevenue;
    private BigDecimal monthLessons;
    private BigDecimal debt;
    private BigDecimal remainLessons;
    private List<CampusCompareVO> campuses;
    private List<TopCourseVO> topCourses;
}
