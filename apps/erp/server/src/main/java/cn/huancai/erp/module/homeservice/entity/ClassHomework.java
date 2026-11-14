package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家校服务-班级作业。 */
@Data
@TableName("class_homework")
public class ClassHomework {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
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
