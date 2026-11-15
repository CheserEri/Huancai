package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 家长端班级相册中的单张照片。 */
@Data
public class WxAlbumPhotoVO {

    private Long id;
    private Long classId;
    private String className;
    private String url;
    private String uploader;
    private LocalDateTime createdAt;
}
