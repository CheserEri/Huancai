package cn.huancai.erp.module.wxapp.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 家长端成绩（只返回已发布考试）。 */
@Data
public class WxGradeVO {

    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private String scoringMode;
    private Long classId;
    private String className;
    private String courseName;
    private BigDecimal totalScore;
    private String level;
    private Integer rankClass;
    private Integer rankCampus;
    private String status;
    private String remark;
    @JsonIgnore
    private String partScoresJson;
    private Map<String, BigDecimal> partScores;
    /** 与上一次同课程成绩的差值，正数为提升。 */
    private BigDecimal deltaScore;
}
