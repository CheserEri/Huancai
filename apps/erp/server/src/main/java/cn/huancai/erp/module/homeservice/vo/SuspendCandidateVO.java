package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 可休课课次。 */
@Data
public class SuspendCandidateVO {

    private Long id;
    private Long classId;
    private String className;
    private String courseName;
    private String teacherName;
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String room;
    private String status;
    private Long suspendId;
}
