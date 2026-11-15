package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家长端活动海报。 */
@Data
public class WxParentPosterVO {

    private Long id;
    private Long messageId;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime sentAt;
    /** 1=已读 0=未读 */
    private Integer readFlag;
    private Integer imageCount;
    private java.util.List<PosterImageVO> images;
}
