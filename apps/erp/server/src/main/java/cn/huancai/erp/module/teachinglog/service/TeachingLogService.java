package cn.huancai.erp.module.teachinglog.service;

import cn.huancai.erp.module.order.dto.LocalOrderRequests;
import cn.huancai.erp.module.order.service.LocalOrderService;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.teachinglog.dto.TeachingLogRequests;
import cn.huancai.erp.module.teachinglog.mapper.TeachingLogMapper;
import cn.huancai.erp.module.teachinglog.vo.TeachingLogRowVO;
import cn.huancai.erp.module.teachinglog.vo.TeachingLogStudentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeachingLogService {

    private final TeachingLogMapper teachingLogMapper;
    private final ScheduleMapper scheduleMapper;
    private final LocalOrderService localOrderService;

    public TeachingLogService(TeachingLogMapper teachingLogMapper,
                              ScheduleMapper scheduleMapper,
                              LocalOrderService localOrderService) {
        this.teachingLogMapper = teachingLogMapper;
        this.scheduleMapper = scheduleMapper;
        this.localOrderService = localOrderService;
    }

    public List<TeachingLogRowVO> list(LocalDate date, Boolean supplement, Long campusId,
                                       String room, Integer lessonMode, Long courseId,
                                       String teacher, String keyword, String recordStatus) {
        boolean makeUp = Boolean.TRUE.equals(supplement);
        return teachingLogMapper.selectRows(makeUp ? null : date, LocalDate.now(), makeUp,
                campusId, room, lessonMode, courseId, teacher, keyword, recordStatus);
    }

    public List<TeachingLogStudentVO> roster(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getClassId() == null) {
            throw new IllegalArgumentException("课次不存在或不是班级课次");
        }
        return teachingLogMapper.selectRoster(scheduleId, schedule.getClassId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Long scheduleId, TeachingLogRequests.Save request) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new IllegalArgumentException("课次不存在");
        }
        if (schedule.getClassId() == null) {
            throw new IllegalArgumentException("该课次不是班级课次，无法按班级记上课");
        }
        if (teachingLogMapper.countAttendance(scheduleId) > 0) {
            throw new IllegalArgumentException("该课次已记上课，不能重复记录");
        }
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new IllegalArgumentException("请先填写学员出勤");
        }

        int present = 0;
        for (TeachingLogRequests.Record record : request.records()) {
            String status = normalizeStatus(record.status());
            BigDecimal lessons = status.equals("出勤") || status.equals("迟到")
                    ? (record.lessons() == null ? BigDecimal.ONE : record.lessons())
                    : BigDecimal.ZERO;
            if (lessons.signum() < 0) {
                throw new IllegalArgumentException("扣减课时不能为负数");
            }
            if (lessons.signum() > 0) {
                if (record.enrollmentId() == null) {
                    throw new IllegalArgumentException("存在未绑定有效报读的学员，无法扣减课时");
                }
                localOrderService.consume(new LocalOrderRequests.Consume(
                        record.studentId(), record.enrollmentId(), lessons, null,
                        "记上课 " + schedule.getLessonDate()));
            }
            if (status.equals("出勤") || status.equals("迟到")) {
                present++;
            }
            teachingLogMapper.insertAttendance(
                    schedule.getCampusId(), scheduleId, schedule.getClassId(),
                    record.studentId(), record.enrollmentId(), schedule.getTeacherId(),
                    schedule.getLessonDate(), lessons, status, record.remark(),
                    LocalDateTime.now());
        }

        schedule.setStatus("已上课");
        schedule.setActualCount(present);
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleMapper.updateById(schedule);

        Map<String, Object> result = new HashMap<>();
        result.put("recorded", request.records().size());
        result.put("present", present);
        return result;
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        return switch (value) {
            case "到课", "出勤" -> "出勤";
            case "迟到" -> "迟到";
            case "请假" -> "请假";
            case "旷课", "缺席" -> "缺席";
            default -> throw new IllegalArgumentException("无效的出勤状态：" + status);
        };
    }
}
