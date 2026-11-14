package cn.huancai.erp.module.course.dto;

import lombok.Data;

import java.util.List;

/** 课程批量操作：delete / on / off。 */
@Data
public class CourseBatchForm {

    private List<Long> ids;
    private String action;
}
