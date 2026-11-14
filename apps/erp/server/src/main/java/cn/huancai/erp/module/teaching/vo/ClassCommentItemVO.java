package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ClassCommentItemVO {

    private Long id;
    private Long studentId;
    private String studentName;
    private String teacherName;
    private LocalDate lessonDate;
    private String content;
    private String rating;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
