package cn.huancai.erp.module.teacherhour.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 教师上课记录（一节课一行），含上课人数与按规则计算的课酬。 */
@Data
public class TeacherHourRecordVO {

    private Long scheduleId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long teacherId;
    private String teacherName;
    private Long campusId;
    private String campusName;
    private Long classId;
    private String className;
    private String courseName;
    /** 主要年级，如「三年级」，用于课酬分档与导出明细 */
    private String grade;
    private Integer shouldCount;
    private Integer studentCount;
    /** 命中的课酬规则；未配置时为空 */
    private BigDecimal unitPrice;
    private Integer thresholdStudents;
    private BigDecimal fixedAmount;
    private Boolean rateConfigured;
    /** 单节手工调整金额，为空表示按规则计算 */
    private BigDecimal adjustAmount;
    private String adjustRemark;
    /** 最终课酬 */
    private BigDecimal amount;
}
