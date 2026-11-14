package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CourseVO {

    private Long id;
    private String name;
    private String category;
    private String subject;
    private String lessonMode;
    private String chargeMode;
    private String campusScope;
    private Long campusId;
    private String campusName;
    private Long campusCount;
    private Long studentCount;
    private Long activeStudentCount;
    private BigDecimal buyLessons;
    private BigDecimal usedLessons;
    private BigDecimal remainLessons;
    private BigDecimal paidAmount;
    private BigDecimal debt;
    private Long classCount;
    private Long scheduleCount;
    private LocalDate lastLessonDate;
    private BigDecimal materialFee;
    private LocalDateTime createdAt;
    private Integer status;
    private String remark;
}
