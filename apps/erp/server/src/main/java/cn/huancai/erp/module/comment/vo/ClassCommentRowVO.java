package cn.huancai.erp.module.comment.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 家校服务-课堂点评列表行。 */
@Data
public class ClassCommentRowVO {

    private Long id;
    private Long classId;
    private Long studentId;
    private String studentName;
    private Long scheduleId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long teacherId;
    private String teacherName;
    private String content;
    private String rating;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
