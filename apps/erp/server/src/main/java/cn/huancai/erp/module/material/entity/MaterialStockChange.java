package cn.huancai.erp.module.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("material_stock_change")
public class MaterialStockChange {

    @TableId(type = IdType.AUTO)
    private Long id;
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
    private Integer seq;
    private String source;
    private LocalDateTime createdAt;
}
