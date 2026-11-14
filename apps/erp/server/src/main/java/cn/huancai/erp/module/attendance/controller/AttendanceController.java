package cn.huancai.erp.module.attendance.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.attendance.mapper.AttendanceMapper;
import cn.huancai.erp.module.attendance.vo.AttendanceSummaryVO;
import cn.huancai.erp.module.attendance.vo.AttendanceVO;
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
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceMapper attendanceMapper;

    public AttendanceController(AttendanceMapper attendanceMapper) {
        this.attendanceMapper = attendanceMapper;
    }

    @GetMapping
    public ApiResult<IPage<AttendanceVO>> list(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<AttendanceVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return ApiResult.ok(attendanceMapper.selectAttendancePage(p, campusId, keyword, teacher, status, start, end));
    }

    @GetMapping("/summary")
    public ApiResult<AttendanceSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(attendanceMapper.selectSummary(campusId, keyword, teacher, status, start, end));
    }

    @GetMapping("/teachers")
    public ApiResult<List<String>> teachers() {
        return ApiResult.ok(attendanceMapper.selectTeachers());
    }
}
