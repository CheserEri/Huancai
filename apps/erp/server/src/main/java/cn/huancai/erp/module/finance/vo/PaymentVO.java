package cn.huancai.erp.module.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private Long studentId;
    private String studentName;
    private String orderNo;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
    private String operatorName;
    private String voucherNo;
    private String remark;
}
