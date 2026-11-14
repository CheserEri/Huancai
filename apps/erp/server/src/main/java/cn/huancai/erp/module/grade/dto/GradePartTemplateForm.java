package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.util.List;

@Data
public class GradePartTemplateForm {

    private String name;
    private String courseCategory;
    private List<GradePartForm> parts;
}
