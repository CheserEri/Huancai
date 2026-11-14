package cn.huancai.erp.module.material.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 教材设置表单。 */
@Data
public class MaterialForm {

    private String name;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private BigDecimal points;
    private String category;
    private String status;
}
