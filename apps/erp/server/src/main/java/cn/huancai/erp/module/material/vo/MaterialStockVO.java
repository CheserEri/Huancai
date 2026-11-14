package cn.huancai.erp.module.material.vo;

import lombok.Data;

@Data
public class MaterialStockVO {

    private Long id;
    private String name;
    private String category;
    private String status;
    private Integer warehouse;
    private Integer bayuquan;
    private Integer xinmin;
    private Integer total;
}
