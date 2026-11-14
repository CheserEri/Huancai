package cn.huancai.erp.module.teaching.dto;

import lombok.Data;

import java.util.List;

/** 班级批量操作。 */
@Data
public class ClassBatchForm {

    private List<Long> ids;
    private Integer enrolling;
    private String status;
}
