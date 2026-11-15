package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 家长端班级相册：一个班级一条，带该班照片列表。 */
@Data
public class WxAlbumVO {

    private Long classId;
    private String className;
    private String courseName;
    private Integer photoCount;
    private LocalDateTime latestAt;
    private List<WxAlbumPhotoVO> photos = new ArrayList<>();
}
