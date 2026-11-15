package cn.huancai.erp.module.homeservice.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.homeservice.dto.PosterCreateForm;
import cn.huancai.erp.module.homeservice.service.ActivityPosterService;
import cn.huancai.erp.module.homeservice.vo.MessageReceiverVO;
import cn.huancai.erp.module.homeservice.vo.PosterImageVO;
import cn.huancai.erp.module.homeservice.vo.PosterVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** 家校服务-活动海报推送：上传海报、按年级/班级/学员定向推送并查看阅读情况。 */
@RestController
@RequestMapping("/api/posters")
public class ActivityPosterController {

    private final ActivityPosterService posterService;

    public ActivityPosterController(ActivityPosterService posterService) {
        this.posterService = posterService;
    }

    @GetMapping
    public ApiResult<IPage<PosterVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(posterService.list(keyword, start, end, page, size));
    }

    @GetMapping("/{id}/images")
    public ApiResult<List<PosterImageVO>> images(@PathVariable Long id) {
        return ApiResult.ok(posterService.images(id));
    }

    @PostMapping("/images")
    public ApiResult<PosterImageVO> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ApiResult.ok(posterService.upload(file));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @PostMapping
    public ApiResult<PosterVO> create(@RequestBody PosterCreateForm form) {
        try {
            return ApiResult.ok(posterService.create(form));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/receivers")
    public ApiResult<IPage<MessageReceiverVO>> receivers(
            @PathVariable Long id,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(posterService.receivers(id, readFlag, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        if (!posterService.delete(id)) {
            return ApiResult.fail("推送记录不存在或已被删除");
        }
        return ApiResult.ok(null);
    }
}
