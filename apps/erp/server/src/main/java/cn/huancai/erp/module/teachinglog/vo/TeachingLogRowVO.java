package cn.huancai.erp.module.teachinglog.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TeachingLogRowVO {

    private Long id;
    private Long classId;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String className;
    private String courseName;
    private String teacherName;
    private String room;
    private Integer lessonMode;
    private String status;
    private Long campusId;
    private String campusName;
    private Integer shouldCount;
    private Integer actualCount;
    private Integer recordCount;
}
