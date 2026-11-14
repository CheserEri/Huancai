package cn.huancai.erp.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long classId;
    private Long courseId;
    private Long teacherId;
    private Long studentId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String topic;
    private String status;
    private String className;
    private String courseName;
    private String teacherName;
    private String assistantName;
    private Integer lessonMode;
    private Long teachingLogId;
    private Integer shouldCount;
    private Integer actualCount;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Long extClassId;
    private Long extTeacherId;
    private Long extAssistantId;
    private Long extSchoolId;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
