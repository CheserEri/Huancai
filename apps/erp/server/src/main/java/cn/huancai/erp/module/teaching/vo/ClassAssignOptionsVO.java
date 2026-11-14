package cn.huancai.erp.module.teaching.vo;

import lombok.Data;

import java.util.List;

/** 分班弹窗数据：同课程可选班级 + 候选学员。 */
@Data
public class ClassAssignOptionsVO {

    private List<ClassVO> classes;
    private List<ClassAssignStudentVO> students;
}
