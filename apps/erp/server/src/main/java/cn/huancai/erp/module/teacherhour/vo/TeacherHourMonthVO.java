package cn.huancai.erp.module.teacherhour.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 教师课时页面/导出所需的整月数据。 */
@Data
public class TeacherHourMonthVO {

    private List<TeacherHourRecordVO> records;
    private List<TeacherHourSummaryVO> teachers;
    private List<Option> teacherOptions;
    private List<Option> campusOptions;
    private Integer lessonCount;
    private Integer studentCount;
    private BigDecimal amount;
    /** 有记录但未配置课酬规则的教师数 */
    private Integer missingRateCount;

    @Data
    public static class Option {
        private Long id;
        private String name;

        public Option() {
        }

        public Option(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
