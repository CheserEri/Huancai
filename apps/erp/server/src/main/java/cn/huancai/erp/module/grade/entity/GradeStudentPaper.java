package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学员试卷图片：每场考试每位学员一张，仅对应家长可查看。 */
@Data
@TableName("grade_student_paper")
public class GradeStudentPaper {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long studentId;
    private String url;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
