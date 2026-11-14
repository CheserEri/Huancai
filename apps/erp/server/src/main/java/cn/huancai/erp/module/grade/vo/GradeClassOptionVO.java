package cn.huancai.erp.module.grade.vo;

import lombok.Data;

/** 考试「适用班级」下拉项。 */
@Data
public class GradeClassOptionVO {

    private Long id;
    private String name;
    private Long campusId;
    private Long courseId;
    private String courseName;
}
