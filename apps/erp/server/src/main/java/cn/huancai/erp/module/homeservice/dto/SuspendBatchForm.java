package cn.huancai.erp.module.homeservice.dto;

import lombok.Data;

import java.util.List;

/** 批量休课。 */
@Data
public class SuspendBatchForm {

    private List<Long> scheduleIds;
    private String reason;
    /** 是否向受影响班级家长推送休课通知。 */
    private Boolean notify;
}
