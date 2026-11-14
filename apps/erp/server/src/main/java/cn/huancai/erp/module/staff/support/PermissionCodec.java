package cn.huancai.erp.module.staff.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 员工菜单权限的序列化与解析。权限以菜单路径数组存储，admin 角色固定拥有全部权限。 */
public final class PermissionCodec {

    public static final String ALL = "*";
    /** 工作台是登录后的默认页面，始终可用，不纳入权限配置。 */
    public static final String DESKTOP = "/desktop";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PATH_LENGTH = 80;

    private PermissionCodec() {
    }

    public static List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return sanitize(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String encode(List<String> permissions) {
        List<String> clean = sanitize(permissions);
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(clean);
        } catch (Exception e) {
            return null;
        }
    }

    /** 登录 / 接口返回使用的最终权限：admin 为全部，其余按存储值。 */
    public static List<String> resolve(String role, String json) {
        if ("admin".equals(role)) {
            return List.of(ALL);
        }
        return decode(json);
    }

    private static List<String> sanitize(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : permissions) {
            if (item == null) {
                continue;
            }
            String path = item.trim();
            if (path.isEmpty() || path.length() > MAX_PATH_LENGTH || !path.startsWith("/") || ALL.equals(path)) {
                continue;
            }
            if (DESKTOP.equals(path)) {
                continue;
            }
            set.add(path);
        }
        return new ArrayList<>(set);
    }
}
