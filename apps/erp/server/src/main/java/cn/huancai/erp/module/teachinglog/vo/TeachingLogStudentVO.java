package cn.huancai.erp.module.teachinglog.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TeachingLogStudentVO {

    private Long studentId;
    private String studentName;
    private String studentStatus;
    private Long enrollmentId;
    private String courseName;
    private BigDecimal remainLessons;
    private BigDecimal unitPrice;
    private String recordStatus;
    private BigDecimal recordLessons;
    private String recordRemark;
}
