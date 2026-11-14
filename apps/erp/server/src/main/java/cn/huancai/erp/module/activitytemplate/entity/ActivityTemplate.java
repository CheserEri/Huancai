package cn.huancai.erp.module.activitytemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 活动模板：节日活动（618/双11）或报课优惠（报10/20节）。 */
@Data
@TableName("activity_template")
public class ActivityTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    /** 节日活动 / 报课优惠 */
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
