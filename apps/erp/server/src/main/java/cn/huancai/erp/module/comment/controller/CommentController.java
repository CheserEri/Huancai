package cn.huancai.erp.module.comment.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.comment.mapper.CommentMapper;
import cn.huancai.erp.module.comment.vo.CommentLessonSummaryVO;
import cn.huancai.erp.module.comment.vo.CommentLessonVO;
import cn.huancai.erp.module.comment.vo.CommentSummaryVO;
import cn.huancai.erp.module.comment.vo.CommentTeacherStatVO;
import cn.huancai.erp.module.comment.vo.CommentVO;
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
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentMapper commentMapper;

    public CommentController(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @GetMapping
    public ApiResult<IPage<CommentVO>> list(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<CommentVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return ApiResult.ok(commentMapper.selectCommentPage(p, campusId, keyword, classId, courseId,
                teacher, rating, readStatus, start, end));
    }

    @GetMapping("/summary")
    public ApiResult<CommentSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(commentMapper.selectSummary(campusId, keyword, classId, courseId,
                teacher, rating, readStatus, start, end));
    }

    @GetMapping("/lessons")
    public ApiResult<IPage<CommentLessonVO>> lessons(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String commented,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<CommentLessonVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return ApiResult.ok(commentMapper.selectLessonPage(p, campusId, keyword, classId, courseId,
                teacher, start, end, commented));
    }

    @GetMapping("/lessons/summary")
    public ApiResult<CommentLessonSummaryVO> lessonSummary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String commented,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(commentMapper.selectLessonSummary(campusId, keyword, classId, courseId,
                teacher, start, end, commented));
    }

    @GetMapping("/teacher-stats")
    public ApiResult<List<CommentTeacherStatVO>> teacherStats(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(commentMapper.selectTeacherStats(campusId, keyword, classId, courseId,
                teacher, rating, readStatus, start, end));
    }

    @GetMapping("/teachers")
    public ApiResult<List<String>> teachers() {
        return ApiResult.ok(commentMapper.selectTeachers());
    }
}
