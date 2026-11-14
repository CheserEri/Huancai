package cn.huancai.erp.module.homeservice.dto;

import lombok.Data;

import java.util.List;

/** 新建消息推送。 */
@Data
public class MessageSendForm {

    private String title;
    private String content;
    /** 全员/班级/学员 */
    private String audienceType;
    private List<Long> classIds;
    private List<Long> studentIds;
}
