package cn.huancai.erp.module.homeservice.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.homeservice.dto.HomeworkForm;
import cn.huancai.erp.module.homeservice.entity.ClassHomework;
import cn.huancai.erp.module.homeservice.service.HomeworkService;
import cn.huancai.erp.module.homeservice.vo.HomeworkVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 家校服务-作业：按班级发布、编辑与删除作业。 */
@RestController
@RequestMapping("/api/homeworks")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @GetMapping
    public ApiResult<IPage<HomeworkVO>> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(homeworkService.list(classId, keyword, start, end, page, size));
    }

    @PostMapping
    public ApiResult<ClassHomework> create(@RequestBody HomeworkForm form) {
        try {
            return ApiResult.ok(homeworkService.create(form));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody HomeworkForm form) {
        try {
            homeworkService.update(id, form);
            return ApiResult.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        try {
            homeworkService.delete(id);
            return ApiResult.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }
}
