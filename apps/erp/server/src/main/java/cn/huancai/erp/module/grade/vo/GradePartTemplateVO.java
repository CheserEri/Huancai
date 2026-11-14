package cn.huancai.erp.module.grade.vo;

import cn.huancai.erp.module.grade.dto.GradePartForm;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 分项模板：parts 已解析为结构化列表。 */
@Data
public class GradePartTemplateVO {

    private Long id;
    private String name;
    private String courseCategory;
    private List<GradePartForm> parts;
    private LocalDateTime createdAt;
}
