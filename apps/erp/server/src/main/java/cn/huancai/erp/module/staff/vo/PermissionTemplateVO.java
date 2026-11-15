package cn.huancai.erp.module.staff.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PermissionTemplateVO {

    private Long id;
    private String name;
    /** 适用角色，空表示通用。 */
    private String role;
    /** 1 内置角色模板，0 自定义模板。 */
    private Integer builtin;
    /** 可见菜单路径。 */
    private List<String> permissions;
    private LocalDateTime updatedAt;
}
