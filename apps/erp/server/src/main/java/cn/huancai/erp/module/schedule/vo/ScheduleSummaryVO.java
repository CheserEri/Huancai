package cn.huancai.erp.module.schedule.vo;

import lombok.Data;

@Data
public class ScheduleSummaryVO {

    private Integer total;
    private Integer done;
    private Integer pending;
    private Integer shouldStudents;
    private Integer actualStudents;
    private Integer classes;
}
