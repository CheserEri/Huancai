package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 班级作业行。 */
@Data
public class HomeworkVO {

    private Long id;
    private Long classId;
    private String className;
    private Long teacherId;
    private String teacherName;
    private String title;
    private String content;
    private LocalDate dueDate;
    private LocalDate publishDate;
    private String publisher;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
