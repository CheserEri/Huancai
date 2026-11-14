package cn.huancai.erp.module.wxapp.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.WxParentAuthInterceptor;
import cn.huancai.erp.module.wxapp.mapper.WxAppContentMapper;
import cn.huancai.erp.module.wxapp.vo.WxParentMessageVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 家长端小程序：消息通知（员工端「家校服务-消息推送」发出的消息，含休课通知）。 */
@RestController
@RequestMapping("/api/wxapp/messages")
public class WxMessageController {

    private final WxAppContentMapper contentMapper;

    public WxMessageController(WxAppContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    @GetMapping
    public ApiResult<List<WxParentMessageVO>> list(HttpServletRequest request,
                                                   @RequestParam(required = false) Long studentId) {
        return ApiResult.ok(contentMapper.selectParentMessages(parentId(request), studentId));
    }

    @GetMapping("/unread-count")
    public ApiResult<Integer> unreadCount(HttpServletRequest request,
                                          @RequestParam(required = false) Long studentId) {
        return ApiResult.ok(contentMapper.countUnreadMessages(parentId(request), studentId));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> read(HttpServletRequest request, @PathVariable Long id) {
        contentMapper.markMessageRead(id, parentId(request));
        return ApiResult.ok(null);
    }

    private Long parentId(HttpServletRequest request) {
        Object value = request.getAttribute(WxParentAuthInterceptor.ATTR_PARENT_ID);
        return value instanceof Long id ? id : null;
    }
}
