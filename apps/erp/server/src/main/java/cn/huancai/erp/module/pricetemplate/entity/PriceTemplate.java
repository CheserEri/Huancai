package cn.huancai.erp.module.pricetemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 基础价格模板：价格与课程绑定，课程本身不再承载价格。 */
@Data
@TableName("price_template")
public class PriceTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String campusScope;
    private String campusIds;
    private String classType;
    private BigDecimal materialFee;
    private Integer validDays;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
