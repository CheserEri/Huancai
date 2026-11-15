package cn.huancai.erp.module.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("staff")
public class Staff {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private String name;
    private String phone;
    private String account;
    private String passwordHash;
    private String role;
    /** 兼任角色，逗号分隔；不参与登录与权限判定，仅用于展示和统计。 */
    private String extraRoles;
    private String title;
    /** 可见菜单路径 JSON 数组；为空表示仅工作台，admin 角色不受此限制。 */
    private String menuPermissions;
    private Integer status;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
