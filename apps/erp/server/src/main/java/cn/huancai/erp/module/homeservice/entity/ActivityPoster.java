package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家校服务-活动海报推送。 */
@Data
@TableName("wx_activity_poster")
public class ActivityPoster {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    /** 年级/班级/学员 组合摘要 */
    private String audienceType;
    private String gradeText;
    private String classNameText;
    private Integer targetCount;
    private Integer receiverCount;
    /** 关联 wx_message.id，用于消息通知与已读统计 */
    private Long messageId;
    private String sender;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
