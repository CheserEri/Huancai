package cn.huancai.erp.module.grade.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** 成绩录入网格中的一行学员成绩。 */
@Data
public class GradeEntryStudentVO {

    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long enrollmentId;
    private Long scoreId;
    private BigDecimal totalScore;
    private String level;
    @JsonIgnore
    private String partScoresJson;
    private Map<String, BigDecimal> partScores;
    /** 正常 / 缺考 / 免考 / 未考 */
    private String status;
    private String remark;
    private Integer rankClass;
    /** 该学员的试卷图片地址，未上传时为 null。 */
    private String paperImage;
}
