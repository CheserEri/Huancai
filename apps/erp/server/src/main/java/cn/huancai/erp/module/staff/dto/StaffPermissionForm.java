package cn.huancai.erp.module.staff.dto;

import lombok.Data;

import java.util.List;

/** 组织架构页给员工分配菜单权限的表单。 */
@Data
public class StaffPermissionForm {

    /** 可见菜单路径；空数组表示仅工作台。 */
    private List<String> permissions;
}
