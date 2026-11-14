package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 家长端课时账户（一个报读/课程一条）。 */
@Data
public class WxCourseBalanceVO {

    private Long enrollmentId;
    private Long courseId;
    private String courseName;
    private Long classId;
    private String className;
    private BigDecimal totalLessons;
    private BigDecimal giftLessons;
    private BigDecimal usedLessons;
    private BigDecimal remainLessons;
    private BigDecimal unitPrice;
    private LocalDate expireDate;
    private String status;
}
