package cn.huancai.erp.module.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("class_group")
public class ClassGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long courseId;
    private Long teacherId;
    private String teacherText;
    private String name;
    private Integer capacity;
    private Integer enrolledCount;
    private String room;
    private String scheduleText;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long spClassId;
    private String lessonClassName;
    private String assistName;
    private String classTimeDesc;
    private BigDecimal stuRate;
    private String upRate;
    private Integer scheduleCount;
    private Integer enrolling;
    private String createBy;
    private String feeStandard;
    private Integer isCollection;
    private String source;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
