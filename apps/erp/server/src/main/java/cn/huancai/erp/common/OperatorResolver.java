package cn.huancai.erp.common;

import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 解析本次请求的操作人：未手动选择经办人时，默认取当前登录账号。 */
@Component
public class OperatorResolver {

    private final StaffMapper staffMapper;

    public OperatorResolver(StaffMapper staffMapper) {
        this.staffMapper = staffMapper;
    }

    /** 当前登录账号姓名；非 Web 请求或取不到时返回 null。 */
    public String currentName() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object staffId = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_STAFF_ID);
        if (!(staffId instanceof Long id)) {
            return null;
        }
        Staff staff = staffMapper.selectById(id);
        return staff == null ? null : staff.getName();
    }

    /** 当前登录账号 ID；非 Web 请求或取不到时返回 null。 */
    public Long currentStaffId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object staffId = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_STAFF_ID);
        return staffId instanceof Long id ? id : null;
    }

    /** 是否可修改退费课时 / 金额：管理员、财务，或被单独分配操作权限的员工。 */
    public boolean canEditRefundAmounts() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        Object role = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_ROLE);
        if ("admin".equals(role) || "finance".equals(role)) {
            return true;
        }
        return hasPermission("/student/refund-edit");
    }

    /** 是否拥有指定操作权限：管理员与含 * 的账号默认全部放行。 */
    public boolean hasPermission(String code) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        Object role = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_ROLE);
        if ("admin".equals(role)) {
            return true;
        }
        Object permissions = attributes.getRequest().getAttribute(AuthInterceptor.ATTR_PERMISSIONS);
        if (permissions instanceof java.util.Collection<?> values) {
            return values.contains("*") || values.contains(code);
        }
        return false;
    }

    /** 是否拥有任意一项指定操作权限。 */
    public boolean hasAnyPermission(String... codes) {
        for (String code : codes) {
            if (hasPermission(code)) {
                return true;
            }
        }
        return false;
    }

    /** 优先使用手动选择的经办人，否则用当前登录账号。 */
    public String resolve(String selected) {
        if (selected != null && !selected.isBlank()) {
            return selected.trim();
        }
        String current = currentName();
        return current == null || current.isBlank() ? "系统" : current;
    }
}
