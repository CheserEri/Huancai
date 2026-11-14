package cn.huancai.erp.module.course.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.dto.CourseBatchForm;
import cn.huancai.erp.module.course.dto.CourseForm;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.entity.CoursePrice;
import cn.huancai.erp.module.course.entity.CourseUpgrade;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.course.mapper.CoursePriceMapper;
import cn.huancai.erp.module.course.mapper.CourseUpgradeMapper;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplateCourse;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplateCourseMapper;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseWriteController {

    private final CourseMapper courseMapper;
    private final CoursePriceMapper coursePriceMapper;
    private final CourseUpgradeMapper courseUpgradeMapper;
    private final CampusMapper campusMapper;
    private final ClassGroupMapper classGroupMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final PriceTemplateCourseMapper priceTemplateCourseMapper;

    public CourseWriteController(CourseMapper courseMapper,
                                 CoursePriceMapper coursePriceMapper,
                                 CourseUpgradeMapper courseUpgradeMapper,
                                 CampusMapper campusMapper,
                                 ClassGroupMapper classGroupMapper,
                                 EnrollmentMapper enrollmentMapper,
                                 PriceTemplateCourseMapper priceTemplateCourseMapper) {
        this.courseMapper = courseMapper;
        this.coursePriceMapper = coursePriceMapper;
        this.courseUpgradeMapper = courseUpgradeMapper;
        this.campusMapper = campusMapper;
        this.classGroupMapper = classGroupMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.priceTemplateCourseMapper = priceTemplateCourseMapper;
    }

    @PostMapping
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody CourseForm form) {
        String error = validate(form, true);
        if (error != null) {
            return ApiResult.fail(error);
        }
        Course course = new Course();
        course.setStatus(1);
        course.setLessonCharge(1);
        course.setTimeCharge(0);
        course.setPeriodCharge(0);
        course.setCampusScope("全部");
        course.setLessonMode("班课");
        course.setMaterialFee(BigDecimal.ZERO);
        apply(course, form);
        courseMapper.insert(course);
        savePrices(course.getId(), form);
        syncPriceSummary(course.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", course.getId());
        data.put("name", course.getName());
        return ApiResult.ok(data);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody CourseForm form) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return ApiResult.fail("课程不存在");
        }
        String error = validate(form, false);
        if (error != null) {
            return ApiResult.fail(error);
        }
        apply(course, form);
        courseMapper.updateById(course);
        savePrices(id, form);
        syncPriceSummary(id);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResult<Void> delete(@PathVariable Long id) {
        String blocked = deleteBlockedReason(id);
        if (blocked != null) {
            return ApiResult.fail(blocked);
        }
        removeCourse(id);
        return ApiResult.ok(null);
    }

    /** 批量导出由前端完成，这里处理删除 / 起售 / 停售。 */
    @PostMapping("/batch")
    @Transactional
    public ApiResult<Integer> batch(@RequestBody CourseBatchForm form) {
        if (form.getIds() == null || form.getIds().isEmpty()) {
            return ApiResult.fail("请先选择课程");
        }
        String action = form.getAction() == null ? "" : form.getAction();
        switch (action) {
            case "delete" -> {
                for (Long id : form.getIds()) {
                    String blocked = deleteBlockedReason(id);
                    if (blocked != null) {
                        return ApiResult.fail(blocked);
                    }
                }
                int deleted = 0;
                for (Long id : form.getIds()) {
                    removeCourse(id);
                    deleted++;
                }
                return ApiResult.ok(deleted);
            }
            case "on", "off" -> {
                Course update = new Course();
                update.setStatus("on".equals(action) ? 1 : 0);
                int count = courseMapper.update(update,
                        new LambdaQueryWrapper<Course>().in(Course::getId, form.getIds()));
                return ApiResult.ok(count);
            }
            default -> {
                return ApiResult.fail("不支持的批量操作");
            }
        }
    }

    private String validate(CourseForm form, boolean create) {
        if (form.getName() != null && form.getName().isBlank()) {
            return "请填写课程名称";
        }
        if (create && (form.getName() == null || form.getName().isBlank())) {
            return "请填写课程名称";
        }
        if (create && (form.getCategory() == null || form.getCategory().isBlank())) {
            return "请选择课程类别";
        }
        if (form.getRemark() != null && form.getRemark().length() > 300) {
            return "备注最多可输入 300 字";
        }
        String scope = form.getCampusScope();
        if (scope != null && !"全部".equals(scope) && !"指定".equals(scope)) {
            return "开课校区参数不正确";
        }
        if ("指定".equals(scope)) {
            if (form.getCampusId() == null) {
                return "请选择指定校区";
            }
            if (campusMapper.selectById(form.getCampusId()) == null) {
                return "指定校区不存在";
            }
        }
        return null;
    }

    private void apply(Course course, CourseForm form) {
        if (form.getName() != null) course.setName(form.getName().trim());
        if (form.getCategory() != null) course.setCategory(form.getCategory().trim());
        if (form.getSubject() != null) course.setSubject(trimToNull(form.getSubject()));
        if (form.getLessonMode() != null) {
            course.setLessonMode("一对一".equals(form.getLessonMode()) ? "一对一" : "班课");
        }
        if (form.getCampusScope() != null) {
            course.setCampusScope("指定".equals(form.getCampusScope()) ? "指定" : "全部");
        }
        if ("全部".equals(course.getCampusScope())) {
            course.setCampusId(null);
        } else if (form.getCampusId() != null) {
            course.setCampusId(form.getCampusId());
        }
        if (form.getLessonCharge() != null) course.setLessonCharge(form.getLessonCharge() ? 1 : 0);
        if (form.getTimeCharge() != null) course.setTimeCharge(form.getTimeCharge() ? 1 : 0);
        if (form.getPeriodCharge() != null) course.setPeriodCharge(form.getPeriodCharge() ? 1 : 0);
        if (form.getMaterialFee() != null) {
            course.setMaterialFee(form.getMaterialFee().max(BigDecimal.ZERO));
        }
        if (form.getRemark() != null) course.setRemark(trimToNull(form.getRemark()));
    }

    /** 按课时定价行整体替换；同时把首行回写到课程的标准课时/总价，兼容报读等旧口径。 */
    private void savePrices(Long courseId, CourseForm form) {
        if (form.getPrices() == null) {
            return;
        }
        coursePriceMapper.delete(new LambdaQueryWrapper<CoursePrice>().eq(CoursePrice::getCourseId, courseId));
        List<CoursePrice> rows = new ArrayList<>();
        for (CourseForm.CoursePriceForm row : form.getPrices()) {
            if (row == null || row.getLessons() == null || row.getLessons().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CoursePrice price = new CoursePrice();
            price.setCourseId(courseId);
            price.setCampusId(row.getCampusId());
            price.setLessons(row.getLessons());
            price.setTotalPrice(row.getTotalPrice());
            rows.add(price);
        }
        for (CoursePrice row : rows) {
            coursePriceMapper.insert(row);
        }
    }

    private void syncPriceSummary(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            return;
        }
        List<CoursePrice> rows = coursePriceMapper.selectList(
                new LambdaQueryWrapper<CoursePrice>().eq(CoursePrice::getCourseId, courseId).orderByAsc(CoursePrice::getId));
        if (!rows.isEmpty()) {
            course.setTotalLessons(rows.get(0).getLessons());
            course.setListPrice(rows.get(0).getTotalPrice());
            courseMapper.updateById(course);
        }
    }

    private String deleteBlockedReason(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return null;
        }
        Long classes = classGroupMapper.selectCount(
                new LambdaQueryWrapper<ClassGroup>().eq(ClassGroup::getCourseId, id));
        if (classes != null && classes > 0) {
            return "课程「" + course.getName() + "」已关联 " + classes + " 个班级，请先处理班级后再删除";
        }
        Long enrollments = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getCourseId, id));
        if (enrollments != null && enrollments > 0) {
            return "课程「" + course.getName() + "」已有 " + enrollments + " 条报读记录，不能删除";
        }
        Long templates = priceTemplateCourseMapper.selectCount(
                new LambdaQueryWrapper<PriceTemplateCourse>().eq(PriceTemplateCourse::getCourseId, id));
        if (templates != null && templates > 0) {
            return "课程「" + course.getName() + "」已被 " + templates + " 个缴费模板引用，请先解除绑定后再删除";
        }
        return null;
    }

    private void removeCourse(Long id) {
        coursePriceMapper.delete(new LambdaQueryWrapper<CoursePrice>().eq(CoursePrice::getCourseId, id));
        courseUpgradeMapper.delete(new LambdaQueryWrapper<CourseUpgrade>()
                .eq(CourseUpgrade::getFromCourseId, id)
                .or()
                .eq(CourseUpgrade::getToCourseId, id));
        courseMapper.deleteById(id);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
