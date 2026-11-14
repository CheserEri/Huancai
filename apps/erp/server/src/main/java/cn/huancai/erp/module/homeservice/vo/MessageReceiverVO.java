package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 消息接收明细行。 */
@Data
public class MessageReceiverVO {

    private Long id;
    private String studentName;
    private String parentName;
    private String parentPhone;
    private Integer readFlag;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
