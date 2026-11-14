package cn.huancai.erp.module.material.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialSettingVO {

    private Long id;
    private String name;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private BigDecimal points;
    private String category;
    private String status;
}
