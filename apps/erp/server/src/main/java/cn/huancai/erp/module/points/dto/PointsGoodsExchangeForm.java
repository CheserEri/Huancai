package cn.huancai.erp.module.points.dto;

import lombok.Data;

/** 学员积分兑换兑换物。 */
@Data
public class PointsGoodsExchangeForm {

    private Long studentId;
    private Long goodsId;
    private Integer quantity;
    private String handler;
    private String remark;
}
