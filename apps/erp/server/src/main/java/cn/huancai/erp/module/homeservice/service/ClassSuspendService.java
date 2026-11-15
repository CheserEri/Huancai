package cn.huancai.erp.module.homeservice.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.homeservice.dto.SuspendBatchForm;
import cn.huancai.erp.module.homeservice.entity.ClassSuspend;
import cn.huancai.erp.module.homeservice.entity.WxMessage;
import cn.huancai.erp.module.homeservice.mapper.ClassSuspendMapper;
import cn.huancai.erp.module.homeservice.vo.SuspendCandidateVO;
import cn.huancai.erp.module.homeservice.vo.SuspendVO;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 家校服务-班级休课：按时间段 + 班级筛选待上课课次，批量标记休课，
 * 并可编辑休课原因后向受影响班级家长推送小程序通知。休课不扣减课时。
 */
@Service
public class ClassSuspendService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_REASON = 255;

    private final ClassSuspendMapper suspendMapper;
    private final ScheduleMapper scheduleMapper;
    private final ClassGroupMapper classGroupMapper;
    private final MessageService messageService;
    private final OperatorResolver operatorResolver;

    public ClassSuspendService(ClassSuspendMapper suspendMapper,
                               ScheduleMapper scheduleMapper,
                               ClassGroupMapper classGroupMapper,
                               MessageService messageService,
                               OperatorResolver operatorResolver) {
        this.suspendMapper = suspendMapper;
        this.scheduleMapper = scheduleMapper;
        this.classGroupMapper = classGroupMapper;
        this.messageService = messageService;
        this.operatorResolver = operatorResolver;
    }

    public List<SuspendCandidateVO> candidates(LocalDate start, LocalDate end, List<Long> classIds, String keyword) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("请选择休课的起止日期");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if (classIds == null || classIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个班级");
        }
        return suspendMapper.selectCandidates(start, end, classIds, trim(keyword));
    }

    public IPage<SuspendVO> list(LocalDate start, LocalDate end, Long classId, String status,
                                 long page, long size) {
        Page<SuspendVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return suspendMapper.selectSuspendPage(p, start, end, classId, trim(status));
    }

    /** 批量休课；notify=true 时按受影响班级推送休课通知。 */
    @Transactional
    public Map<String, Object> suspend(SuspendBatchForm form) {
        List<Long> scheduleIds = form.getScheduleIds();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要休课的课次");
        }
        String reason = trim(form.getReason());
        if (reason == null) {
            throw new IllegalArgumentException("请填写休课原因");
        }
        if (reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("休课原因不能超过 " + MAX_REASON + " 个字");
        }

        List<Schedule> schedules = scheduleMapper.selectBatchIds(scheduleIds);
        Map<Long, String> classNames = resolveClassNames(schedules);
        String operator = operatorResolver.resolve(null);
        LocalDateTime now = LocalDateTime.now();

        List<ClassSuspend> created = new ArrayList<>();
        Set<Long> affectedClassIds = new LinkedHashSet<>();
        int skipped = 0;
        for (Schedule schedule : schedules) {
            if (!"待上课".equals(schedule.getStatus())) {
                skipped++;
                continue;
            }
            ClassSuspend record = new ClassSuspend();
            record.setScheduleId(schedule.getId());
            record.setCampusId(schedule.getCampusId());
            record.setClassId(schedule.getClassId());
            record.setClassName(schedule.getClassName() != null && !schedule.getClassName().isBlank()
                    ? schedule.getClassName() : classNames.get(schedule.getClassId()));
            record.setCourseName(schedule.getCourseName());
            record.setTeacherName(schedule.getTeacherName());
            record.setLessonDate(schedule.getLessonDate());
            record.setStartTime(schedule.getStartTime());
            record.setEndTime(schedule.getEndTime());
            record.setReason(reason);
            record.setStatus("已休课");
            record.setNotifyFlag(0);
            record.setOperatorName(operator);
            record.setSuspendedAt(now);
            record.setCreatedAt(now);
            suspendMapper.insert(record);
            created.add(record);
            affectedClassIds.add(schedule.getClassId());

            schedule.setStatus("休课");
            schedule.setUpdatedAt(now);
            scheduleMapper.updateById(schedule);
        }

        if (created.isEmpty()) {
            throw new IllegalArgumentException("所选课次均已上课、已休课或已取消，无法休课");
        }

        Long messageId = null;
        int receiverCount = 0;
        String notifyMessage = null;
        if (Boolean.TRUE.equals(form.getNotify())) {
            List<Long> notifyClassIds = new ArrayList<>(affectedClassIds);
            WxMessage message = messageService.sendMessage(
                    "休课通知",
                    buildNoticeContent(created, reason),
                    "休课通知",
                    "班级",
                    notifyClassIds,
                    null);
            messageId = message.getId();
            receiverCount = message.getReceiverCount() == null ? 0 : message.getReceiverCount();
            for (ClassSuspend record : created) {
                record.setMessageId(messageId);
                record.setNotifyFlag(1);
                suspendMapper.updateById(record);
            }
            if (receiverCount == 0) {
                notifyMessage = "休课通知已生成，受影响班级暂无已绑定家长，家长绑定后可在小程序查看";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suspended", created.size());
        result.put("skipped", skipped);
        result.put("messageId", messageId);
        result.put("receiverCount", receiverCount);
        result.put("notifyMessage", notifyMessage);
        return result;
    }

    /** 对已休课记录批量推送家长通知。 */
    @Transactional
    public Map<String, Object> notify(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要通知的已休课课次");
        }
        List<ClassSuspend> records = suspendMapper.selectBatchIds(ids).stream()
                .filter(record -> "已休课".equals(record.getStatus()))
                .toList();
        if (records.isEmpty()) {
            throw new IllegalArgumentException("所选记录均已恢复，无法发送休课通知");
        }
        List<Long> classIds = records.stream()
                .map(ClassSuspend::getClassId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        WxMessage message = messageService.sendMessage(
                "休课通知",
                buildNoticeContent(records, commonReason(records)),
                "休课通知",
                "班级",
                classIds,
                null);
        Long messageId = message.getId();
        int receiverCount = message.getReceiverCount() == null ? 0 : message.getReceiverCount();
        for (ClassSuspend record : records) {
            record.setMessageId(messageId);
            record.setNotifyFlag(1);
            suspendMapper.updateById(record);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notified", records.size());
        result.put("classCount", classIds.size());
        result.put("messageId", messageId);
        result.put("receiverCount", receiverCount);
        result.put("notifyMessage", receiverCount == 0
                ? "休课通知已生成，所选班级暂无已绑定家长，家长绑定后可在小程序查看" : null);
        return result;
    }

    /** 恢复休课：课次回到待上课，休课记录标记已恢复。 */
    @Transactional
    public void restore(Long id) {
        ClassSuspend record = suspendMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("休课记录不存在");
        }
        if (!"已休课".equals(record.getStatus())) {
            throw new IllegalArgumentException("该记录已恢复，无需重复操作");
        }
        Schedule schedule = scheduleMapper.selectById(record.getScheduleId());
        if (schedule == null) {
            throw new IllegalArgumentException("对应课次不存在，无法恢复");
        }
        if (!"休课".equals(schedule.getStatus()) && !"已取消".equals(schedule.getStatus())) {
            throw new IllegalArgumentException("该课次当前状态为「" + schedule.getStatus() + "」，无法恢复");
        }
        LocalDateTime now = LocalDateTime.now();
        schedule.setStatus("待上课");
        schedule.setUpdatedAt(now);
        scheduleMapper.updateById(schedule);

        record.setStatus("已恢复");
        record.setRestoredAt(now);
        record.setRestoredBy(operatorResolver.resolve(null));
        suspendMapper.updateById(record);
    }

    private Map<Long, String> resolveClassNames(List<Schedule> schedules) {
        Set<Long> classIds = new LinkedHashSet<>();
        for (Schedule schedule : schedules) {
            if (schedule.getClassId() != null) {
                classIds.add(schedule.getClassId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (classIds.isEmpty()) {
            return names;
        }
        for (ClassGroup group : classGroupMapper.selectBatchIds(classIds)) {
            names.put(group.getId(), group.getName());
        }
        return names;
    }

    private String buildNoticeContent(List<ClassSuspend> records, String reason) {
        StringBuilder content = new StringBuilder("以下课程已休课，请留意后续上课安排：\n");
        for (ClassSuspend record : records) {
            content.append(formatDate(record.getLessonDate()))
                    .append(" ")
                    .append(formatTime(record.getStartTime()))
                    .append("-")
                    .append(formatTime(record.getEndTime()))
                    .append(" ")
                    .append(record.getClassName() == null ? "班级课程" : record.getClassName());
            if (record.getCourseName() != null && !record.getCourseName().isBlank()) {
                content.append("（").append(record.getCourseName()).append("）");
            }
            content.append("\n");
        }
        content.append("休课原因：").append(reason);
        String text = content.toString();
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private String commonReason(List<ClassSuspend> records) {
        String reason = null;
        for (ClassSuspend record : records) {
            String current = trim(record.getReason());
            if (current == null) {
                continue;
            }
            if (reason == null) {
                reason = current;
            } else if (!reason.equals(current)) {
                return "课程调整";
            }
        }
        return reason == null ? "课程调整" : reason;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME_FORMAT);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
