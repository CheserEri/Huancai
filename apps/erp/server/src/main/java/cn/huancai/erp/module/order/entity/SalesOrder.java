package cn.huancai.erp.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sales_order")
public class SalesOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long campusId;
    private Long studentId;
    private String type;
    private String content;
    private BigDecimal receivable;
    private BigDecimal received;
    private BigDecimal discount;
    private BigDecimal debt;
    private Long handlerId;
    private String payMethod;
    private String status;
    private LocalDateTime paidAt;
    private String remark;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
