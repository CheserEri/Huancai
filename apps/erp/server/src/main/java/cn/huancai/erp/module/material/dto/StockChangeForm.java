package cn.huancai.erp.module.material.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 教材出入库表单；出库时 quantity 传正数，后端存为负数。 */
@Data
public class StockChangeForm {

    private String roomName;
    private String materialName;
    private String bizType;
    private Integer quantity;
    private BigDecimal unitPrice;
    private LocalDate changeDate;
    private String handler;
    private String studentName;
    private String extOrderNo;
    private String remark;
}
