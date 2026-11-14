package cn.huancai.erp.module.teaching.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.entity.LessonTxn;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.student.mapper.LessonTxnMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.teaching.dto.ClassAssignForm;
import cn.huancai.erp.module.teaching.dto.ClassBatchForm;
import cn.huancai.erp.module.teaching.dto.ClassForm;
import cn.huancai.erp.module.teaching.dto.ClassStudentAddForm;
import cn.huancai.erp.module.teaching.dto.ClassUpgradeForm;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import cn.huancai.erp.module.teaching.vo.ClassAssignOptionsVO;
import cn.huancai.erp.module.teaching.vo.ClassNameOptionVO;
import cn.huancai.erp.module.teaching.vo.ClassOptionsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/classes")
public class ClassGroupWriteController {

    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final CampusMapper campusMapper;
    private final CourseMapper courseMapper;
    private final StaffMapper staffMapper;
    private final StudentMapper studentMapper;
    private final ScheduleMapper scheduleMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final LessonTxnMapper lessonTxnMapper;

    public ClassGroupWriteController(ClassGroupMapper classGroupMapper,
                                     ClassStudentMapper classStudentMapper,
                                     CampusMapper campusMapper,
                                     CourseMapper courseMapper,
                                     StaffMapper staffMapper,
                                     StudentMapper studentMapper,
                                     ScheduleMapper scheduleMapper,
                                     EnrollmentMapper enrollmentMapper,
                                     LessonTxnMapper lessonTxnMapper) {
        this.classGroupMapper = classGroupMapper;
        this.classStudentMapper = classStudentMapper;
        this.campusMapper = campusMapper;
        this.courseMapper = courseMapper;
        this.staffMapper = staffMapper;
        this.studentMapper = studentMapper;
        this.scheduleMapper = scheduleMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.lessonTxnMapper = lessonTxnMapper;
    }

