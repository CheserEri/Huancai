package cn.huancai.erp.module.course.vo;

import cn.huancai.erp.module.course.entity.CourseSubject;
import lombok.Data;

import java.util.List;

@Data
public class CourseOptionsVO {

    private List<CourseOptionVO> campuses;
    private List<CourseCategoryVO> categories;
    private List<CourseSubject> subjects;
}
