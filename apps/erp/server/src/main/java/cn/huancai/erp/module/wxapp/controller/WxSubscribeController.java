package cn.huancai.erp.module.wxapp.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.WxParentAuthInterceptor;
import cn.huancai.erp.module.wxapp.service.WxSubscribeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 家长端小程序：订阅消息（用户授权上报与剩余次数）。 */
@RestController
@RequestMapping("/api/wxapp/subscribe")
public class WxSubscribeController {

    private final WxSubscribeService subscribeService;

    public WxSubscribeController(WxSubscribeService subscribeService) {
        this.subscribeService = subscribeService;
    }

    /** 小程序端 wx.requestSubscribeMessage 授权成功后调用，记录一次可发送额度。 */
    @PostMapping
    public ApiResult<Map<String, Object>> grant(HttpServletRequest request) {
        Long parentId = parentId(request);
        int remain = subscribeService.grantActivity(parentId);
        return ApiResult.ok(Map.of("remainCount", remain));
    }

    @GetMapping
    public ApiResult<Map<String, Object>> remain(HttpServletRequest request) {
        int remain = subscribeService.remainActivity(parentId(request));
        return ApiResult.ok(Map.of("remainCount", remain));
    }

    private Long parentId(HttpServletRequest request) {
        Object value = request.getAttribute(WxParentAuthInterceptor.ATTR_PARENT_ID);
        return value instanceof Long id ? id : null;
    }
}
