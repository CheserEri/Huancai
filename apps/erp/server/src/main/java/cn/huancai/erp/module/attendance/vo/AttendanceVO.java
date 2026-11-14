package cn.huancai.erp.module.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AttendanceVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private LocalDate lessonDate;
    private String studentName;
    private String className;
    private String courseName;
    private String teacherName;
    private String status;
    private BigDecimal lessons;
    private LocalDateTime checkedAt;
    private String remark;
}
