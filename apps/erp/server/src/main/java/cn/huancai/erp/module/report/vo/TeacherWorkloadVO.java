package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TeacherWorkloadVO {

    private String teacherName;
    private String campusName;
    private Long records;
    private Long schedules;
    private BigDecimal lessons;
    private Long classes;
    private BigDecimal attendRate;
}
