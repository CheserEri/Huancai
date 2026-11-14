package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 家长端待上课程。 */
@Data
public class WxUpcomingLessonVO {

    private Long id;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String className;
    private String courseName;
    private String teacherName;
    private String room;
    private String status;
}
