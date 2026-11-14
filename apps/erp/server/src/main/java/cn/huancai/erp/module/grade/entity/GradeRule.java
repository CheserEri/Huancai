package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成绩等级规则：分数区间 → 等级名称。 */
@Data
@TableName("grade_rule")
public class GradeRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private Integer sort;
    private LocalDateTime createdAt;
}
