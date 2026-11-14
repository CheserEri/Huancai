package cn.huancai.erp.module.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AttendanceSummaryVO {

    private Long total;
    private Long present;
    private Long leaveCount;
    private Long absent;
    private BigDecimal lessons;
}
