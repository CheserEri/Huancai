package cn.huancai.erp.config;

import cn.huancai.erp.common.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 接口级权限校验：按“接口前缀 → 可见菜单路径”的映射，拦截无对应菜单权限的账号。
 * <ul>
 *   <li>管理员（permissions 含 *）放行全部接口；</li>
 *   <li>登录、校区等共享接口对所有登录账号放行；</li>
 *   <li>学生 / 班级 / 排课读取接口为跨页面共用（排课要取学员、班级下拉），读取放行，写操作按菜单权限校验；</li>
 *   <li>其余业务接口读写均需对应菜单权限。</li>
 * </ul>
 */
@Component
public class ApiPermissionInterceptor implements HandlerInterceptor {

    private static final Set<String> REPORTER_PATHS = Set.of(
            "/reporter/business", "/reporter/teaching", "/reporter/finance",
            "/reporter/material", "/reporter/screen", "/reporter/ai");

    private static final Set<String> MARKETING_PATHS = Set.of("/marketingSolution");

    /** 所有登录账号都可访问的共享接口。 */
    private static final List<String> SHARED_PREFIXES = List.of(
            "/api/auth", "/api/campus", "/api/health", "/api/dashboard", "/api/staff/options");

    private static final List<Rule> RULES = List.of(
            new Rule("/api/finance", Set.of("/finance"), false),
            new Rule("/api/reports", REPORTER_PATHS, false),
            new Rule("/api/orders", Set.of("/order"), false),
            new Rule("/api/local-orders", Set.of("/order", "/student"), false),
            new Rule("/api/sp-orders", Set.of("/order"), false),
            new Rule("/api/print", Set.of("/order"), false),
            new Rule("/api/materials", Set.of("/storage", "/order", "/student"), false),
            new Rule("/api/data-fees", Set.of("/dataFeeTemplate", "/student", "/order"), false),
            new Rule("/api/points-goods", Set.of("/pointsMall", "/order", "/student"), false),
            new Rule("/api/staff", Set.of("/employee", "/orgStructure"), false),
            new Rule("/api/permission-templates", Set.of("/orgStructure"), false),
            new Rule("/api/comments", Set.of("/comment", "/growthArchives"), false),
            new Rule("/api/attendance", Set.of("/teachingLog", "/attendance"), false),
            new Rule("/api/courses", Set.of("/lesson"), false),
            new Rule("/api/consult", Set.of("/crmConsult"), false),
            new Rule("/api/marketing", MARKETING_PATHS, false),
            new Rule("/api/activity-templates", Set.of("/priceTemplate"), false),
            // 家校服务：消息推送 / 班级休课 / 课堂点评 / 作业 / 班级相册
            new Rule("/api/messages", Set.of("/messagePush"), false),
            new Rule("/api/posters", Set.of("/activityPoster"), false),
            new Rule("/api/class-suspends", Set.of("/classSuspend"), false),
            new Rule("/api/class-comments", Set.of("/classComment"), false),
            // 作业读取接口同时供班级详情页签使用，仅校验写操作
            new Rule("/api/homeworks", Set.of("/homework"), true),
            new Rule("/api/class-album", Set.of("/classAlbum"), false),
            // 成绩读取接口同时供学员详情、班级详情页签使用，仅校验写操作
            new Rule("/api/grades", Set.of("/gradeExam", "/gradeEntry", "/gradeQuery",
                    "/gradeAnalysis", "/gradeSetting"), true),
            // 以下三类读取接口被多个页面共用，仅校验写操作
            new Rule("/api/students", Set.of("/student"), true),
            new Rule("/api/classes", Set.of("/class"), true),
            // 教师课时：办理中心子菜单
            new Rule("/api/teacher-hours", Set.of("/teacherHours"), false),
            new Rule("/api/schedules", Set.of("/schedule"), true));

    private final ObjectMapper objectMapper;

    public ApiPermissionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Object permissionsAttr = request.getAttribute(AuthInterceptor.ATTR_PERMISSIONS);
        List<?> permissions = permissionsAttr instanceof List<?> list ? list : List.of();
        if (permissions.contains("*")) {
            return true;
        }
        String path = request.getRequestURI();
        if (SHARED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        Rule rule = findRule(path);
        if (rule == null) {
            return true;
        }
        if (rule.writeOnly() && isReadMethod(request.getMethod())) {
            return true;
        }
        if (rule.permissions().stream().anyMatch(permissions::contains)) {
            return true;
        }
        return reject(response);
    }

    private Rule findRule(String path) {
        for (Rule rule : RULES) {
            if (path.equals(rule.prefix()) || path.startsWith(rule.prefix() + "/")) {
                return rule;
            }
        }
        return null;
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiResult<>(403, "当前账号无权访问该功能，请联系管理员分配菜单权限", null)));
        return false;
    }

    private record Rule(String prefix, Set<String> permissions, boolean writeOnly) {
    }
}
