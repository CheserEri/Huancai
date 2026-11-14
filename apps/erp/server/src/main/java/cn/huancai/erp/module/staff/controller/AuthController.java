package cn.huancai.erp.module.staff.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.entity.StaffSession;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.mapper.StaffSessionMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int SESSION_DAYS = 7;
    private static final String BEARER_PREFIX = "Bearer ";

    private final StaffMapper staffMapper;
    private final StaffSessionMapper sessionMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(StaffMapper staffMapper, StaffSessionMapper sessionMapper) {
        this.staffMapper = staffMapper;
        this.sessionMapper = sessionMapper;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isEmpty()) {
            return ApiResult.fail("请输入账号和密码");
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .and(w -> w.eq(Staff::getPhone, request.account())
                        .or()
                        .eq(Staff::getAccount, request.account()))
                .eq(Staff::getStatus, 1)
                .last("LIMIT 1"));
        if (staff == null || staff.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), staff.getPasswordHash())) {
            return ApiResult.fail("账号或密码不正确，请重新输入");
        }

        sessionMapper.delete(new LambdaQueryWrapper<StaffSession>()
                .lt(StaffSession::getExpiresAt, LocalDateTime.now()));
        StaffSession session = new StaffSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setStaffId(staff.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessionMapper.insert(session);

        Map<String, Object> data = buildProfile(staff);
        data.put("token", session.getToken());
        return ApiResult.ok(data);
    }

    /** 刷新当前登录信息与最新菜单权限，供前端进入系统时同步。 */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(HttpServletRequest request) {
        Long staffId = (Long) request.getAttribute(AuthInterceptor.ATTR_STAFF_ID);
        Staff staff = staffId == null ? null : staffMapper.selectById(staffId);
        if (staff == null) {
            return ApiResult.fail("员工不存在");
        }
        return ApiResult.ok(buildProfile(staff));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                sessionMapper.deleteById(token);
            }
        }
        return ApiResult.ok(null);
    }

    private Map<String, Object> buildProfile(Staff staff) {
        Map<String, Object> data = new HashMap<>();
        data.put("staffId", staff.getId());
        data.put("name", staff.getName());
        data.put("role", staff.getRole());
        data.put("permissions", PermissionCodec.resolve(staff.getRole(), staff.getMenuPermissions()));
        if (staff.getCampusId() != null) {
            data.put("campusId", staff.getCampusId());
        }
        return data;
    }

    public record LoginRequest(String account, String password) {
    }
}
