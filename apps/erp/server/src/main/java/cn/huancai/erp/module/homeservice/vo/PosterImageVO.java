package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

/** 活动海报图片。 */
@Data
public class PosterImageVO {

    private Long id;
    private Long posterId;
    private String url;
    private Integer sortNo;
}
