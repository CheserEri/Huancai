package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 家长端课时流水。 */
@Data
public class WxLessonTxnVO {

    private Long id;
    private String type;
    private BigDecimal lessons;
    private BigDecimal balanceAfter;
    private String courseName;
    private String note;
    private LocalDateTime createdAt;
}
