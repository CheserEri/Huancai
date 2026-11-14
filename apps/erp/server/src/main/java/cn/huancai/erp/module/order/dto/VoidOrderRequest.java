package cn.huancai.erp.module.order.dto;

/** 作废订单请求：作废原因需 5~100 字。 */
public record VoidOrderRequest(String reason) {
}
