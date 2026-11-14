package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家校服务-消息接收记录（每位家长每个学员一条）。 */
@Data
@TableName("wx_message_receiver")
public class WxMessageReceiver {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long parentId;
    private Long studentId;
    private String studentName;
    private Integer readFlag;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
