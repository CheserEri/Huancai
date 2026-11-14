package cn.huancai.erp.module.schedule.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 排课新建 / 调课表单。字段为 null 表示不修改。 */
@Data
public class ScheduleForm {

    private Long campusId;
    private Long classId;
    private Long courseId;
    private Long teacherId;
    private Long studentId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String topic;
    private Integer lessonMode;
}
