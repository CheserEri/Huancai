package cn.huancai.erp.module.wxapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家长端小程序用户（按微信 openid 唯一）。 */
@Data
@TableName("wx_parent")
public class WxParent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
