package cn.huancai.erp.config;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.wxapp.entity.WxParentSession;
import cn.huancai.erp.module.wxapp.mapper.WxParentSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;

/** 家长端小程序鉴权，与员工端 staff_session 完全隔离。 */
@Component
public class WxParentAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_PARENT_ID = "wxParentId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WxParentSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public WxParentAuthInterceptor(WxParentSessionMapper sessionMapper, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            return reject(response, "请先登录");
        }
        WxParentSession session = sessionMapper.selectById(token);
        if (session == null) {
            return reject(response, "登录已失效，请重新登录");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionMapper.deleteById(token);
            return reject(response, "登录已过期，请重新登录");
        }
        request.setAttribute(ATTR_PARENT_ID, session.getParentId());
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
