package cn.huancai.erp.module.grade.vo;

import cn.huancai.erp.module.grade.entity.GradePart;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 考试列表/详情：考试信息 + 适用班级 + 分项 + 录入进度。 */
@Data
public class GradeExamVO {

    private Long id;
    private Long campusId;
    private String name;
    private String type;
    private Long courseId;
    private String courseName;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private BigDecimal passScore;
    private String scoringMode;
    /** 试卷图片地址列表。 */
    private List<String> paperImages;
    private String status;
    private String creator;
    private String remark;
    private LocalDateTime createdAt;
    private List<Long> classIds;
    private List<String> classNames;
    private List<GradePart> parts;
    /** 适用班级在读学员总数。 */
    private Integer studentCount;
    /** 已录入成绩（正常/缺考/免考）人数。 */
    private Integer enteredCount;
}
