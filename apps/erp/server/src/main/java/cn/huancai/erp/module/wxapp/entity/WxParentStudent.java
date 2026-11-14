package cn.huancai.erp.module.wxapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家长与学员的绑定关系。 */
@Data
@TableName("wx_parent_student")
public class WxParentStudent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private Long studentId;
    private String relation;
    private LocalDateTime createdAt;
}
