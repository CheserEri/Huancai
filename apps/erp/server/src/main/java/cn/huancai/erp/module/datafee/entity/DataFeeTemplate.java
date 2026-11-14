package cn.huancai.erp.module.datafee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资料费模板：价格 + 缴纳周期。资料是打印的 A4 纸，不涉及库存。 */
@Data
@TableName("data_fee_template")
public class DataFeeTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer cycleMonths;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
