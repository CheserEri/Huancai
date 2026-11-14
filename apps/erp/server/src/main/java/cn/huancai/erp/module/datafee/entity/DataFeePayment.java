package cn.huancai.erp.module.datafee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 资料费缴纳记录，按报读（课程）记录缴纳时间与周期。 */
@Data
@TableName("data_fee_payment")
public class DataFeePayment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private String campusName;
    private Long studentId;
    private String studentName;
    private Long enrollmentId;
    private Long courseId;
    private String courseName;
    private Long templateId;
    private String templateName;
    private BigDecimal price;
    private Integer cycleMonths;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime paidAt;
    private String payMethod;
    private String handler;
    private Long orderId;
    private String orderNo;
    private String remark;
    private LocalDateTime createdAt;
}
