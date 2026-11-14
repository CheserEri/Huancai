package cn.huancai.erp.module.student.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EnrollmentVO {

    private Long id;
    private Long courseId;
    private Long classId;
    private String courseName;
    private String className;
    private String teacher;
    private LocalDate startDate;
    private LocalDateTime enrollTime;
    private BigDecimal totalLessons;
    private BigDecimal giftLessons;
    private BigDecimal remainLessons;
    private BigDecimal usedLessons;
    private BigDecimal paidAmount;
    private BigDecimal consumedAmount;
    private BigDecimal unitPrice;
    private BigDecimal accountBalance;
    private LocalDate expireDate;
    private String status;
    private String remark;
    /** 上次资料费缴纳时间，用于课程卡片展示。 */
    private LocalDateTime lastDataFeePaidAt;
}
