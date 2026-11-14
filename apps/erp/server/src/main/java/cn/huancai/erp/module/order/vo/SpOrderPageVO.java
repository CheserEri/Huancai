package cn.huancai.erp.module.order.vo;

import lombok.Data;

import java.util.List;

/** 校宝订单分页结果。 */
@Data
public class SpOrderPageVO {

    private long total;
    private List<SpOrderVO> list;
    private SpOrderSummaryVO summary;
}
