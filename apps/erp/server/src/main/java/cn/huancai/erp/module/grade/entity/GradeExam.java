package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 考试成绩单：机构周考、月考与学校考试共用。 */
@Data
@TableName("grade_exam")
public class GradeExam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private String name;
    private String type;
    private Long courseId;
    private String courseName;
    private LocalDate examDate;
    private BigDecimal fullScore;
    /** 及格线（分），为空时按满分的 60% 计算。 */
    private BigDecimal passScore;
    private String scoringMode;
    /** 试卷图片 JSON 数组，如 ["/uploads/grade-paper/1/xxx.jpg"]。 */
    private String paperImages;
    private String status;
    private String creator;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
