package cn.huancai.erp.module.teaching.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 班级新建 / 编辑表单。字段为 null 表示不修改（编辑时）。
 */
@Data
public class ClassForm {

    private Long campusId;
    private String name;
    private Long courseId;
    private Long teacherId;
    private String assistName;
    private String lessonClassName;
    private String room;
    private String scheduleText;
    private Integer capacity;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer enrolling;
    private String feeStandard;
}
