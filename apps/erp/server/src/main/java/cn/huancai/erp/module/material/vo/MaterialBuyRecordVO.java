package cn.huancai.erp.module.material.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MaterialBuyRecordVO {

    private Long id;
    private String extOrderNo;
    private String tradeType;
    private String studentName;
    private String itemName;
    private Integer quantity;
    private String paidText;
    private BigDecimal paidAmount;
    private Integer paidPoints;
    private String receiveStatus;
    private String campusName;
    private String handler;
    private LocalDate handledDate;
    private String remark;
    private String source;
}
