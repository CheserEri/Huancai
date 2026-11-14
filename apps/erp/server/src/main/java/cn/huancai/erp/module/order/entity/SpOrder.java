package cn.huancai.erp.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 校宝订单快照（只读镜像）。 */
@Data
@TableName("sp_order")
public class SpOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spId;
    private String orderNo;
    private Long campusId;
    private Long schoolId;
    private String campusName;
    private Long studentId;
    private String stuName;
    private String stuTel;
    private String stuNamePinyin;
    private Integer typeCode;
    private String content;
    private BigDecimal shouldPay;
    private BigDecimal realPay;
    private BigDecimal remain;
    private BigDecimal arrearage;
    private BigDecimal credit;
    private Integer sourceType;
    private String salesman;
    private String creator;
    private String salesSource;
    private String tags;
    private Boolean isReturn;
    private Integer statusCode;
    private Integer contractStatus;
    private String payMethod;
    private String comment;
    private String commentOuter;
    private Boolean hasConfirmedDetail;
    private Boolean hasTextbook;
    private Boolean isOnlinePayment;
    private Boolean hasMultiLessonEnroll;
    private LocalDateTime dealDate;
    private LocalDateTime createdTime;
    private LocalDateTime expiryDate;
    private String rawJson;
    /** 作废原因与作废时间，仅本地作废时写入。 */
    private String voidReason;
    private LocalDateTime voidAt;
}
