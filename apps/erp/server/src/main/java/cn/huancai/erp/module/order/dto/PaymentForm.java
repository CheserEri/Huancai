package cn.huancai.erp.module.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 收款 / 退费表单。 */
@Data
public class PaymentForm {

    private BigDecimal amount;
    private String method;
    private String remark;
}
