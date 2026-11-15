package cn.huancai.erp.module.wxapp.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.WxParentAuthInterceptor;
import cn.huancai.erp.module.homeservice.vo.PosterImageVO;
import cn.huancai.erp.module.homeservice.vo.WxParentPosterVO;
import cn.huancai.erp.module.wxapp.mapper.WxAppContentMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 家长端小程序：活动海报（员工端「家校服务-活动海报推送」按年级/班级/学员推送）。 */
@RestController
@RequestMapping("/api/wxapp/posters")
public class WxAppPosterController {

    private final WxAppContentMapper contentMapper;

    public WxAppPosterController(WxAppContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    @GetMapping
    public ApiResult<List<WxParentPosterVO>> list(HttpServletRequest request) {
        Long parentId = parentId(request);
        contentMapper.syncParentReceivers(parentId);
        List<WxParentPosterVO> posters = contentMapper.selectParentPosters(parentId);
        if (posters.isEmpty()) {
            return ApiResult.ok(posters);
        }
        List<Long> posterIds = new ArrayList<>();
        for (WxParentPosterVO poster : posters) {
            posterIds.add(poster.getId());
        }
        Map<Long, List<PosterImageVO>> imagesByPoster = new HashMap<>();
        for (PosterImageVO image : contentMapper.selectPosterImagesByPosters(posterIds)) {
            imagesByPoster.computeIfAbsent(image.getPosterId(), key -> new ArrayList<>()).add(image);
        }
        for (WxParentPosterVO poster : posters) {
            poster.setImages(imagesByPoster.getOrDefault(poster.getId(), List.of()));
        }
        return ApiResult.ok(posters);
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> read(HttpServletRequest request, @PathVariable Long id) {
        Long parentId = parentId(request);
        contentMapper.syncParentReceivers(parentId);
        contentMapper.markPosterRead(id, parentId);
        return ApiResult.ok(null);
    }

    private Long parentId(HttpServletRequest request) {
        Object value = request.getAttribute(WxParentAuthInterceptor.ATTR_PARENT_ID);
        return value instanceof Long id ? id : null;
    }
}
