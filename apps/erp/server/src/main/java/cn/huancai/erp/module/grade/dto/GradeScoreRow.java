package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 成绩录入单行：分项成绩 Map 的 key 为分项 id。 */
@Data
public class GradeScoreRow {

    private Long studentId;
    private Long enrollmentId;
    private BigDecimal totalScore;
    private String level;
    private Map<String, BigDecimal> partScores;
    private String status;
    private String remark;
}
