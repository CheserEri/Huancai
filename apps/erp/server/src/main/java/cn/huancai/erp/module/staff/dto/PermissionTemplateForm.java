package cn.huancai.erp.module.staff.dto;

import lombok.Data;

import java.util.List;

/** 权限模板新建 / 编辑表单。 */
@Data
public class PermissionTemplateForm {

    private String name;
    /** 适用角色，留空表示通用模板。 */
    private String role;
    /** 可见菜单路径；空数组表示仅工作台。 */
    private List<String> permissions;
}
