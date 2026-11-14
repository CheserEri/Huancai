package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CourseClassVO {

    private Long id;
    private String name;
    private String teacherName;
    private String status;
    private Integer enrolledCount;
    private Integer capacity;
    private String scheduleText;
    private LocalDate startDate;
}
