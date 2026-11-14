package cn.huancai.erp.module.comment.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class CommentVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private Long classId;
    private String className;
    private Long courseId;
    private String courseName;
    private Long studentId;
    private String studentName;
    private String teacherName;
    private Long scheduleId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private String content;
    private String rating;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
