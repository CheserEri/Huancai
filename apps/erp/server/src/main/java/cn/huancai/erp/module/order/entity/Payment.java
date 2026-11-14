package cn.huancai.erp.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long orderId;
    private Long studentId;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paidAt;
    private Long operatorId;
    private String voucherNo;
    private String remark;
    private String extId;
    private LocalDateTime createdAt;
}
