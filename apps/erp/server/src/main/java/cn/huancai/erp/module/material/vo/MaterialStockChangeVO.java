package cn.huancai.erp.module.material.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialStockChangeVO {

    private Long id;
    private String roomName;
    private String materialName;
    private String bizType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String changeDate;
    private String handler;
    private String studentName;
    private String extOrderNo;
    private String remark;
}
