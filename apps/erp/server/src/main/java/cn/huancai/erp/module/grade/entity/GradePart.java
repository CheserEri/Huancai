package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("grade_part")
public class GradePart {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private String name;
    private BigDecimal weight;
    private BigDecimal fullScore;
    private Integer sort;
}
