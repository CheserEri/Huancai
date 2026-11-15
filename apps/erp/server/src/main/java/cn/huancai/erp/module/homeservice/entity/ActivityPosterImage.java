package cn.huancai.erp.module.homeservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家校服务-活动海报图片。 */
@Data
@TableName("wx_activity_poster_image")
public class ActivityPosterImage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long posterId;
    private String url;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
