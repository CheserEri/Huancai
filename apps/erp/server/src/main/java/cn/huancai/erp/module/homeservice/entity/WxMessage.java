package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家校服务-消息推送主表。 */
@Data
@TableName("wx_message")
public class WxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    /** 系统通知/休课通知 */
    private String msgType;
    /** 全员/班级/学员 */
    private String audienceType;
    private String classNameText;
    private Integer targetCount;
    private Integer receiverCount;
    private String sender;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
