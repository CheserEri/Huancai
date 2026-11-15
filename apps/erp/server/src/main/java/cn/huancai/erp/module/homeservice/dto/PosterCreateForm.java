package cn.huancai.erp.module.homeservice.dto;

import lombok.Data;

import java.util.List;

/** 新建活动海报推送。 */
@Data
public class PosterCreateForm {

    private String title;
    private String description;
    /** 已上传的海报地址，按展示顺序 */
    private List<String> imageUrls;
    /** 年级（student.grade） */
    private List<String> grades;
    private List<Long> classIds;
    private List<Long> studentIds;
    /** yyyy-MM-dd，为空表示长期有效 */
    private String startDate;
    private String endDate;
}
