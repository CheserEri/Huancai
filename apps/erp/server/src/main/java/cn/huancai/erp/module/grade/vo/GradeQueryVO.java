package cn.huancai.erp.module.grade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 成绩查询列表行（仅已发布考试）。 */
@Data
public class GradeQueryVO {

    private Long id;
    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private String scoringMode;
    private Long classId;
    private String className;
    private Long studentId;
    private String studentName;
    private BigDecimal totalScore;
    private String level;
    private String status;
    private Integer rankClass;
    private Integer rankCampus;
    private String remark;
}
