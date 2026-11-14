package cn.huancai.erp.module.activitytemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 活动模板优惠规则：节日活动单条（lessons=0），报课优惠按课时门槛多档。 */
@Data
@TableName("activity_template_rule")
public class ActivityTemplateRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private BigDecimal lessons;
    /** 折扣 / 立减 / 赠送课时 */
    private String discountMode;
    private BigDecimal discountValue;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
