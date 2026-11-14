package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 家长端课堂表现 / 课堂点评。 */
@Data
public class WxCommentVO {

    private Long id;
    private Long classId;
    private String className;
    private Long courseId;
    private String courseName;
    private String teacherName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private String content;
    private String rating;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
