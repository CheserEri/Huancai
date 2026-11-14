package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 班级相册照片。 */
@Data
public class ClassPhotoVO {

    private Long id;
    private Long classId;
    private String url;
    private String uploader;
    private LocalDateTime createdAt;
}
