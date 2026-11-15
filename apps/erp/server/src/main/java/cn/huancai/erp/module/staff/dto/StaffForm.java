package cn.huancai.erp.module.staff.dto;

import lombok.Data;

import java.util.List;

/** 新建 / 编辑员工表单。 */
@Data
public class StaffForm {

    private String name;
    private String phone;
    private String account;
    private String password;
    private String role;
    /** 兼任角色，不能与主角色重复，也不能是 admin。 */
    private List<String> extraRoles;
    private String title;
    /** 可见菜单路径；不填表示仅工作台。 */
    private List<String> permissions;
    private Long campusId;
    private Integer status;
}
