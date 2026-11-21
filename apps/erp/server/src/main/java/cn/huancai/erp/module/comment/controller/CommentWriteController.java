package cn.huancai.erp.module.comment.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.comment.dto.ClassLessonCommentForm;
import cn.huancai.erp.module.comment.dto.CommentBatchForm;
import cn.huancai.erp.module.comment.service.CommentWriteService;
import cn.huancai.erp.module.comment.vo.ClassCommentRowVO;
import cn.huancai.erp.module.comment.vo.ClassLessonCommentVO;
import cn.huancai.erp.module.comment.vo.CommentRosterVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 家校服务-课堂点评：按班级课次批量录入点评。 */
@RestController
@RequestMapping("/api/class-comments")
public class CommentWriteController {

    private final CommentWriteService commentWriteService;

    public CommentWriteController(CommentWriteService commentWriteService) {
        this.commentWriteService = commentWriteService;
    }

    @GetMapping
    public ApiResult<List<ClassCommentRowVO>> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        try {
            return ApiResult.ok(commentWriteService.list(classId, start, end));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/roster")
    public ApiResult<List<CommentRosterVO>> roster(@RequestParam Long classId,
                                                   @RequestParam Long scheduleId) {
        try {
            return ApiResult.ok(commentWriteService.roster(classId, scheduleId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResult<Integer> batch(@RequestBody CommentBatchForm form) {
        try {
            return ApiResult.ok(commentWriteService.saveBatch(form));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    /** 整节课的整体点评：对全班统一评价，与学员逐人点评分开保存。 */
    @GetMapping("/overall")
    public ApiResult<ClassLessonCommentVO> overall(@RequestParam Long classId,
                                                   @RequestParam Long scheduleId) {
        try {
            return ApiResult.ok(commentWriteService.overall(classId, scheduleId));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PostMapping("/overall")
    public ApiResult<ClassLessonCommentVO> saveOverall(@RequestBody ClassLessonCommentForm form) {
        try {
            return ApiResult.ok(commentWriteService.saveOverall(form));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }
}
