package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 分班弹窗中的候选学员（按课程维度）。 */
@Data
public class ClassAssignStudentVO {

    private Long studentId;
    private String studentName;
    private String phone;
    private Long classId;
    private String className;
    private BigDecimal totalLessons;
    private BigDecimal remainLessons;
    private String enrollStatus;
}
