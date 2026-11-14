package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 休课记录行。 */
@Data
public class SuspendVO {

    private Long id;
    private Long scheduleId;
    private Long classId;
    private String className;
    private String courseName;
    private String teacherName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String reason;
    private String status;
    private Integer notifyFlag;
    private Long messageId;
    private String operatorName;
    private LocalDateTime suspendedAt;
    private LocalDateTime restoredAt;
    private String restoredBy;
}
