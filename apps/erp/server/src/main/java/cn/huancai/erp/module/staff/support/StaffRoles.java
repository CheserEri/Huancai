package cn.huancai.erp.module.staff.support;

import java.util.Arrays;
import java.util.List;

/** 员工角色字典：与前端角色选项保持一致。 */
public final class StaffRoles {

    /** 顺序即员工页角色下拉的展示顺序。 */
    public static final List<String> ALL = List.of(
            "admin", "manager", "finance", "reception", "consultant", "teacher", "assistant");

    private StaffRoles() {
    }

    public static boolean isValid(String role) {
        return role != null && ALL.contains(role);
    }

    /** 解析数据库中的兼任角色字段。 */
    public static List<String> splitExtras(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .distinct()
                .toList();
    }

    /** 兼任角色入库前统一去重、去主角色、去 admin，无有效值时存 null。 */
    public static String encodeExtras(String primaryRole, List<String> extras) {
        if (extras == null || extras.isEmpty()) {
            return null;
        }
        List<String> valid = extras.stream()
                .filter(StaffRoles::isValid)
                .filter(role -> !"admin".equals(role))
                .filter(role -> !role.equals(primaryRole))
                .distinct()
                .toList();
        return valid.isEmpty() ? null : String.join(",", valid);
    }
}
