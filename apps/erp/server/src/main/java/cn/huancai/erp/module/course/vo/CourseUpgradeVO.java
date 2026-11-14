package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseUpgradeVO {

    private Long id;
    private String name;
    private Long fromCourseId;
    private String fromCourseName;
    private Long toCourseId;
    private String toCourseName;
    private Integer periodCount;
    private Integer courseCount;
    private LocalDateTime createdAt;
}
