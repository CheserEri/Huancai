package cn.huancai.erp.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("enrollment")
public class Enrollment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long studentId;
    private Long courseId;
    private Long classId;
    private Long orderId;
    private BigDecimal totalLessons;
    private BigDecimal giftLessons;
    private BigDecimal usedLessons;
    private BigDecimal remainLessons;
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private BigDecimal consumedAmount;
    private BigDecimal unitPrice;
    /** 本次报读使用的价格模板，便于追溯。 */
    private Long priceTemplateId;
    private LocalDate expireDate;
    private String status;
    private String remark;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
