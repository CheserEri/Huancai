package cn.huancai.erp.module.homeservice.dto;

import lombok.Data;

import java.time.LocalDate;

/** 新增/编辑班级作业。 */
@Data
public class HomeworkForm {

    private Long classId;
    private String title;
    private String content;
    private LocalDate dueDate;
    private LocalDate publishDate;
}
