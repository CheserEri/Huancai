package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 新建/编辑考试表单。 */
@Data
public class GradeExamForm {

    private String name;
    private String type;
    private Long campusId;
    private Long courseId;
    private String courseName;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private BigDecimal passScore;
    private String scoringMode;
    private String remark;
    private List<Long> classIds;
    private List<GradePartForm> parts;
}
