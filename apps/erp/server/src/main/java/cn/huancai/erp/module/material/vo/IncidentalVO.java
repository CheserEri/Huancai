package cn.huancai.erp.module.material.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IncidentalVO {

    private Long id;
    private String name;
    private BigDecimal price;
    private BigDecimal points;
    private String category;
    private String status;
}
