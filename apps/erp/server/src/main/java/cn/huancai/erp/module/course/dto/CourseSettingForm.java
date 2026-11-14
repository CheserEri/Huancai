package cn.huancai.erp.module.course.dto;

import lombok.Data;

/** 课程类别 / 科目 / 升期关系维护表单。 */
@Data
public class CourseSettingForm {

    private String name;
    private Long fromCourseId;
    private Long toCourseId;
    private Integer periodCount;
    private Integer status;
}
