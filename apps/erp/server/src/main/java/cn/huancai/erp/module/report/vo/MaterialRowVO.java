package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialRowVO {

    private String name;
    private Long count;
    private BigDecimal receivable;
    private BigDecimal received;
}
