package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("grade_exam_class")
public class GradeExamClass {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long classId;
}
