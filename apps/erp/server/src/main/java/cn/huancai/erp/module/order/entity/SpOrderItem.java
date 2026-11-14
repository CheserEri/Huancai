package cn.huancai.erp.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单明细：保存价格模板与活动模板的明文快照，模板后续变更不影响历史订单。 */
@Data
@TableName("sp_order_item")
public class SpOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long enrollmentId;
    private Long courseId;
    private String courseName;
    private Long priceTemplateId;
    private String priceTemplateName;
    private Long activityTemplateId;
    private String activityTemplateName;
    /** 本次使用的全部活动模板 id（逗号分隔），支持多活动叠加。 */
    private String activityTemplateIds;
    private BigDecimal lessons;
    private BigDecimal unitPrice;
    private BigDecimal baseAmount;
    private BigDecimal discountAmount;
    private BigDecimal materialAmount;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