    @GetMapping("/options")
    public ApiResult<ClassOptionsVO> options() {
        ClassOptionsVO vo = new ClassOptionsVO();
        vo.setCampuses(campusMapper.selectList(new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> new ClassNameOptionVO(c.getId(), c.getName())).toList());
        vo.setCourses(courseMapper.selectList(new LambdaQueryWrapper<Course>().orderByAsc(Course::getName))
                .stream().map(c -> new ClassNameOptionVO(c.getId(), c.getName())).toList());
        vo.setTeachers(staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .isNotNull(Staff::getName).ne(Staff::getName, "").orderByAsc(Staff::getName))
                .stream().map(s -> new ClassNameOptionVO(s.getId(), s.getName())).toList());
        vo.setRooms(classGroupMapper.selectRooms());
        vo.setLessonClasses(classGroupMapper.selectLessonClasses());
        return ApiResult.ok(vo);
    }

    /** 分班弹窗：该课程的可选班级与候选学员。 */
    @GetMapping("/assign-options")
    public ApiResult<ClassAssignOptionsVO> assignOptions(@RequestParam Long courseId) {
        if (courseId == null) {
            return ApiResult.fail("请指定课程");
        }
        ClassAssignOptionsVO vo = new ClassAssignOptionsVO();
        vo.setClasses(classGroupMapper.selectCourseClasses(courseId));
        vo.setStudents(classGroupMapper.selectCourseStudents(courseId));
        return ApiResult.ok(vo);
    }

    /** 批量分班：选中学员调整到目标班级，未选中学员留在原班级。 */
    @PostMapping("/assign")
    @Transactional
    public ApiResult<Map<String, Object>> assign(@RequestBody ClassAssignForm form) {
        if (form.getCourseId() == null) {
            return ApiResult.fail("请指定课程");
        }
        if (form.getTargetClassId() == null) {
            return ApiResult.fail("请选择目标班级");
        }
        if (form.getStudentIds() == null || form.getStudentIds().isEmpty()) {
            return ApiResult.fail("请先勾选需要分班的学员");
        }
        ClassGroup target = classGroupMapper.selectById(form.getTargetClassId());
        if (target == null) {
            return ApiResult.fail("目标班级不存在");
        }
        if (target.getCourseId() != null && !target.getCourseId().equals(form.getCourseId())) {
            return ApiResult.fail("目标班级不属于该课程");
        }

        Set<Long> affectedClasses = new LinkedHashSet<>();
        int moved = 0;
        for (Long studentId : form.getStudentIds()) {
            if (studentId == null || studentMapper.selectById(studentId) == null) {
                continue;
            }
            // 1. 清理该学员在其他班级（同课程）的成员关系，相当于调班
            List<ClassStudent> memberships = classStudentMapper.selectList(
                    new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStudentId, studentId));
            List<Long> memberClassIds = memberships.stream()
                    .map(ClassStudent::getClassId).distinct().toList();
            if (!memberClassIds.isEmpty()) {
                List<ClassGroup> memberClasses = classGroupMapper.selectList(
                        new LambdaQueryWrapper<ClassGroup>().in(ClassGroup::getId, memberClassIds));
                for (ClassStudent membership : memberships) {
                    if (membership.getClassId().equals(target.getId())) {
                        continue;
                    }
                    ClassGroup oldClass = memberClasses.stream()
                            .filter(item -> item.getId().equals(membership.getClassId()))
                            .findFirst().orElse(null);
                    if (oldClass != null && form.getCourseId().equals(oldClass.getCourseId())) {
                        classStudentMapper.deleteById(membership.getId());
                        affectedClasses.add(oldClass.getId());
                    }
                }
            }

            // 2. 加入目标班级并回填课时
            ClassStudent cs = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getClassId, target.getId())
                            .eq(ClassStudent::getStudentId, studentId))
                    .stream().findFirst().orElse(null);
            boolean isNew = cs == null;
            if (isNew) {
                cs = new ClassStudent();
                cs.setCampusId(target.getCampusId());
                cs.setClassId(target.getId());
                cs.setStudentId(studentId);
                cs.setStatus("在读");
                cs.setJoinDate(LocalDateTime.now());
                cs.setTotalLessons(BigDecimal.ZERO);
                cs.setUsedLessons(BigDecimal.ZERO);
                cs.setRemainLessons(BigDecimal.ZERO);
            }
            Enrollment enrollment = activeEnrollment(studentId, form.getCourseId());
            if (enrollment != null) {
                // 入班时把未绑定的剩余学费按单价折算成课时
                bindTuitionToLessons(enrollment);
                cs.setTotalLessons(nz(enrollment.getTotalLessons()).add(nz(enrollment.getGiftLessons())));
                cs.setUsedLessons(nz(enrollment.getUsedLessons()));
                cs.setRemainLessons(nz(enrollment.getRemainLessons()));
            }
            if (isNew) {
                classStudentMapper.insert(cs);
            } else {
                classStudentMapper.updateById(cs);
            }

            // 3. 生效中的报读统一挂到目标班级
            enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getStudentId, studentId)
                            .eq(Enrollment::getCourseId, form.getCourseId())
                            .eq(Enrollment::getStatus, "生效"))
                    .forEach(item -> {
                        if (!target.getId().equals(item.getClassId())) {
                            item.setClassId(target.getId());
                            item.setUpdatedAt(LocalDateTime.now());
                            enrollmentMapper.updateById(item);
                        }
                    });
            moved++;
        }

        affectedClasses.add(target.getId());
        int enrolled = 0;
        for (Long classId : affectedClasses) {
            int count = refreshEnrolled(classId);
            if (classId.equals(target.getId())) {
                enrolled = count;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moved", moved);
        data.put("className", target.getName());
        data.put("enrolled", enrolled);
        return ApiResult.ok(data);
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody ClassForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return ApiResult.fail("请填写班级名称");
        }
        if (form.getCampusId() == null) {
            return ApiResult.fail("请选择开班校区");
        }
        Campus campus = campusMapper.selectById(form.getCampusId());
        if (campus == null) {
            return ApiResult.fail("开班校区不存在");
        }

        ClassGroup cg = new ClassGroup();
        cg.setName(form.getName().trim());
        cg.setCampusId(form.getCampusId());
        cg.setCourseId(validCourseId(form.getCourseId()));
        applyTeacher(cg, form.getTeacherId());
        cg.setAssistName(trimToNull(form.getAssistName()));
        cg.setLessonClassName(trimToNull(form.getLessonClassName()));
        cg.setRoom(trimToNull(form.getRoom()));
        cg.setScheduleText(trimToNull(form.getScheduleText()));
        cg.setCapacity(form.getCapacity() == null ? 0 : Math.max(form.getCapacity(), 0));
        cg.setEnrolledCount(0);
        cg.setStuRate(stuRate(0, cg.getCapacity()));
        cg.setScheduleCount(0);
        cg.setStartDate(form.getStartDate() == null ? LocalDate.now() : form.getStartDate());
        cg.setEndDate(form.getEndDate());
        cg.setStatus(trimToNull(form.getStatus()) == null ? "已开班" : form.getStatus().trim());
        cg.setEnrolling(form.getEnrolling() == null ? 1 : form.getEnrolling());
        cg.setFeeStandard(trimToNull(form.getFeeStandard()));
        cg.setSource("本系统新建");
        cg.setCreateBy("管理员");
        classGroupMapper.insert(cg);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", cg.getId());
        data.put("name", cg.getName());
        return ApiResult.ok(data);
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody ClassForm form) {
        ClassGroup cg = classGroupMapper.selectById(id);
        if (cg == null) {
            return ApiResult.fail("班级不存在");
        }
        if (form.getName() != null) {
            if (form.getName().isBlank()) {
                return ApiResult.fail("请填写班级名称");
            }
            cg.setName(form.getName().trim());
        }
        if (form.getCampusId() != null) {
            if (campusMapper.selectById(form.getCampusId()) == null) {
                return ApiResult.fail("开班校区不存在");
            }
            cg.setCampusId(form.getCampusId());
        }
        if (form.getCourseId() != null) {
            cg.setCourseId(validCourseId(form.getCourseId()));
        }
        if (form.getTeacherId() != null) {
            applyTeacher(cg, form.getTeacherId());
        }
        if (form.getAssistName() != null) cg.setAssistName(trimToNull(form.getAssistName()));
        if (form.getLessonClassName() != null) cg.setLessonClassName(trimToNull(form.getLessonClassName()));
        if (form.getRoom() != null) cg.setRoom(trimToNull(form.getRoom()));
        if (form.getScheduleText() != null) cg.setScheduleText(trimToNull(form.getScheduleText()));
        if (form.getCapacity() != null) cg.setCapacity(Math.max(form.getCapacity(), 0));
        if (form.getStartDate() != null) cg.setStartDate(form.getStartDate());
        if (form.getEndDate() != null) cg.setEndDate(form.getEndDate());
        if (form.getStatus() != null && !form.getStatus().isBlank()) {
            String nextStatus = form.getStatus().trim();
            if ("已结班".equals(nextStatus) && !"已结班".equals(cg.getStatus())) {
                String blocked = unfinishedReason(cg);
                if (blocked != null) {
                    return ApiResult.fail(blocked);
                }
            }
            cg.setStatus(nextStatus);
        }
        if (form.getEnrolling() != null) cg.setEnrolling(form.getEnrolling());
        if (form.getFeeStandard() != null) cg.setFeeStandard(trimToNull(form.getFeeStandard()));
        cg.setStuRate(stuRate(cg.getEnrolledCount() == null ? 0 : cg.getEnrolledCount(), cg.getCapacity()));
        classGroupMapper.updateById(cg);
        return ApiResult.ok(null);
    }

    /** 班级结班：所有学员必须已结课（本班不存在生效/停课的报读）才允许结班。 */
    @PostMapping("/{id}/close")
    @Transactional
    public ApiResult<Void> close(@PathVariable Long id) {
        ClassGroup cg = classGroupMapper.selectById(id);
        if (cg == null) {
            return ApiResult.fail("班级不存在");
        }
        if ("已结班".equals(cg.getStatus())) {
            return ApiResult.fail("该班级已经结班");
        }
        String blocked = unfinishedReason(cg);
        if (blocked != null) {
            return ApiResult.fail(blocked);
        }
        cg.setStatus("已结班");
        cg.setEnrolling(0);
        if (cg.getEndDate() == null) {
            cg.setEndDate(LocalDate.now());
        }
        classGroupMapper.updateById(cg);
        return ApiResult.ok(null);
    }

    /** 结班校验：仍有在读报读的学员会阻断结班；返回 null 表示可以结班。 */
    private String unfinishedReason(ClassGroup cg) {
        List<ClassStudent> members = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, cg.getId()));
        List<Long> studentIds = members.stream()
                .map(ClassStudent::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Enrollment> query = new LambdaQueryWrapper<Enrollment>()
                .in(Enrollment::getStudentId, studentIds)
                .in(Enrollment::getStatus, "生效", "停课");
        if (cg.getCourseId() == null) {
            query.eq(Enrollment::getClassId, cg.getId());
        } else {
            Long courseId = cg.getCourseId();
            query.and(w -> w.eq(Enrollment::getClassId, cg.getId())
                    .or(x -> x.isNull(Enrollment::getClassId).eq(Enrollment::getCourseId, courseId)));
        }
        Long active = enrollmentMapper.selectCount(query);
        if (active != null && active > 0) {
            return "还有 " + active + " 名学员的报读未结课，请先在学员报读中办理结课后再结班";
        }
        return null;
    }

    /** 批量修改招生状态 / 班级状态。 */
    @PostMapping("/batch-status")
    public ApiResult<Integer> batchStatus(@RequestBody ClassBatchForm form) {
        if (form.getIds() == null || form.getIds().isEmpty()) {
            return ApiResult.fail("请先选择班级");
        }
        if (form.getEnrolling() == null && (form.getStatus() == null || form.getStatus().isBlank())) {
            return ApiResult.fail("请选择要批量修改的内容");
        }
        boolean closing = form.getStatus() != null && "已结班".equals(form.getStatus().trim());
        if (closing) {
            for (Long id : form.getIds()) {
                ClassGroup cg = classGroupMapper.selectById(id);
                if (cg == null || "已结班".equals(cg.getStatus())) {
                    continue;
                }
                String blocked = unfinishedReason(cg);
                if (blocked != null) {
                    return ApiResult.fail("班级「" + cg.getName() + "」" + blocked);
                }
            }
        }
        ClassGroup update = new ClassGroup();
        if (form.getEnrolling() != null) {
            update.setEnrolling(form.getEnrolling());
        }
        if (form.getStatus() != null && !form.getStatus().isBlank()) {
            update.setStatus(form.getStatus().trim());
            if ("已结班".equals(form.getStatus().trim())) {
                update.setEnrolling(0);
            }
        }
        int count = classGroupMapper.update(update,
                new LambdaQueryWrapper<ClassGroup>().in(ClassGroup::getId, form.getIds()));
        return ApiResult.ok(count);
    }

    /** 批量删除班级，已有排课记录的班级不允许删除。 */
    @PostMapping("/batch-delete")
    @Transactional
    public ApiResult<Integer> batchDelete(@RequestBody ClassBatchForm form) {
        if (form.getIds() == null || form.getIds().isEmpty()) {
            return ApiResult.fail("请先选择班级");
        }
        for (Long id : form.getIds()) {
            ClassGroup cg = classGroupMapper.selectById(id);
            if (cg == null) {
                continue;
            }
            Long lessons = scheduleMapper.selectCount(
                    new LambdaQueryWrapper<Schedule>().eq(Schedule::getClassId, id));
            if (lessons != null && lessons > 0) {
                return ApiResult.fail("班级「" + cg.getName() + "」已有 " + lessons + " 条排课记录，请先删除排课后再删除班级");
            }
        }
        int deleted = 0;
        for (Long id : form.getIds()) {
            classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, id));
            deleted += classGroupMapper.deleteById(id);
        }
        return ApiResult.ok(deleted);
    }

    /** 升班：把原班级的学员分批升入一个或多个新班级。 */
    @PostMapping("/upgrade")
    @Transactional
    public ApiResult<Map<String, Object>> upgrade(@RequestBody ClassUpgradeForm form) {
        if (form.getSourceClassId() == null) {
            return ApiResult.fail("请选择原班级");
        }
        if (form.getTargets() == null || form.getTargets().isEmpty()) {
            return ApiResult.fail("请选择新班级并安排学员");
        }
        ClassGroup source = classGroupMapper.selectById(form.getSourceClassId());
        if (source == null) {
            return ApiResult.fail("原班级不存在");
        }

        Set<Long> handled = new LinkedHashSet<>();
        List<Map<String, Object>> targetStats = new java.util.ArrayList<>();
        int moved = 0;
        int kept = 0;
        int removed = 0;

        for (ClassUpgradeForm.Target targetForm : form.getTargets()) {
            if (targetForm.getClassId() == null
                    || targetForm.getStudentIds() == null
                    || targetForm.getStudentIds().isEmpty()) {
                continue;
            }
            if (targetForm.getClassId().equals(source.getId())) {
                return ApiResult.fail("新班级不能与原班级相同");
            }
            ClassGroup target = classGroupMapper.selectById(targetForm.getClassId());
            if (target == null) {
                return ApiResult.fail("新班级不存在");
            }
            if ("已结班".equals(target.getStatus())) {
                return ApiResult.fail("班级「" + target.getName() + "」已结班，不能作为新班级");
            }

            int count = 0;
            for (Long studentId : targetForm.getStudentIds()) {
                if (studentId == null || !handled.add(studentId)) {
                    continue;
                }
                ClassStudent sourceCs = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                                .eq(ClassStudent::getClassId, source.getId())
                                .eq(ClassStudent::getStudentId, studentId))
                        .stream().findFirst().orElse(null);
                if (sourceCs == null) {
                    continue;
                }
                Student student = studentMapper.selectById(studentId);
                if (student == null) {
                    continue;
                }

                boolean sameCourse = source.getCourseId() != null
                        && source.getCourseId().equals(target.getCourseId());

                ClassStudent targetCs = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                                .eq(ClassStudent::getClassId, target.getId())
                                .eq(ClassStudent::getStudentId, studentId))
                        .stream().findFirst().orElse(null);
                boolean isNew = targetCs == null;
                if (isNew) {
                    targetCs = new ClassStudent();
                    targetCs.setCampusId(target.getCampusId());
                    targetCs.setClassId(target.getId());
                    targetCs.setStudentId(studentId);
                    targetCs.setStatus("在读");
                    targetCs.setJoinDate(LocalDateTime.now());
                    targetCs.setTotalLessons(BigDecimal.ZERO);
                    targetCs.setUsedLessons(BigDecimal.ZERO);
                    targetCs.setRemainLessons(BigDecimal.ZERO);
                }
                if (sameCourse) {
                    // 同课程升班：课时随学员带到新班，报读同步挂到新班
                    targetCs.setTotalLessons(nz(sourceCs.getTotalLessons()));
                    targetCs.setUsedLessons(nz(sourceCs.getUsedLessons()));
                    targetCs.setRemainLessons(nz(sourceCs.getRemainLessons()));
                    enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                                    .eq(Enrollment::getStudentId, studentId)
                                    .eq(Enrollment::getCourseId, source.getCourseId())
                                    .eq(Enrollment::getStatus, "生效"))
                            .forEach(item -> {
                                if (!target.getId().equals(item.getClassId())) {
                                    item.setClassId(target.getId());
                                    item.setUpdatedAt(LocalDateTime.now());
                                    enrollmentMapper.updateById(item);
                                }
                            });
                }
                targetCs.setRemark("由「" + source.getName() + "」升班");
                if (isNew) {
                    classStudentMapper.insert(targetCs);
                } else {
                    classStudentMapper.updateById(targetCs);
                }

                // 原班记录：欠费学员必须保留；跨课程升班时未消耗课时仍属原课程，也保留
                boolean hasDebt = nz(student.getDebt()).signum() > 0;
                boolean hasRemain = nz(sourceCs.getRemainLessons()).signum() > 0;
                if (hasDebt || (!sameCourse && hasRemain)) {
                    kept++;
                } else {
                    classStudentMapper.deleteById(sourceCs.getId());
                    removed++;
                }
                moved++;
                count++;
            }

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("classId", target.getId());
            stat.put("name", target.getName());
            stat.put("count", count);
            targetStats.add(stat);
            refreshEnrolled(target.getId());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moved", moved);
        data.put("kept", kept);
        data.put("removed", removed);
        data.put("sourceEnrolled", refreshEnrolled(source.getId()));
        data.put("targets", targetStats);
        return ApiResult.ok(data);
    }

    /** 分班：把学员加入班级。 */
    @PostMapping("/{id}/students")
    @Transactional
    public ApiResult<Map<String, Object>> addStudents(@PathVariable Long id,
                                                     @RequestBody ClassStudentAddForm form) {
        ClassGroup cg = classGroupMapper.selectById(id);
        if (cg == null) {
            return ApiResult.fail("班级不存在");
        }
        if (form.getStudentIds() == null || form.getStudentIds().isEmpty()) {
            return ApiResult.fail("请先选择学员");
        }
        int added = 0;
        for (Long studentId : form.getStudentIds()) {
            if (studentId == null) {
                continue;
            }
            Student student = studentMapper.selectById(studentId);
            if (student == null) {
                continue;
            }
            ClassStudent cs = classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getClassId, id)
                            .eq(ClassStudent::getStudentId, studentId))
                    .stream().findFirst().orElse(null);
            boolean isNew = cs == null;
            if (isNew) {
                cs = new ClassStudent();
                cs.setCampusId(cg.getCampusId());
                cs.setClassId(id);
                cs.setStudentId(studentId);
                cs.setStatus("在读");
                cs.setJoinDate(LocalDateTime.now());
                cs.setTotalLessons(BigDecimal.ZERO);
                cs.setUsedLessons(BigDecimal.ZERO);
                cs.setRemainLessons(BigDecimal.ZERO);
            }
            // 分班时回填该课程的有效报读课时，并把报读挂到本班
            Enrollment enrollment = activeEnrollment(studentId, cg.getCourseId());
            if (enrollment != null) {
                // 入班时把未绑定的剩余学费按单价折算成课时
                bindTuitionToLessons(enrollment);
                cs.setTotalLessons(nz(enrollment.getTotalLessons()).add(nz(enrollment.getGiftLessons())));
                cs.setUsedLessons(nz(enrollment.getUsedLessons()));
                cs.setRemainLessons(nz(enrollment.getRemainLessons()));
                if (enrollment.getClassId() == null || !enrollment.getClassId().equals(id)) {
                    enrollment.setClassId(id);
                    enrollment.setUpdatedAt(LocalDateTime.now());
                    enrollmentMapper.updateById(enrollment);
                }
            }
            if (isNew) {
                classStudentMapper.insert(cs);
                added++;
            } else {
                classStudentMapper.updateById(cs);
            }
        }
        int enrolled = refreshEnrolled(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("added", added);
        data.put("enrolled", enrolled);
        return ApiResult.ok(data);
    }

    /** 调班：把学员移出班级。 */
    @DeleteMapping("/{id}/students/{studentId}")
    @Transactional
    public ApiResult<Integer> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        ClassGroup cg = classGroupMapper.selectById(id);
        if (cg == null) {
            return ApiResult.fail("班级不存在");
        }
        classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, id)
                .eq(ClassStudent::getStudentId, studentId));
        // 报读同步解绑，避免班级成员与报读指向不一致
        if (cg.getCourseId() != null) {
            enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getStudentId, studentId)
                            .eq(Enrollment::getCourseId, cg.getCourseId())
                            .eq(Enrollment::getClassId, id))
                    .forEach(enrollment -> {
                        enrollment.setClassId(null);
                        enrollment.setUpdatedAt(LocalDateTime.now());
                        enrollmentMapper.updateById(enrollment);
                    });
        }
        return ApiResult.ok(refreshEnrolled(id));
    }

    private Enrollment activeEnrollment(Long studentId, Long courseId) {
        if (courseId == null) {
            return null;
        }
        return enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getStatus, "生效")
                        .orderByDesc(Enrollment::getId))
                .stream().findFirst().orElse(null);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 入班绑定学费：报读剩余课时为 0 但仍有剩余学费时，按课时单价把学费折算成课时。
     * 折算后学费即绑定为该报读的课时价值，不能挪用于其他课程（跨课程只能走升期/转课结算）。
     */
    private void bindTuitionToLessons(Enrollment enrollment) {
        if (!"生效".equals(enrollment.getStatus())) {
            return;
        }
        BigDecimal remain = nz(enrollment.getRemainLessons());
        BigDecimal balance = nz(enrollment.getPaidAmount()).subtract(nz(enrollment.getConsumedAmount()));
        BigDecimal unitPrice = nz(enrollment.getUnitPrice());
        if (remain.signum() > 0 || balance.signum() <= 0 || unitPrice.signum() <= 0) {
            return;
        }
        BigDecimal lessons = balance.divide(unitPrice, 0, RoundingMode.DOWN);
        if (lessons.signum() <= 0) {
            return;
        }
        enrollment.setTotalLessons(nz(enrollment.getTotalLessons()).add(lessons));
        enrollment.setRemainLessons(lessons);
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentMapper.updateById(enrollment);

        LessonTxn txn = new LessonTxn();
        txn.setCampusId(enrollment.getCampusId());
        txn.setEnrollmentId(enrollment.getId());
        txn.setStudentId(enrollment.getStudentId());
        txn.setType("学费转课时");
        txn.setLessons(lessons);
        txn.setUnitPrice(unitPrice);
        txn.setBalanceAfter(lessons);
        txn.setBizType("本地办理");
        txn.setNote("入班：剩余学费 " + money(balance) + " 按单价 " + money(unitPrice)
                + " 折算为 " + lessons.stripTrailingZeros().toPlainString() + " 课时");
        txn.setCreatedAt(LocalDateTime.now());
        lessonTxnMapper.insert(txn);
    }

    private static String money(BigDecimal value) {
        return "￥" + value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private int refreshEnrolled(Long classId) {
        ClassGroup cg = classGroupMapper.selectById(classId);
        if (cg == null) {
            return 0;
        }
        Long count = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classId));
        int enrolled = count == null ? 0 : count.intValue();
        cg.setEnrolledCount(enrolled);
        cg.setStuRate(stuRate(enrolled, cg.getCapacity()));
        classGroupMapper.updateById(cg);
        return enrolled;
    }

    private void applyTeacher(ClassGroup cg, Long teacherId) {
        if (teacherId == null) {
            return;
        }
        Staff staff = staffMapper.selectById(teacherId);
        if (staff != null) {
            cg.setTeacherId(staff.getId());
            cg.setTeacherText(staff.getName());
        }
    }

    private Long validCourseId(Long courseId) {
        if (courseId == null || courseMapper.selectById(courseId) == null) {
            return null;
        }
        return courseId;
    }

    private BigDecimal stuRate(int enrolled, Integer capacity) {
        if (capacity == null || capacity <= 0) {
            return null;
        }
        return BigDecimal.valueOf(enrolled * 100.0 / capacity).setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
