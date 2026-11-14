package cn.huancai.erp.module.points.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 积分兑换物维护表单：pointsValue 为所需积分（积分口径，非金额）。 */
@Data
public class PointsGoodsForm {

    private String name;
    private BigDecimal pointsValue;
    private Integer stock;
    private String status;
    private String remark;
}
