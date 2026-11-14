package cn.huancai.erp.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long campusId;
    private String campusName;
    private Long studentId;
    private String studentName;
    private String phone;
    private String type;
    private String content;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal discount;
    private BigDecimal debt;
    private String handlerName;
    private String payMethod;
    private String status;
    private LocalDateTime paidAt;
    private String remark;
}
