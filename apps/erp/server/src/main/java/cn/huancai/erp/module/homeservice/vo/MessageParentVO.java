package cn.huancai.erp.module.homeservice.vo;

import lombok.Data;

/** 学员已绑定的家长。 */
@Data
public class MessageParentVO {

    private Long studentId;
    private Long parentId;
}
