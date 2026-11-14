package cn.huancai.erp.module.material.dto;

import lombok.Data;

/** 学员用积分兑换教材：扣减学员积分与库存，生成购买记录与积分订单。 */
@Data
public class PointsExchangeForm {

    private Long studentId;
    private String roomName;
    private String materialName;
    private Integer quantity;
    private String handler;
    private String remark;
}
