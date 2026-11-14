package cn.huancai.erp.module.teaching.dto;

import lombok.Data;

import java.util.List;

/** 分班：向班级添加学员。 */
@Data
public class ClassStudentAddForm {

    private List<Long> studentIds;
}
