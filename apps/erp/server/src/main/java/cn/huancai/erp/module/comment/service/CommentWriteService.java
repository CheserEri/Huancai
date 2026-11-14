package cn.huancai.erp.module.comment.service;

import cn.huancai.erp.module.comment.dto.CommentBatchForm;
import cn.huancai.erp.module.comment.entity.ClassComment;
import cn.huancai.erp.module.comment.mapper.CommentMapper;
import cn.huancai.erp.module.comment.vo.ClassCommentRowVO;
import cn.huancai.erp.module.comment.vo.CommentRosterVO;
import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.mapper.ScheduleMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 家校服务-课堂点评：按班级课次录入、修改与删除点评。 */
@Service
public class CommentWriteService {

    private static final int MAX_CONTENT = 1000;
    private static final int MAX_RATING = 16;

    private final CommentMapper commentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ScheduleMapper scheduleMapper;

    public CommentWriteService(CommentMapper commentMapper,
                               ClassGroupMapper classGroupMapper,
                               ScheduleMapper scheduleMapper) {
        this.commentMapper = commentMapper;
        this.classGroupMapper = classGroupMapper;
        this.scheduleMapper = scheduleMapper;
    }

    public List<ClassCommentRowVO> list(Long classId, LocalDate start, LocalDate end) {
        if (classId == null) {
            throw new IllegalArgumentException("请选择班级");
        }
        return commentMapper.selectClassCommentRows(classId, start, end);
    }

    public List<CommentRosterVO> roster(Long classId, Long scheduleId) {
        if (classId == null || scheduleId == null) {
            throw new IllegalArgumentException("请选择班级与课次");
        }
        return commentMapper.selectRoster(classId, scheduleId);
    }

    /** 批量保存某课次的学员点评；评分与内容都为空时视为删除该学员点评。 */
    @Transactional
    public int saveBatch(CommentBatchForm form) {
        if (form.getClassId() == null) {
            throw new IllegalArgumentException("请选择班级");
        }
        if (form.getScheduleId() == null) {
            throw new IllegalArgumentException("请选择课次");
        }
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new IllegalArgumentException("没有需要保存的点评");
        }
        ClassGroup group = classGroupMapper.selectById(form.getClassId());
        if (group == null) {
            throw new IllegalArgumentException("班级不存在");
        }
        Schedule schedule = scheduleMapper.selectById(form.getScheduleId());
        if (schedule == null || !form.getClassId().equals(schedule.getClassId())) {
            throw new IllegalArgumentException("课次不属于该班级");
        }
        Long teacherId = schedule.getTeacherId() == null ? group.getTeacherId() : schedule.getTeacherId();
        Long campusId = schedule.getCampusId() == null ? group.getCampusId() : schedule.getCampusId();

        int saved = 0;
        for (CommentBatchForm.Item item : form.getItems()) {
            if (item == null || item.getStudentId() == null) {
                continue;
            }
            String content = trim(item.getContent());
            String rating = trim(item.getRating());
            if (content != null && content.length() > MAX_CONTENT) {
                throw new IllegalArgumentException("点评内容不能超过 " + MAX_CONTENT + " 个字");
            }
            if (rating != null && rating.length() > MAX_RATING) {
                throw new IllegalArgumentException("评价标签不能超过 " + MAX_RATING + " 个字");
            }
            ClassComment existing = findExisting(form.getClassId(), form.getScheduleId(), item.getStudentId());
            if (content == null && rating == null) {
                if (existing != null) {
                    commentMapper.deleteById(existing.getId());
                }
                continue;
            }
            if (existing == null) {
                ClassComment comment = new ClassComment();
                comment.setCampusId(campusId);
                comment.setScheduleId(form.getScheduleId());
                comment.setClassId(form.getClassId());
                comment.setStudentId(item.getStudentId());
                comment.setTeacherId(teacherId);
                comment.setContent(content);
                comment.setRating(rating);
                comment.setCreatedAt(LocalDateTime.now());
                commentMapper.insert(comment);
            } else {
                existing.setContent(content);
                existing.setRating(rating);
                if (teacherId != null) {
                    existing.setTeacherId(teacherId);
                }
                commentMapper.updateById(existing);
            }
            saved++;
        }
        return saved;
    }

    private ClassComment findExisting(Long classId, Long scheduleId, Long studentId) {
        List<ClassComment> rows = commentMapper.selectList(new LambdaQueryWrapper<ClassComment>()
                .eq(ClassComment::getClassId, classId)
                .eq(ClassComment::getScheduleId, scheduleId)
                .eq(ClassComment::getStudentId, studentId)
                .orderByAsc(ClassComment::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
