package cn.huancai.erp.module.pricetemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 价格模板的课时档位（课时数 / 单价 / 总价）。 */
@Data
@TableName("price_template_price")
public class PriceTemplatePrice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private BigDecimal lessons;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
