package cn.huancai.erp.module.order.vo;

import cn.huancai.erp.module.order.entity.SpOrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 历史订单列表 / 详情展示对象。 */
@Data
public class SpOrderVO {

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
    private String typeName;
    private String content;
    private BigDecimal shouldPay;
    private BigDecimal realPay;
    private BigDecimal remain;
    private BigDecimal arrearage;
    private BigDecimal credit;
    private Integer sourceType;
    private String sourceName;
    private String salesman;
    private String creator;
    private String salesSource;
    private String tags;
    private Boolean isReturn;
    private Integer statusCode;
    private String statusName;
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
    /** 最后修改时间，仅订单被编辑后才有值。 */
    private LocalDateTime updatedTime;
    private LocalDateTime expiryDate;
    private String rawJson;
    private String voidReason;
    private LocalDateTime voidAt;
    /** 订单明细（含课时），修改订单时展示与调整。 */
    private List<SpOrderItem> items;
}
