package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 活动海报推送列表行。 */
@Data
public class PosterVO {

    private Long id;
    private String title;
    private String description;
    private String audienceType;
    private String gradeText;
    private String classNameText;
    private Integer targetCount;
    private Integer receiverCount;
    private Integer readCount;
    private Long messageId;
    private String sender;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime sentAt;
    private Integer imageCount;
    private String coverUrl;
}
