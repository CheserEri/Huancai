package cn.huancai.erp.module.grade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 班级详情「成绩」页签：该班级的考试与整体表现。 */
@Data
public class GradeClassExamVO {

    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private String scoringMode;
    private String status;
    private Integer enteredCount;
    private Integer studentCount;
    private BigDecimal avgScore;
    private BigDecimal maxScore;
    private BigDecimal minScore;
    /** 及格率（%），无有效成绩时为空。 */
    private BigDecimal passRate;
    /** 优秀率（%），无有效成绩时为空。 */
    private BigDecimal excellentRate;
}
