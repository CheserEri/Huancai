package cn.huancai.erp.module.teaching.dto;

import lombok.Data;

import java.util.List;

/** 批量分班：把选中学员调整到目标班级，未选中学员留在原班。 */
@Data
public class ClassAssignForm {

    private Long courseId;
    private Long targetClassId;
    private List<Long> studentIds;
}
