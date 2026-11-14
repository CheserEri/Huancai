package cn.huancai.erp.module.staff.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class StaffVO {

    private Long id;
    private String name;
    private String phone;
    private String account;
    private String role;
    private String title;
    private Integer status;
    private Long campusId;
    private String campusName;
    private Long classCount;
    private Long scheduleCount;
    private Long commentCount;
    /** 数据库原始值，仅用于转换成 permissions 后返回前端。 */
    @JsonIgnore
    private String menuPermissions;
    /** 最终可见菜单路径，admin 返回 ["*"]。 */
    private List<String> permissions;
}
