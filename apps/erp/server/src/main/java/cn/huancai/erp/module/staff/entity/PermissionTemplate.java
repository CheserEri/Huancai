package cn.huancai.erp.module.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("permission_template")
public class PermissionTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 适用角色，空表示通用；内置模板与角色一一对应。 */
    private String role;
    /** 1 内置角色模板（不可删除），0 自定义模板。 */
    private Integer builtin;
    /** 可见菜单路径 JSON 数组。 */
    private String permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
