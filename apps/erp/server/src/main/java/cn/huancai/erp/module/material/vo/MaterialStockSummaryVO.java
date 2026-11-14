package cn.huancai.erp.module.material.vo;

import lombok.Data;

@Data
public class MaterialStockSummaryVO {

    private Long count;
    private Long warehouse;
    private Long bayuquan;
    private Long xinmin;
    private Long total;
    private Long lowStock;
}
