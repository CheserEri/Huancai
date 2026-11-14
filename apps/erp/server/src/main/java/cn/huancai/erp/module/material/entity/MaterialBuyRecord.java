package cn.huancai.erp.module.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("material_buy_record")
public class MaterialBuyRecord {

    @TableId(type = IdType.AUTO)
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
