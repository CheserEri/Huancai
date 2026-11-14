package cn.huancai.erp.module.course.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.entity.CourseSubject;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.course.mapper.CourseSubjectMapper;
import cn.huancai.erp.module.course.vo.CourseCategoryVO;
import cn.huancai.erp.module.course.vo.CourseDetailVO;
import cn.huancai.erp.module.course.vo.CourseOptionVO;
import cn.huancai.erp.module.course.vo.CourseOptionsVO;
import cn.huancai.erp.module.course.vo.CourseSummaryVO;
import cn.huancai.erp.module.course.vo.CourseUpgradeVO;
import cn.huancai.erp.module.course.vo.CourseVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
@RequestMapping("/api/courses")
public class CourseController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "classCount");

    private final CourseMapper courseMapper;
    private final CampusMapper campusMapper;
    private final CourseSubjectMapper courseSubjectMapper;

    public CourseController(CourseMapper courseMapper,
                            CampusMapper campusMapper,
                            CourseSubjectMapper courseSubjectMapper) {
        this.courseMapper = courseMapper;
        this.campusMapper = campusMapper;
        this.courseSubjectMapper = courseSubjectMapper;
    }

    @GetMapping
    public ApiResult<List<Course>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)
                        .like(keyword != null && !keyword.isBlank(), Course::getName, keyword)
                        .orderByAsc(Course::getId)));
    }

    @GetMapping("/page")
    public ApiResult<IPage<CourseVO>> page(
            @RequestParam(required = false) List<Long> campusIds,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) List<String> subjects,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String lessonMode,
            @RequestParam(required = false) String chargeMode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<CourseVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        String field = sortField != null && SORT_FIELDS.contains(sortField) ? sortField : null;
        String order = "descend".equals(sortOrder) || "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return ApiResult.ok(courseMapper.selectCoursePage(
                p, campusIds, categories, subjects, lessonMode, chargeMode, status, keyword, field, order));
    }

    @GetMapping("/summary")
    public ApiResult<CourseSummaryVO> summary(
            @RequestParam(required = false) List<Long> campusIds,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) List<String> subjects,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String lessonMode,
            @RequestParam(required = false) String chargeMode) {
        return ApiResult.ok(courseMapper.selectSummary(
                campusIds, categories, subjects, lessonMode, chargeMode, status, keyword));
    }

    /** 新建课程 / 筛选所需的校区、类别、科目下拉选项。 */
    @GetMapping("/options")
    public ApiResult<CourseOptionsVO> options() {
        CourseOptionsVO vo = new CourseOptionsVO();
        vo.setCampuses(campusMapper.selectList(new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> new CourseOptionVO(c.getId(), c.getName())).toList());
        vo.setCategories(courseMapper.selectCategoryRows(null));
        vo.setSubjects(courseSubjectMapper.selectList(
                new LambdaQueryWrapper<CourseSubject>().eq(CourseSubject::getStatus, 1).orderByAsc(CourseSubject::getId)));
        return ApiResult.ok(vo);
    }

    @GetMapping("/categories")
    public ApiResult<List<CourseCategoryVO>> categories(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(courseMapper.selectCategoryRows(keyword));
    }

    @GetMapping("/subjects")
    public ApiResult<List<CourseSubject>> subjects() {
        return ApiResult.ok(courseSubjectMapper.selectList(
                new LambdaQueryWrapper<CourseSubject>().orderByAsc(CourseSubject::getId)));
    }

    @GetMapping("/upgrades")
    public ApiResult<List<CourseUpgradeVO>> upgrades(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(courseMapper.selectUpgrades(keyword));
    }

    /** 课程详情（编辑回显、展开行开班列表）。 */
    @GetMapping("/{id}")
    public ApiResult<CourseDetailVO> detail(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return ApiResult.fail("课程不存在");
        }
        CourseDetailVO vo = new CourseDetailVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCategory(course.getCategory());
        vo.setSubject(course.getSubject());
        vo.setLessonMode(course.getLessonMode());
        vo.setCampusScope(course.getCampusScope());
        vo.setCampusId(course.getCampusId());
        if (course.getCampusId() != null) {
            Campus campus = campusMapper.selectById(course.getCampusId());
            vo.setCampusName(campus == null ? null : campus.getName());
        }
        vo.setLessonCharge(course.getLessonCharge());
        vo.setTimeCharge(course.getTimeCharge());
        vo.setPeriodCharge(course.getPeriodCharge());
        vo.setMaterialFee(course.getMaterialFee());
        vo.setRemark(course.getRemark());
        vo.setStatus(course.getStatus());
        vo.setCreatedAt(course.getCreatedAt());
        vo.setPrices(courseMapper.selectCoursePrices(id));
        vo.setClasses(courseMapper.selectCourseClasses(id));
        return ApiResult.ok(vo);
    }

    @GetMapping("/{id}/classes")
    public ApiResult<List<cn.huancai.erp.module.course.vo.CourseClassVO>> classes(@PathVariable Long id) {
        return ApiResult.ok(courseMapper.selectCourseClasses(id));
    }
}
