package cn.huancai.erp.module.teacherhour.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 教师月度汇总：课时数（节）、上课人次、课酬合计、明细（年级节数）。 */
@Data
public class TeacherHourSummaryVO {

    private Long teacherId;
    private String teacherName;
    private Integer lessonCount;
    private Integer studentCount;
    private BigDecimal amount;
    private String detail;
    private List<String> remarks;
}
