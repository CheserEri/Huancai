package cn.huancai.erp.module.wxapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家长端小程序订阅消息额度（一次性订阅：每次授权可发送一条）。 */
@Data
@TableName("wx_subscribe_quota")
public class WxSubscribeQuota {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String templateId;
    private Integer remainCount;
    private LocalDateTime updatedAt;
}
