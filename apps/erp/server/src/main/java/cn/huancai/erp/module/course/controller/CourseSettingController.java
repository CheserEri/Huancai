package cn.huancai.erp.module.course.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.course.dto.CourseSettingForm;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.entity.CourseCategory;
import cn.huancai.erp.module.course.entity.CourseSubject;
import cn.huancai.erp.module.course.entity.CourseUpgrade;
import cn.huancai.erp.module.course.mapper.CourseCategoryMapper;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.course.mapper.CourseSubjectMapper;
import cn.huancai.erp.module.course.mapper.CourseUpgradeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseSettingController {

    private final CourseMapper courseMapper;
    private final CourseCategoryMapper courseCategoryMapper;
    private final CourseSubjectMapper courseSubjectMapper;
    private final CourseUpgradeMapper courseUpgradeMapper;

    public CourseSettingController(CourseMapper courseMapper,
                                   CourseCategoryMapper courseCategoryMapper,
                                   CourseSubjectMapper courseSubjectMapper,
                                   CourseUpgradeMapper courseUpgradeMapper) {
        this.courseMapper = courseMapper;
        this.courseCategoryMapper = courseCategoryMapper;
        this.courseSubjectMapper = courseSubjectMapper;
        this.courseUpgradeMapper = courseUpgradeMapper;
    }

    // ---------------- 课程类别 ----------------

    @PostMapping("/categories")
    public ApiResult<CourseCategory> createCategory(@RequestBody CourseSettingForm form) {
        String name = trimToNull(form.getName());
        if (name == null) {
            return ApiResult.fail("请填写课程类别名称");
        }
        Long exists = courseCategoryMapper.selectCount(
                new LambdaQueryWrapper<CourseCategory>().eq(CourseCategory::getName, name));
        if (exists != null && exists > 0) {
            return ApiResult.fail("课程类别「" + name + "」已存在");
        }
        CourseCategory category = new CourseCategory();
        category.setName(name);
        courseCategoryMapper.insert(category);
        return ApiResult.ok(category);
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public ApiResult<Void> renameCategory(@PathVariable Long id, @RequestBody CourseSettingForm form) {
        CourseCategory category = courseCategoryMapper.selectById(id);
        if (category == null) {
            return ApiResult.fail("课程类别不存在");
        }
        String name = trimToNull(form.getName());
        if (name == null) {
            return ApiResult.fail("请填写课程类别名称");
        }
        if (name.equals(category.getName())) {
            return ApiResult.ok(null);
        }
        Long exists = courseCategoryMapper.selectCount(
                new LambdaQueryWrapper<CourseCategory>().eq(CourseCategory::getName, name));
        if (exists != null && exists > 0) {
            return ApiResult.fail("课程类别「" + name + "」已存在");
        }
        String oldName = category.getName();
        category.setName(name);
        courseCategoryMapper.updateById(category);
        courseMapper.update(null, new LambdaUpdateWrapper<Course>()
                .eq(Course::getCategory, oldName)
                .set(Course::getCategory, name));
        return ApiResult.ok(null);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResult<Void> deleteCategory(@PathVariable Long id) {
        CourseCategory category = courseCategoryMapper.selectById(id);
        if (category == null) {
            return ApiResult.ok(null);
        }
        Long used = courseMapper.selectCount(
                new LambdaQueryWrapper<Course>().eq(Course::getCategory, category.getName()));
        if (used != null && used > 0) {
            return ApiResult.fail("课程类别「" + category.getName() + "」下还有 " + used + " 个课程，不能删除");
        }
        courseCategoryMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    // ---------------- 科目 ----------------

    @PostMapping("/subjects")
    public ApiResult<CourseSubject> createSubject(@RequestBody CourseSettingForm form) {
        String name = trimToNull(form.getName());
        if (name == null) {
            return ApiResult.fail("请填写科目名称");
        }
        Long exists = courseSubjectMapper.selectCount(
                new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getName, name));
        if (exists != null && exists > 0) {
            return ApiResult.fail("科目「" + name + "」已存在");
        }
        CourseSubject subject = new CourseSubject();
        subject.setName(name);
        subject.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        courseSubjectMapper.insert(subject);
        return ApiResult.ok(subject);
    }

    @DeleteMapping("/subjects/{id}")
    public ApiResult<Void> deleteSubject(@PathVariable Long id) {
        CourseSubject subject = courseSubjectMapper.selectById(id);
        if (subject == null) {
            return ApiResult.ok(null);
        }
        Long used = courseMapper.selectCount(
                new LambdaQueryWrapper<Course>().eq(Course::getSubject, subject.getName()));
        if (used != null && used > 0) {
            return ApiResult.fail("科目「" + subject.getName() + "」已被 " + used + " 个课程使用，不能删除");
        }
        courseSubjectMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    // ---------------- 升期关系 ----------------

    @PostMapping("/upgrades")
    public ApiResult<CourseUpgrade> createUpgrade(@RequestBody CourseSettingForm form) {
        String name = trimToNull(form.getName());
        if (name == null) {
            return ApiResult.fail("请填写升期关系名称");
        }
        if (form.getFromCourseId() == null || courseMapper.selectById(form.getFromCourseId()) == null) {
            return ApiResult.fail("请选择原课程");
        }
        if (form.getToCourseId() != null && courseMapper.selectById(form.getToCourseId()) == null) {
            return ApiResult.fail("升期课程不存在");
        }
        CourseUpgrade upgrade = new CourseUpgrade();
        upgrade.setName(name);
        upgrade.setFromCourseId(form.getFromCourseId());
        upgrade.setToCourseId(form.getToCourseId());
        upgrade.setPeriodCount(form.getPeriodCount() == null ? 1 : Math.max(form.getPeriodCount(), 0));
        courseUpgradeMapper.insert(upgrade);
        return ApiResult.ok(upgrade);
    }

    @DeleteMapping("/upgrades/{id}")
    public ApiResult<Void> deleteUpgrade(@PathVariable Long id) {
        courseUpgradeMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
