package cn.huancai.erp.module.grade.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 学员详情「成绩」页签：单个学员的历次成绩。 */
@Data
public class GradeStudentItemVO {

    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private String scoringMode;
    private Long classId;
    private String className;
    private BigDecimal totalScore;
    private String level;
    @JsonIgnore
    private String partScoresJson;
    private Map<String, BigDecimal> partScores;
    private String status;
    private Integer rankClass;
    private Integer rankCampus;
    /** 同场同班平均分，用于对比学员成绩波动。 */
    private BigDecimal classAvgScore;
    private String remark;
}
