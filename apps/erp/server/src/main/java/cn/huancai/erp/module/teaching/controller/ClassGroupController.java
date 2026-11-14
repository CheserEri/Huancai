package cn.huancai.erp.module.teaching.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.vo.ClassAttendanceVO;
import cn.huancai.erp.module.teaching.vo.ClassCommentItemVO;
import cn.huancai.erp.module.teaching.vo.ClassScheduleVO;
import cn.huancai.erp.module.teaching.vo.ClassStudentVO;
import cn.huancai.erp.module.teaching.vo.ClassSummaryVO;
import cn.huancai.erp.module.teaching.vo.ClassVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/classes")
public class ClassGroupController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "enrolledCount", "startDate");

    private final ClassGroupMapper classGroupMapper;

    public ClassGroupController(ClassGroupMapper classGroupMapper) {
        this.classGroupMapper = classGroupMapper;
    }

    @GetMapping
    public ApiResult<IPage<ClassVO>> list(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer enrolling,
            @RequestParam(required = false) String lessonClass,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String courseCategory,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String weekday,
            @RequestParam(required = false) String timePeriod,
            @RequestParam(required = false) Boolean includeClosed,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ClassVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        String field = sortField != null && SORT_FIELDS.contains(sortField) ? sortField : null;
        String order = "descend".equals(sortOrder) || "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return ApiResult.ok(classGroupMapper.selectClassPage(
                p, keyword, campusId, teacher, status, source, enrolling, lessonClass,
                room, courseCategory, courseName, weekday, timePeriod, includeClosed,
                field, order));
    }

    @GetMapping("/summary")
    public ApiResult<ClassSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer enrolling,
            @RequestParam(required = false) String lessonClass,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String courseCategory,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String weekday,
            @RequestParam(required = false) String timePeriod,
            @RequestParam(required = false) Boolean includeClosed) {
        ClassSummaryVO summary = classGroupMapper.selectSummary(
                keyword, campusId, teacher, status, source, enrolling, lessonClass,
                room, courseCategory, courseName, weekday, timePeriod, includeClosed);
        ClassSummaryVO students = classGroupMapper.selectStudentSummary(
                keyword, campusId, teacher, status, source, enrolling, lessonClass,
                room, courseCategory, courseName, weekday, timePeriod, includeClosed);
        if (summary == null) {
            summary = new ClassSummaryVO();
        }
        if (students != null) {
            summary.setStudentCount(students.getStudentCount());
            summary.setMemberCount(students.getMemberCount());
        }
        return ApiResult.ok(summary);
    }

    @GetMapping("/teachers")
    public ApiResult<List<String>> teachers() {
        return ApiResult.ok(classGroupMapper.selectTeachers());
    }

    @GetMapping("/lesson-classes")
    public ApiResult<List<String>> lessonClasses() {
        return ApiResult.ok(classGroupMapper.selectLessonClasses());
    }

    @GetMapping("/courses")
    public ApiResult<List<String>> courses() {
        return ApiResult.ok(classGroupMapper.selectCourseNames());
    }

    @GetMapping("/categories")
    public ApiResult<List<String>> categories() {
        return ApiResult.ok(classGroupMapper.selectCategories());
    }

    @GetMapping("/rooms")
    public ApiResult<List<String>> rooms() {
        return ApiResult.ok(classGroupMapper.selectRooms());
    }

    @GetMapping("/{classId}/students")
    public ApiResult<List<ClassStudentVO>> students(@PathVariable Long classId) {
        return ApiResult.ok(classGroupMapper.selectClassStudents(classId));
    }

    @GetMapping("/{classId}/schedules")
    public ApiResult<List<ClassScheduleVO>> schedules(
            @PathVariable Long classId,
            @RequestParam(required = false) Boolean doneOnly) {
        return ApiResult.ok(classGroupMapper.selectClassSchedules(classId, doneOnly));
    }

    @GetMapping("/{classId}/attendance")
    public ApiResult<List<ClassAttendanceVO>> attendance(
            @PathVariable Long classId,
            @RequestParam(required = false) Long scheduleId) {
        return ApiResult.ok(classGroupMapper.selectClassAttendance(classId, scheduleId));
    }

    @GetMapping("/{classId}/comments")
    public ApiResult<List<ClassCommentItemVO>> comments(@PathVariable Long classId) {
        return ApiResult.ok(classGroupMapper.selectClassComments(classId));
    }

    @GetMapping("/{classId}")
    public ApiResult<ClassVO> detail(@PathVariable Long classId) {
        ClassVO vo = classGroupMapper.selectClassById(classId);
        if (vo == null) {
            return ApiResult.fail("班级不存在");
        }
        vo.setAttendanceRate(classGroupMapper.selectAttendanceRate(classId));
        return ApiResult.ok(vo);
    }
}
