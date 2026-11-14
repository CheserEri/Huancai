package cn.huancai.erp.module.staff.vo;

import java.util.List;

/** 员工表单下拉选项。 */
public record StaffOptionsVO(List<CampusOption> campuses, List<String> roles) {

    public record CampusOption(Long id, String name, Integer status) {
    }
}
