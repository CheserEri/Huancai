package cn.huancai.erp.module.teaching.dto;

import lombok.Data;

import java.util.List;

/** 升班：把原班级的一批学员分批升入一个或多个新班级。 */
@Data
public class ClassUpgradeForm {

    private Long sourceClassId;

    private List<Target> targets;

    @Data
    public static class Target {
        private Long classId;
        private List<Long> studentIds;
    }
}
