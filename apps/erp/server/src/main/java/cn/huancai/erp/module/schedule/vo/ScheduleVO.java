package cn.huancai.erp.module.schedule.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ScheduleVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private Long classId;
    private String className;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private String assistantName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String topic;
    private String status;
    private Integer lessonMode;
    private Long teachingLogId;
    private Integer shouldCount;
    private Integer actualCount;
}
