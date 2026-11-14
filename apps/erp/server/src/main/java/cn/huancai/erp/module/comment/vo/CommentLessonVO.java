package cn.huancai.erp.module.comment.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CommentLessonVO {

    private Long scheduleId;
    private Long campusId;
    private String campusName;
    private Long classId;
    private String className;
    private Long courseId;
    private String courseName;
    private String teacherName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer shouldCount;
    private Integer actualCount;
    private Long commentCount;
    private Long readCount;
}
