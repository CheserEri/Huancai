package cn.huancai.erp.module.course.vo;

import lombok.Data;

@Data
public class CourseOptionVO {

    private Long id;
    private String name;

    public CourseOptionVO() {
    }

    public CourseOptionVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
