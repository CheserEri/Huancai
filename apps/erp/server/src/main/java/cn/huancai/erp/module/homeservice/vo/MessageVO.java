package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 消息推送列表行。 */
@Data
public class MessageVO {

    private Long id;
    private String title;
    private String content;
    private String msgType;
    private String audienceType;
    private String classNameText;
    private Integer targetCount;
    private Integer receiverCount;
    private Integer readCount;
    private String sender;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
