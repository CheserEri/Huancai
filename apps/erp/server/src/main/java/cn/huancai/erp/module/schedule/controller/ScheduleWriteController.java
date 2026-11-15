package cn.huancai.erp.module.schedule.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.homeservice.entity.ClassSuspend;
import cn.huancai.erp.module.homeservice.mapper.ClassSuspendMapper;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.schedule.dto.ScheduleForm;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.schedule.vo.ScheduleOptionsVO;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleWriteController {

    private final ScheduleMapper scheduleMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final CampusMapper campusMapper;
    private final CourseMapper courseMapper;
    private final StaffMapper staffMapper;
    private final StudentMapper studentMapper;
    private final ClassSuspendMapper suspendMapper;
    private final OperatorResolver operatorResolver;

    public ScheduleWriteController(ScheduleMapper scheduleMapper,
                                   ClassGroupMapper classGroupMapper,
                                   ClassStudentMapper classStudentMapper,
                                   CampusMapper campusMapper,
                                   CourseMapper courseMapper,
                                   StaffMapper staffMapper,
                                   StudentMapper studentMapper,
                                   ClassSuspendMapper suspendMapper,
                                   OperatorResolver operatorResolver) {
        this.scheduleMapper = scheduleMapper;
        this.classGroupMapper = classGroupMapper;
        this.classStudentMapper = classStudentMapper;
        this.campusMapper = campusMapper;
        this.courseMapper = courseMapper;
        this.staffMapper = staffMapper;
        this.studentMapper = studentMapper;
        this.suspendMapper = suspendMapper;
        this.operatorResolver = operatorResolver;
    }

    @GetMapping("/options")
    public ApiResult<ScheduleOptionsVO> options() {
        ScheduleOptionsVO vo = new ScheduleOptionsVO();
        vo.setCampuses(campusMapper.selectList(new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> new ScheduleOptionsVO.Option(c.getId(), c.getName())).toList());
        vo.setClasses(classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroup>()
                        .orderByAsc(ClassGroup::getName))
                .stream().map(c -> new ScheduleOptionsVO.ClassOption(c.getId(), c.getName(), c.getCampusId(),
                        c.getCourseId(), c.getTeacherId(), c.getTeacherText(), c.getRoom())).toList());
        vo.setCourses(courseMapper.selectList(new LambdaQueryWrapper<Course>().orderByAsc(Course::getName))
                .stream().map(c -> new ScheduleOptionsVO.Option(c.getId(), c.getName())).toList());
        vo.setTeachers(staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .isNotNull(Staff::getName).ne(Staff::getName, "").orderByAsc(Staff::getName))
                .stream().map(s -> new ScheduleOptionsVO.Option(s.getId(), s.getName())).toList());
        vo.setRooms(scheduleMapper.selectRooms());
        return ApiResult.ok(vo);
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody ScheduleForm form) {
        if (form.getLessonDate() == null) {
            return ApiResult.fail("请选择上课日期");
        }
        if (form.getStartTime() == null || form.getEndTime() == null) {
            return ApiResult.fail("请填写上课时间");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            return ApiResult.fail("下课时间必须晚于上课时间");
        }
        if (form.getCampusId() == null) {
            return ApiResult.fail("请选择校区");
        }
        if (campusMapper.selectById(form.getCampusId()) == null) {
            return ApiResult.fail("校区不存在");
        }
        if (form.getClassId() == null && form.getStudentId() == null) {
            return ApiResult.fail("请选择班级或一对一学员");
        }

        Schedule schedule = new Schedule();
        schedule.setCampusId(form.getCampusId());
        schedule.setLessonDate(form.getLessonDate());
        schedule.setStartTime(form.getStartTime());
        schedule.setEndTime(form.getEndTime());
        schedule.setRoom(trimToNull(form.getRoom()));
        schedule.setTopic(trimToNull(form.getTopic()));
        schedule.setStatus("待上课");
        schedule.setLessonMode(form.getLessonMode());

        if (form.getClassId() != null) {
            ClassGroup cg = classGroupMapper.selectById(form.getClassId());
            if (cg == null) {
                return ApiResult.fail("班级不存在");
            }
            schedule.setClassId(cg.getId());
            schedule.setClassName(cg.getName());
            schedule.setCampusId(form.getCampusId());
            Long courseId = form.getCourseId() != null ? form.getCourseId() : cg.getCourseId();
            schedule.setCourseId(courseId);
            if (courseId != null) {
                Course course = courseMapper.selectById(courseId);
                schedule.setCourseName(course == null ? null : course.getName());
            }
            Long teacherId = form.getTeacherId() != null ? form.getTeacherId() : cg.getTeacherId();
            schedule.setTeacherId(teacherId);
            if (teacherId != null) {
                Staff staff = staffMapper.selectById(teacherId);
                schedule.setTeacherName(staff == null ? cg.getTeacherText() : staff.getName());
            } else {
                schedule.setTeacherName(cg.getTeacherText());
            }
            schedule.setAssistantName(trimToNull(cg.getAssistName()));
            if (schedule.getRoom() == null) {
                schedule.setRoom(trimToNull(cg.getRoom()));
            }
            Long count = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, cg.getId()));
            schedule.setShouldCount(count == null ? 0 : count.intValue());
            schedule.setLessonMode(form.getLessonMode() == null ? 1 : form.getLessonMode());

            cg.setScheduleCount((cg.getScheduleCount() == null ? 0 : cg.getScheduleCount()) + 1);
            classGroupMapper.updateById(cg);
        } else {
            Student student = studentMapper.selectById(form.getStudentId());
            if (student == null) {
                return ApiResult.fail("学员不存在");
            }
            schedule.setStudentId(student.getId());
            schedule.setShouldCount(1);
            schedule.setLessonMode(form.getLessonMode() == null ? 2 : form.getLessonMode());
            schedule.setCourseId(form.getCourseId());
            if (form.getCourseId() != null) {
                Course course = courseMapper.selectById(form.getCourseId());
                schedule.setCourseName(course == null ? null : course.getName());
            }
            schedule.setTeacherId(form.getTeacherId());
            if (form.getTeacherId() != null) {
                Staff staff = staffMapper.selectById(form.getTeacherId());
                schedule.setTeacherName(staff == null ? null : staff.getName());
            }
        }
        scheduleMapper.insert(schedule);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", schedule.getId());
        data.put("lessonDate", schedule.getLessonDate());
        data.put("startTime", schedule.getStartTime());
        return ApiResult.ok(data);
    }

    /** 调课：修改日期 / 时间 / 教室 / 教师 / 主题。已上课的课次不允许调整。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody ScheduleForm form) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return ApiResult.fail("排课记录不存在");
        }
        if ("已上课".equals(schedule.getStatus())) {
            return ApiResult.fail("该课次已上课并点名，不能调课");
        }
        if (form.getLessonDate() != null) schedule.setLessonDate(form.getLessonDate());
        if (form.getStartTime() != null) schedule.setStartTime(form.getStartTime());
        if (form.getEndTime() != null) schedule.setEndTime(form.getEndTime());
        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            return ApiResult.fail("下课时间必须晚于上课时间");
        }
        if (form.getRoom() != null) schedule.setRoom(trimToNull(form.getRoom()));
        if (form.getTopic() != null) schedule.setTopic(trimToNull(form.getTopic()));
        if (form.getTeacherId() != null) {
            Staff staff = staffMapper.selectById(form.getTeacherId());
            if (staff == null) {
                return ApiResult.fail("教师不存在");
            }
            schedule.setTeacherId(staff.getId());
            schedule.setTeacherName(staff.getName());
        }
        if (form.getCampusId() != null) {
            if (campusMapper.selectById(form.getCampusId()) == null) {
                return ApiResult.fail("校区不存在");
            }
            schedule.setCampusId(form.getCampusId());
        }
        scheduleMapper.updateById(schedule);
        return ApiResult.ok(null);
    }

    /** 停课。 */
    @PostMapping("/{id}/cancel")
    public ApiResult<Void> cancel(@PathVariable Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return ApiResult.fail("排课记录不存在");
        }
        if ("已上课".equals(schedule.getStatus())) {
            return ApiResult.fail("该课次已上课并点名，不能停课");
        }
        if ("已取消".equals(schedule.getStatus())) {
            return ApiResult.fail("该课次已停课");
        }
        schedule.setStatus("已取消");
        scheduleMapper.updateById(schedule);
        createSuspendRecord(schedule);
        refreshClassScheduleCount(schedule.getClassId());
        return ApiResult.ok(null);
    }

    /** 恢复停课。 */
    @PostMapping("/{id}/restore")
    public ApiResult<Void> restore(@PathVariable Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return ApiResult.fail("排课记录不存在");
        }
        if (!"已取消".equals(schedule.getStatus())) {
            return ApiResult.fail("只有已停课的课次可以恢复");
        }
        schedule.setStatus("待上课");
        scheduleMapper.updateById(schedule);
        restoreSuspendRecord(schedule.getId());
        refreshClassScheduleCount(schedule.getClassId());
        return ApiResult.ok(null);
    }

    /** 排课停课时同步生成班级休课记录，家校服务-班级休课可直接查看。 */
    private void createSuspendRecord(Schedule schedule) {
        Long exists = suspendMapper.selectCount(new LambdaQueryWrapper<ClassSuspend>()
                .eq(ClassSuspend::getScheduleId, schedule.getId())
                .eq(ClassSuspend::getStatus, "已休课"));
        if (exists != null && exists > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ClassSuspend record = new ClassSuspend();
        record.setScheduleId(schedule.getId());
        record.setCampusId(schedule.getCampusId());
        record.setClassId(schedule.getClassId());
        String className = schedule.getClassName();
        if ((className == null || className.isBlank()) && schedule.getClassId() != null) {
            ClassGroup group = classGroupMapper.selectById(schedule.getClassId());
            className = group == null ? null : group.getName();
        }
        record.setClassName(className);
        record.setCourseName(schedule.getCourseName());
        record.setTeacherName(schedule.getTeacherName());
        record.setLessonDate(schedule.getLessonDate());
        record.setStartTime(schedule.getStartTime());
        record.setEndTime(schedule.getEndTime());
        record.setReason("排课停课");
        record.setStatus("已休课");
        record.setNotifyFlag(0);
        record.setOperatorName(operatorResolver.resolve(null));
        record.setSuspendedAt(now);
        record.setCreatedAt(now);
        suspendMapper.insert(record);
    }

    /** 排课恢复时同步把对应的班级休课记录标记为已恢复。 */
    private void restoreSuspendRecord(Long scheduleId) {
        ClassSuspend patch = new ClassSuspend();
        patch.setStatus("已恢复");
        patch.setRestoredAt(LocalDateTime.now());
        patch.setRestoredBy(operatorResolver.resolve(null));
        suspendMapper.update(patch, new LambdaQueryWrapper<ClassSuspend>()
                .eq(ClassSuspend::getScheduleId, scheduleId)
                .eq(ClassSuspend::getStatus, "已休课"));
    }

    private void refreshClassScheduleCount(Long classId) {
        if (classId == null) {
            return;
        }
        ClassGroup cg = classGroupMapper.selectById(classId);
        if (cg == null) {
            return;
        }
        Long count = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getClassId, classId)
                .ne(Schedule::getStatus, "已取消"));
        cg.setScheduleCount(count == null ? 0 : count.intValue());
        classGroupMapper.updateById(cg);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
