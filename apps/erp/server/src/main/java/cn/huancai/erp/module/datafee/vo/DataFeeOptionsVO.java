package cn.huancai.erp.module.datafee.vo;

import cn.huancai.erp.module.datafee.entity.DataFeeTemplate;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 资料费缴纳弹窗所需的下拉数据：启用中的模板、学员在读课程及上次缴纳时间。 */
@Data
public class DataFeeOptionsVO {

    private List<DataFeeTemplate> templates;
    private List<EnrollmentOption> enrollments;
    private List<String> payMethods;

    @Data
    public static class EnrollmentOption {
        private Long id;
        private Long courseId;
        private String courseName;
        private LocalDateTime lastPaidAt;
    }
}
