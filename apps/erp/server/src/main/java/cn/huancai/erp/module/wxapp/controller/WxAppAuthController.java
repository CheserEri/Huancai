package cn.huancai.erp.module.wxapp.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.WxParentAuthInterceptor;
import cn.huancai.erp.module.wxapp.service.WxAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 家长端小程序：登录、资料、绑定孩子。 */
@RestController
@RequestMapping("/api/wxapp")
public class WxAppAuthController {

    private final WxAppService wxAppService;

    public WxAppAuthController(WxAppService wxAppService) {
        this.wxAppService = wxAppService;
    }

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            return ApiResult.ok(wxAppService.login(request.code(), request.devOpenid(),
                    request.nickname(), request.avatarUrl()));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiResult.ok(wxAppService.profile(parentId(request)));
    }

    @PutMapping("/profile")
    public ApiResult<Map<String, Object>> updateProfile(HttpServletRequest request,
                                                        @RequestBody ProfileRequest body) {
        return ApiResult.ok(wxAppService.updateProfile(parentId(request), body.nickname(), body.avatarUrl()));
    }

    @PostMapping("/bind")
    public ApiResult<Map<String, Object>> bind(HttpServletRequest request, @RequestBody BindRequest body) {
        try {
            return ApiResult.ok(wxAppService.bind(parentId(request), body.name(), body.phone()));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PostMapping("/unbind")
    public ApiResult<Map<String, Object>> unbind(HttpServletRequest request,
                                                 @RequestParam Long studentId) {
        return ApiResult.ok(wxAppService.unbind(parentId(request), studentId));
    }

    private Long parentId(HttpServletRequest request) {
        return (Long) request.getAttribute(WxParentAuthInterceptor.ATTR_PARENT_ID);
    }

    public record LoginRequest(String code, String devOpenid, String nickname, String avatarUrl) {
    }

    public record ProfileRequest(String nickname, String avatarUrl) {
    }

    public record BindRequest(String name, String phone) {
    }
}
