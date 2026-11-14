package cn.huancai.erp.module.material.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 学员购买教材：一次可购买多本教材，统一结算并扣减库存。 */
@Data
public class MaterialPurchaseForm {

    private Long studentId;
    private String roomName;
    /** 支付方式：微信 / 支付宝 / 现金 / 银行卡 / POS / 转账。 */
    private String payMethod;
    /** 本次购买的教材明细。 */
    private List<Item> items;
    /** 使用学员账户剩余未用学费抵扣的金额，剩余部分收现金。 */
    private BigDecimal balanceAmount;
    private String handler;
    private String remark;

    @Data
    public static class Item {
        private String materialName;
        private Integer quantity;
        /** 不填按教材设置售价。 */
        private BigDecimal unitPrice;
    }
}
