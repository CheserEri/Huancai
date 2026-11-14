package cn.huancai.erp.module.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 积分兑换物：pointsValue 为所需积分（积分口径，与金额无关）。 */
@Data
@TableName("points_goods")
public class PointsGoods {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal pointsValue;
    private Integer stock;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
