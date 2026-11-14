package cn.huancai.erp.module.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private BigDecimal points;
    private String category;
    private String status;
    private Integer qtyWarehouse;
    private Integer qtyBayuquan;
    private Integer qtyXinmin;
    private Integer qtyTotal;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
