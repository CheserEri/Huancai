package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 家长端消息通知。 */
@Data
public class WxParentMessageVO {

    /** 接收记录ID，用于标记已读。 */
    private Long id;
    private Long messageId;
    private String title;
    private String content;
    private String msgType;
    private Long studentId;
    private String studentName;
    private Integer readFlag;
    private LocalDateTime readAt;
    private LocalDateTime sentAt;
}
