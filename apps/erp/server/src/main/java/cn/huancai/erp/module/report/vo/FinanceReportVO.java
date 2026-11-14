package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FinanceReportVO {

    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal refund;
    private BigDecimal debt;
    private Long orderCount;
    private Long paymentCount;
    private List<DailyFinanceVO> daily;
}
