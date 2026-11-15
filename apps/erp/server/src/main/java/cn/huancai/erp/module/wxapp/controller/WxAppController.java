package cn.huancai.erp.module.wxapp.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.WxParentAuthInterceptor;
import cn.huancai.erp.module.wxapp.service.WxAppService;
import cn.huancai.erp.module.wxapp.vo.WxActivityVO;
import cn.huancai.erp.module.wxapp.vo.WxAlbumVO;
import cn.huancai.erp.module.wxapp.vo.WxChildVO;
import cn.huancai.erp.module.wxapp.vo.WxGradeVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 家长端小程序：课时、课堂表现、成绩、活动。 */
@RestController
@RequestMapping("/api/wxapp")
public class WxAppController {

    private final WxAppService wxAppService;

    public WxAppController(WxAppService wxAppService) {
        this.wxAppService = wxAppService;
    }

    @GetMapping("/children")
    public ApiResult<List<WxChildVO>> children(HttpServletRequest request) {
        return ApiResult.ok(wxAppService.children(parentId(request)));
    }

    @GetMapping("/home")
    public ApiResult<Map<String, Object>> home(HttpServletRequest request, @RequestParam Long studentId) {
        try {
            return ApiResult.ok(wxAppService.home(parentId(request), studentId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/lessons")
    public ApiResult<Map<String, Object>> lessons(HttpServletRequest request, @RequestParam Long studentId) {
        try {
            return ApiResult.ok(wxAppService.lessons(parentId(request), studentId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/comments")
    public ApiResult<Map<String, Object>> comments(HttpServletRequest request,
                                                   @RequestParam Long studentId,
                                                   @RequestParam(defaultValue = "1") int page) {
        try {
            return ApiResult.ok(wxAppService.comments(parentId(request), studentId, page));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PostMapping("/comments/{id}/read")
    public ApiResult<Void> readComment(HttpServletRequest request,
                                       @PathVariable Long id,
                                       @RequestParam Long studentId) {
        try {
            wxAppService.markCommentRead(parentId(request), studentId, id);
            return ApiResult.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/grades")
    public ApiResult<List<WxGradeVO>> grades(HttpServletRequest request, @RequestParam Long studentId) {
        try {
            return ApiResult.ok(wxAppService.grades(parentId(request), studentId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/activities")
    public ApiResult<List<WxActivityVO>> activities() {
        return ApiResult.ok(wxAppService.activities());
    }

    /** 班级相册：家长只能看到已绑定学员所在班级的照片。 */
    @GetMapping("/albums")
    public ApiResult<List<WxAlbumVO>> albums(HttpServletRequest request, @RequestParam Long studentId) {
        try {
            return ApiResult.ok(wxAppService.albums(parentId(request), studentId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    private Long parentId(HttpServletRequest request) {
        return (Long) request.getAttribute(WxParentAuthInterceptor.ATTR_PARENT_ID);
    }
}
