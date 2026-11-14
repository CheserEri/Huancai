package cn.huancai.erp.module.datafee.dto;

import java.math.BigDecimal;

/** 资料费模板与缴纳请求。 */
public final class DataFeeRequests {

    private DataFeeRequests() {
    }

    /** 资料费模板：名称、价格、缴纳周期（月）。 */
    public record TemplateForm(String name, BigDecimal price, Integer cycleMonths, String status, String remark) {
    }

    /** 为某个学员的某门课程缴纳资料费。 */
    public record PaymentForm(Long studentId, Long enrollmentId, Long templateId,
                              String payMethod, String handler, String remark) {
    }
}
