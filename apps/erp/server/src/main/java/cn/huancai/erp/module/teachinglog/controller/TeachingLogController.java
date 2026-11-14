package cn.huancai.erp.module.teachinglog.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.teachinglog.dto.TeachingLogRequests;
import cn.huancai.erp.module.teachinglog.service.TeachingLogService;
import cn.huancai.erp.module.teachinglog.vo.TeachingLogRowVO;
import cn.huancai.erp.module.teachinglog.vo.TeachingLogStudentVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teaching-logs")
public class TeachingLogController {

    private final TeachingLogService teachingLogService;

    public TeachingLogController(TeachingLogService teachingLogService) {
        this.teachingLogService = teachingLogService;
    }

    @GetMapping
    public ApiResult<List<TeachingLogRowVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Boolean supplement,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) Integer lessonMode,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResult.ok(teachingLogService.list(date, supplement, campusId, room,
                lessonMode, courseId, teacher, keyword, status));
    }

    @GetMapping("/{scheduleId}/roster")
    public ApiResult<List<TeachingLogStudentVO>> roster(@PathVariable Long scheduleId) {
        return ApiResult.ok(teachingLogService.roster(scheduleId));
    }

    @PostMapping("/{scheduleId}")
    public ApiResult<Map<String, Object>> save(@PathVariable Long scheduleId,
                                               @RequestBody TeachingLogRequests.Save request) {
        return ApiResult.ok(teachingLogService.save(scheduleId, request));
    }
}
