package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 家校服务-班级休课记录。 */
@Data
@TableName("class_suspend")
public class ClassSuspend {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private Long campusId;
    private Long classId;
    private String className;
    private String courseName;
    private String teacherName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String reason;
    /** 已休课/已恢复 */
    private String status;
    private Long messageId;
    private Integer notifyFlag;
    private String operatorName;
    private LocalDateTime suspendedAt;
    private LocalDateTime restoredAt;
    private String restoredBy;
    private LocalDateTime createdAt;
}
