package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家校服务-活动海报目标学员。 */
@Data
@TableName("wx_activity_poster_target")
public class ActivityPosterTarget {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long posterId;
    private Long studentId;
    private String studentName;
    private LocalDateTime createdAt;
}
