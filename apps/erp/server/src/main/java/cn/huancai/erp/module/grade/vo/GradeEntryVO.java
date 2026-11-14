package cn.huancai.erp.module.grade.vo;

import cn.huancai.erp.module.grade.entity.GradePart;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 成绩录入页数据：考试信息 + 班级 + 分项 + 学员名单。 */
@Data
public class GradeEntryVO {

    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private String scoringMode;
    private BigDecimal fullScore;
    private String status;
    private Long classId;
    private String className;
    private List<GradePart> parts;
    private List<GradeEntryStudentVO> students;
}
