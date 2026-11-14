package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ClassScheduleVO {

    private Long id;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String courseName;
    private String room;
    private String topic;
    private String status;
    private String teacherName;
    private String assistantName;
    private Integer shouldCount;
    private Integer actualCount;
    private Integer attendanceCount;
    private Integer presentCount;
    private Integer leaveCount;
    private Integer absentCount;
    private Integer lateCount;
    private BigDecimal lessonCount;
    private BigDecimal attendanceRate;
    private Integer commentCount;
    private LocalDateTime createdAt;
}
