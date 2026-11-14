package cn.huancai.erp.module.teachinglog.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TeachingLogRequests {

    private TeachingLogRequests() {
    }

    /** 记上课：一次提交一个课次下所有学员的出勤与扣课时。 */
    public record Save(List<Record> records) {
    }

    public record Record(
            Long studentId,
            Long enrollmentId,
            String status,
            BigDecimal lessons,
            String remark
    ) {
    }
}
