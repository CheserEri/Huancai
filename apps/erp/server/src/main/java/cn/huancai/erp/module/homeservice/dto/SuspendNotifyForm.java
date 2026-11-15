package cn.huancai.erp.module.homeservice.dto;

import lombok.Data;

import java.util.List;

/** 对已休课课次批量发送家长通知。 */
@Data
public class SuspendNotifyForm {

    private List<Long> ids;
}
