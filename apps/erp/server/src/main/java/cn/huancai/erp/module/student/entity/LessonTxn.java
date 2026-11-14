package cn.huancai.erp.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lesson_txn")
public class LessonTxn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long enrollmentId;
    private Long studentId;
    private String type;
    private BigDecimal lessons;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String bizType;
    private Long bizId;
    private String note;
    private Long operatorId;
    private LocalDateTime createdAt;
}
