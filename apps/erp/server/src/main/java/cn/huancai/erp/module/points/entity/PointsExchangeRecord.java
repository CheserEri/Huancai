package cn.huancai.erp.module.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 积分兑换记录：pointsCost 为消耗积分（积分口径，与金额无关）。 */
@Data
@TableName("points_exchange_record")
public class PointsExchangeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long studentId;
    private String studentName;
    private Long goodsId;
    private String goodsName;
    private Integer quantity;
    private BigDecimal pointsCost;
    private String handler;
    private String remark;
    private LocalDateTime createdAt;
}
