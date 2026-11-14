package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TeachingReportVO {

    private Long records;
    private BigDecimal lessons;
    private Long students;
    private Long teacherCount;
    private Long present;
    private BigDecimal attendRate;
    private List<TeacherWorkloadVO> teachers;
    private List<CourseConsumeVO> courses;
}
