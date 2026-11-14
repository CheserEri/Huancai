package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ClassAttendanceVO {

    private Long id;
    private Long scheduleId;
    private Long studentId;
    private String studentName;
    private LocalDate lessonDate;
    private String topic;
    private String teacherName;
    private String status;
    private BigDecimal lessons;
    private String remark;
}
