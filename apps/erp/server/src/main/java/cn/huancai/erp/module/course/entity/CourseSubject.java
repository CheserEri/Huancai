package cn.huancai.erp.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_subject")
public class CourseSubject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
}
