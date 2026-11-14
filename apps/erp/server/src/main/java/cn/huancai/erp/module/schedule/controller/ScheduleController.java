package cn.huancai.erp.module.schedule.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.schedule.vo.ScheduleOptionVO;
import cn.huancai.erp.module.schedule.vo.ScheduleSummaryVO;
import cn.huancai.erp.module.schedule.vo.ScheduleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleMapper scheduleMapper;

    public ScheduleController(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    @GetMapping
    public ApiResult<IPage<ScheduleVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer lessonMode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "200") long size) {
        long safeSize = Math.min(Math.max(size, 1), 1000);
        Page<ScheduleVO> p = new Page<>(Math.max(page, 1), safeSize);
        return ApiResult.ok(scheduleMapper.selectSchedulePage(p, start, end, campusId, teacher,
                classId, courseId, room, keyword, status, lessonMode));
    }

    @GetMapping("/summary")
    public ApiResult<ScheduleSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer lessonMode) {
        return ApiResult.ok(scheduleMapper.selectSummary(start, end, campusId, teacher,
                classId, courseId, room, keyword, status, lessonMode));
    }

    @GetMapping("/teachers")
    public ApiResult<List<String>> teachers() {
        return ApiResult.ok(scheduleMapper.selectTeachers());
    }

    @GetMapping("/classes")
    public ApiResult<List<ScheduleOptionVO>> classes() {
        return ApiResult.ok(scheduleMapper.selectClassOptions());
    }

    @GetMapping("/courses")
    public ApiResult<List<ScheduleOptionVO>> courses() {
        return ApiResult.ok(scheduleMapper.selectCourseOptions());
    }

    @GetMapping("/rooms")
    public ApiResult<List<String>> rooms() {
        return ApiResult.ok(scheduleMapper.selectRooms());
    }
}
