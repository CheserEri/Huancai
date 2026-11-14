package cn.huancai.erp.module.course.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 课程新建 / 编辑表单。字段为 null 表示不修改（编辑时）。
 */
@Data
public class CourseForm {

    private String name;
    private String category;
    private String subject;
    private String lessonMode;
    private String campusScope;
    private Long campusId;
    private Boolean lessonCharge;
    private Boolean timeCharge;
    private Boolean periodCharge;
    private List<CoursePriceForm> prices;
    private BigDecimal materialFee;
    private String remark;

    @Data
    public static class CoursePriceForm {
        private Long campusId;
        private BigDecimal lessons;
        private BigDecimal totalPrice;
    }
}
