package cn.huancai.erp.module.activitytemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动分类：支持层级，如「1-3年级」下挂具体活动模板。 */
@Data
@TableName("activity_category")
public class ActivityCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
