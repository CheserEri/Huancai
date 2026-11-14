package cn.huancai.erp.config;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.entity.StaffSession;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.mapper.StaffSessionMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;

/** 接口鉴权：除登录接口外，所有 /api 请求都必须携带有效的登录令牌。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_STAFF_ID = "staffId";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_PERMISSIONS = "permissions";
    private static final String BEARER_PREFIX = "Bearer ";

    private final StaffSessionMapper sessionMapper;
    private final StaffMapper staffMapper;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(StaffSessionMapper sessionMapper, StaffMapper staffMapper, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.staffMapper = staffMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 跨域预检请求不携带业务令牌，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            return reject(response, "请先登录");
        }
        StaffSession session = sessionMapper.selectById(token);
        if (session == null) {
            return reject(response, "登录已失效，请重新登录");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionMapper.deleteById(token);
            return reject(response, "登录已过期，请重新登录");
        }
        Staff staff = staffMapper.selectById(session.getStaffId());
        if (staff == null || staff.getStatus() == null || staff.getStatus() != 1) {
            sessionMapper.deleteById(token);
            return reject(response, "账号已停用，请联系管理员");
        }
        request.setAttribute(ATTR_STAFF_ID, staff.getId());
        request.setAttribute(ATTR_ROLE, staff.getRole());
        request.setAttribute(ATTR_PERMISSIONS,
                PermissionCodec.resolve(staff.getRole(), staff.getMenuPermissions()));
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResult<>(401, message, null)));
        return false;
    }
}
