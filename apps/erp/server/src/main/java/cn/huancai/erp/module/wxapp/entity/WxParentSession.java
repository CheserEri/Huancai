package cn.huancai.erp.module.wxapp.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家长端登录会话，与员工 staff_session 隔离。 */
@Data
@TableName("wx_parent_session")
public class WxParentSession {

    @TableId
    private String token;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
