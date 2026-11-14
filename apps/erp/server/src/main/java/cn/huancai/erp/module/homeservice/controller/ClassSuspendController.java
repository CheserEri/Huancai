package cn.huancai.erp.module.homeservice.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.homeservice.dto.SuspendBatchForm;
import cn.huancai.erp.module.homeservice.service.ClassSuspendService;
import cn.huancai.erp.module.homeservice.vo.SuspendCandidateVO;
import cn.huancai.erp.module.homeservice.vo.SuspendVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 家校服务-班级休课：按时间段 + 班级批量休课、恢复，并向家长推送休课通知。 */
@RestController
@RequestMapping("/api/class-suspends")
public class ClassSuspendController {

    private final ClassSuspendService suspendService;

    public ClassSuspendController(ClassSuspendService suspendService) {
        this.suspendService = suspendService;
    }

    @GetMapping("/candidates")
    public ApiResult<List<SuspendCandidateVO>> candidates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam String classIds,
            @RequestParam(required = false) String keyword) {
        try {
            return ApiResult.ok(suspendService.candidates(start, end, parseIds(classIds), keyword));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResult<Map<String, Object>> batch(@RequestBody SuspendBatchForm form) {
        try {
            return ApiResult.ok(suspendService.suspend(form));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping
    public ApiResult<IPage<SuspendVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(suspendService.list(start, end, classId, status, page, size));
    }

    @PostMapping("/{id}/restore")
    public ApiResult<Void> restore(@PathVariable Long id) {
        try {
            suspendService.restore(id);
            return ApiResult.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    private List<Long> parseIds(String classIds) {
        return Arrays.stream(classIds.split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
