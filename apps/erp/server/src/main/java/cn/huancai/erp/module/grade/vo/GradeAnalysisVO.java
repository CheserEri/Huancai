package cn.huancai.erp.module.grade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 成绩分析：整体指标 + 分数段分布 + 班级对比 + 排名。 */
@Data
public class GradeAnalysisVO {

    private Long examId;
    private String examName;
    private String examType;
    private LocalDate examDate;
    private String scoringMode;
    private BigDecimal fullScore;
    /** 及格线（分）。 */
    private BigDecimal passLine;
    private Integer studentCount;
    private Integer enteredCount;
    private Integer absentCount;
    private Integer exemptCount;
    private Integer missingCount;
    private BigDecimal avgScore;
    private BigDecimal maxScore;
    private BigDecimal minScore;
    /** 及格率（%）。 */
    private BigDecimal passRate;
    /** 优秀率（%）。 */
    private BigDecimal excellentRate;
    private List<Distribution> distributions;
    private List<ClassStat> classes;
    private List<RankRow> rankings;
    private List<LevelCount> levels;

    @Data
    public static class Distribution {
        private String label;
        private Integer count;
    }

    @Data
    public static class ClassStat {
        private Long classId;
        private String className;
        private Integer count;
        private BigDecimal avgScore;
        private BigDecimal maxScore;
        private BigDecimal minScore;
        private BigDecimal passRate;
        private BigDecimal excellentRate;
    }

    @Data
    public static class RankRow {
        private Integer rank;
        private Long studentId;
        private String studentName;
        private String className;
        private BigDecimal totalScore;
        private String level;
    }

    @Data
    public static class LevelCount {
        private String level;
        private Integer count;
    }
}
