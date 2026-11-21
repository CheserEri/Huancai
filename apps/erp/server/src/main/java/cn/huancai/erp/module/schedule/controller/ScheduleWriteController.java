package cn.huancai.erp.module.schedule.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.homeservice.entity.ClassSuspend;
import cn.huancai.erp.module.homeservice.mapper.ClassSuspendMapper;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.schedule.dto.ScheduleBatchForm;
import cn.huancai.erp.module.schedule.dto.ScheduleForm;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.schedule.vo.ScheduleOptionsVO;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.support.StaffRoles;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
                        c.getCourseId(), c.getTeacherId(), c.getTeacherText(), c.getRoom(),
                        c.getAssistName(), c.getScheduleText())).toList());
        vo.setCourses(courseMapper.selectList(new LambdaQueryWrapper<Course>().orderByAsc(Course::getName))
                .stream().map(c -> new ScheduleOptionsVO.Option(c.getId(), c.getName())).toList());
        vo.setTeachers(staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .isNotNull(Staff::getName).ne(Staff::getName, "").orderByAsc(Staff::getName))
                .stream()
                .filter(s -> s.getStatus() == null || s.getStatus() != 0)
                .filter(s -> "teacher".equals(s.getRole())
                        || StaffRoles.splitExtras(s.getExtraRoles()).contains("teacher"))
                .map(s -> new ScheduleOptionsVO.Option(s.getId(), s.getName())).toList());
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
        if (form.getClassId() == null && form.getStudentId() == null) {
            return ApiResult.fail("请选择班级或一对一学员");
        }

        Schedule schedule;
        if (form.getClassId() != null) {
            ClassGroup cg = classGroupMapper.selectById(form.getClassId());
            if (cg == null) {
                return ApiResult.fail("班级不存在");
            }
            Long campusId = form.getCampusId() != null ? form.getCampusId() : cg.getCampusId();
            if (campusId == null) {
                return ApiResult.fail("班级未设置校区，请先完善班级信息");
            }
            if (campusMapper.selectById(campusId) == null) {
                return ApiResult.fail("校区不存在");
            }
            schedule = buildClassSchedule(cg, campusId, form.getLessonDate(), form.getStartTime(),
                    form.getEndTime(), form.getCourseId(), form.getTeacherId(), form.getAssistantName(),
                    form.getRoom());
            schedule.setTopic(trimToNull(form.getTopic()));
            scheduleMapper.insert(schedule);
            refreshClassScheduleCount(cg.getId());
        } else {
            if (form.getCampusId() == null) {
                return ApiResult.fail("请选择校区");
            }
            if (campusMapper.selectById(form.getCampusId()) == null) {
                return ApiResult.fail("校区不存在");
            }
            Student student = studentMapper.selectById(form.getStudentId());
            if (student == null) {
                return ApiResult.fail("学员不存在");
            }
            schedule = new Schedule();
            schedule.setCampusId(form.getCampusId());
            schedule.setLessonDate(form.getLessonDate());
            schedule.setStartTime(form.getStartTime());
            schedule.setEndTime(form.getEndTime());
            schedule.setRoom(trimToNull(form.getRoom()));
            schedule.setTopic(trimToNull(form.getTopic()));
            schedule.setStatus("待上课");
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
            schedule.setAssistantName(trimToNull(form.getAssistantName()));
            scheduleMapper.insert(schedule);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", schedule.getId());
        data.put("lessonDate", schedule.getLessonDate());
        data.put("startTime", schedule.getStartTime());
        return ApiResult.ok(data);
    }

    /** 重复排课：给已建班级批量添加上课时间，已存在的相同日期时段自动跳过。 */
    @PostMapping("/batch")
    public ApiResult<Map<String, Object>> batchCreate(@RequestBody ScheduleBatchForm form) {
        if (form.getClassId() == null) {
            return ApiResult.fail("请选择班级");
        }
        ClassGroup cg = classGroupMapper.selectById(form.getClassId());
        if (cg == null) {
            return ApiResult.fail("班级不存在");
        }
        Long campusId = cg.getCampusId();
        if (campusId == null) {
            return ApiResult.fail("班级未设置校区，请先完善班级信息");
        }
        if (form.getStartDate() == null) {
            return ApiResult.fail("请选择开始日期");
        }
        if (form.getEndDate() == null) {
            return ApiResult.fail("请选择结束日期");
        }
        if (form.getEndDate().isBefore(form.getStartDate())) {
            return ApiResult.fail("结束日期不能早于开始日期");
        }
        if (form.getStartTime() == null || form.getEndTime() == null) {
            return ApiResult.fail("请选择上课时段");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            return ApiResult.fail("下课时间必须晚于上课时间");
        }
        String repeatType = form.getRepeatType() == null ? "week" : form.getRepeatType();
        if (!"altDay".equals(repeatType)
                && (form.getWeekdays() == null || form.getWeekdays().isEmpty())) {
            return ApiResult.fail("请选择周几上课");
        }

        List<LocalDate> dates = repeatDates(form, repeatType);
        if (dates.isEmpty()) {
            return ApiResult.fail("所选条件下没有可排课的日期");
        }
        if (dates.size() > 366) {
            return ApiResult.fail("单次最多生成 366 节课次，请缩短排课周期");
        }

        Schedule template = buildClassSchedule(cg, campusId, dates.get(0), form.getStartTime(),
                form.getEndTime(), null, form.getTeacherId(), form.getAssistantName(), form.getRoom());

        Set<String> existing = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getClassId, cg.getId())
                        .between(Schedule::getLessonDate, form.getStartDate(), form.getEndDate())
                        .ne(Schedule::getStatus, "已取消"))
                .stream()
                .map(item -> item.getLessonDate() + "|" + item.getStartTime())
                .collect(Collectors.toSet());

        int created = 0;
        int skipped = 0;
        for (LocalDate date : dates) {
            if (existing.contains(date + "|" + form.getStartTime())) {
                skipped++;
                continue;
            }
            Schedule schedule = new Schedule();
            BeanUtils.copyProperties(template, schedule);
            schedule.setLessonDate(date);
            scheduleMapper.insert(schedule);
            created++;
        }
        refreshClassScheduleCount(cg.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("skipped", skipped);
        data.put("total", dates.size());
        data.put("className", cg.getName());
        data.put("firstDate", dates.get(0));
        data.put("lastDate", dates.get(dates.size() - 1));
        return ApiResult.ok(data);
    }

    /** 按班级配置生成课次模板：课程、教师、助教、教室默认取班级信息，可被表单覆盖。 */
    private Schedule buildClassSchedule(ClassGroup cg, Long campusId, LocalDate lessonDate,
                                        LocalTime startTime, LocalTime endTime, Long courseId,
                                        Long teacherId, String assistantName, String room) {
        Schedule schedule = new Schedule();
        schedule.setCampusId(campusId);
        schedule.setLessonDate(lessonDate);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setStatus("待上课");
        schedule.setLessonMode(1);
        schedule.setClassId(cg.getId());
        schedule.setClassName(cg.getName());

        Long finalCourseId = courseId != null ? courseId : cg.getCourseId();
        schedule.setCourseId(finalCourseId);
        if (finalCourseId != null) {
            Course course = courseMapper.selectById(finalCourseId);
            schedule.setCourseName(course == null ? null : course.getName());
        }

        Long finalTeacherId = teacherId != null ? teacherId : cg.getTeacherId();
        schedule.setTeacherId(finalTeacherId);
        if (finalTeacherId != null) {
            Staff staff = staffMapper.selectById(finalTeacherId);
            schedule.setTeacherName(staff == null ? cg.getTeacherText() : staff.getName());
        } else {
            schedule.setTeacherName(cg.getTeacherText());
        }

        String assist = trimToNull(assistantName);
        schedule.setAssistantName(assist != null ? assist : trimToNull(cg.getAssistName()));
        String roomValue = trimToNull(room);
        schedule.setRoom(roomValue != null ? roomValue : trimToNull(cg.getRoom()));

        Long count = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, cg.getId()));
        schedule.setShouldCount(count == null ? 0 : count.intValue());
        return schedule;
    }

    /** 按重复方式生成上课日期，节假日不排课时跳过周六、周日。 */
    private List<LocalDate> repeatDates(ScheduleBatchForm form, String repeatType) {
        List<LocalDate> dates = new ArrayList<>();
        if ("altDay".equals(repeatType)) {
            for (LocalDate date = form.getStartDate(); !date.isAfter(form.getEndDate()); date = date.plusDays(2)) {
                dates.add(date);
            }
        } else {
            Set<Integer> weekdays = normalizeWeekdays(form.getWeekdays());
            LocalDate startWeek = form.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (LocalDate date = form.getStartDate(); !date.isAfter(form.getEndDate()); date = date.plusDays(1)) {
                if (!weekdays.contains(date.getDayOfWeek().getValue())) {
                    continue;
                }
                if ("biweek".equals(repeatType)) {
                    long weeks = ChronoUnit.WEEKS.between(startWeek,
                            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                    if (weeks % 2 != 0) {
                        continue;
                    }
                }
                dates.add(date);
            }
        }
        if (Boolean.TRUE.equals(form.getSkipWeekend())) {
            dates.removeIf(date -> date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY);
        }
        return dates;
    }

    private Set<Integer> normalizeWeekdays(List<Integer> weekdays) {
        Set<Integer> result = new HashSet<>();
        if (weekdays != null) {
            for (Integer day : weekdays) {
                if (day != null && day >= 1 && day <= 7) {
                    result.add(day);
                }
            }
        }
        return result;
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
        if (form.getAssistantName() != null) schedule.setAssistantName(trimToNull(form.getAssistantName()));
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
