package cn.huancai.erp.module.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 班级相册照片。 */
@Data
@TableName("class_photo")
public class ClassPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long classId;
    /** 可访问的图片地址，如 /uploads/class-album/1/xxx.jpg。 */
    private String url;
    private String uploader;
    private LocalDateTime createdAt;
}
