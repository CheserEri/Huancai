package cn.huancai.erp.module.material.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialSummaryVO {

    private Long total;
    private Long orderCount;
    private BigDecimal amount;
    private Long unclaimed;
}
