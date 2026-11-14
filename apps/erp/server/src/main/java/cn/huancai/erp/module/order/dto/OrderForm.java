package cn.huancai.erp.module.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 新建订单表单。 */
@Data
public class OrderForm {

    private Long studentId;
    private Long campusId;
    private String type;
    private String content;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal discount;
    private String payMethod;
    private String remark;
}
