package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BusinessReportVO {

    /** 期间有跟进的咨询线索数 */
    private Long consults;
    /** 期间报名订单数（招生人次） */
    private Long deals;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal discount;
    private BigDecimal refund;
    private BigDecimal periodPayments;
    private BigDecimal balance;
    private Long activeStudents;
    private Long enrollmentCount;
    private BigDecimal remainLessons;
    private BigDecimal periodLessons;
    private BigDecimal debt;
    private BigDecimal materialIncome;
    private List<CampusCompareVO> campuses;
}
