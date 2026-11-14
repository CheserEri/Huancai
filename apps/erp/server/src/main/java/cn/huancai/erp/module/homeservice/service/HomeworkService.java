package cn.huancai.erp.module.homeservice.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.homeservice.dto.HomeworkForm;
import cn.huancai.erp.module.homeservice.entity.ClassHomework;
import cn.huancai.erp.module.homeservice.mapper.ClassHomeworkMapper;
import cn.huancai.erp.module.homeservice.vo.HomeworkVO;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家校服务-班级作业：本地闭环维护作业发布记录。 */
@Service
public class HomeworkService {

    private static final int MAX_TITLE = 128;
    private static final int MAX_CONTENT = 1000;

    private final ClassHomeworkMapper homeworkMapper;
    private final ClassGroupMapper classGroupMapper;
    private final OperatorResolver operatorResolver;

    public HomeworkService(ClassHomeworkMapper homeworkMapper,
                           ClassGroupMapper classGroupMapper,
                           OperatorResolver operatorResolver) {
        this.homeworkMapper = homeworkMapper;
        this.classGroupMapper = classGroupMapper;
        this.operatorResolver = operatorResolver;
    }

    public IPage<HomeworkVO> list(Long classId, String keyword, LocalDate start, LocalDate end,
                                  long page, long size) {
        Page<HomeworkVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return homeworkMapper.selectHomeworkPage(p, classId, trim(keyword), start, end);
    }

    public ClassHomework create(HomeworkForm form) {
        ClassHomework homework = new ClassHomework();
        applyForm(homework, form);
        LocalDateTime now = LocalDateTime.now();
        homework.setPublisher(operatorResolver.resolve(null));
        homework.setStatus("已发布");
        homework.setCreatedAt(now);
        homeworkMapper.insert(homework);
        return homework;
    }

    public void update(Long id, HomeworkForm form) {
        ClassHomework homework = homeworkMapper.selectById(id);
        if (homework == null) {
            throw new IllegalArgumentException("作业记录不存在或已被删除");
        }
        applyForm(homework, form);
        homework.setUpdatedAt(LocalDateTime.now());
        homeworkMapper.updateById(homework);
    }

    public void delete(Long id) {
        if (homeworkMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("作业记录不存在或已被删除");
        }
    }

    private void applyForm(ClassHomework homework, HomeworkForm form) {
        String title = trim(form.getTitle());
        String content = trim(form.getContent());
        if (form.getClassId() == null) {
            throw new IllegalArgumentException("请选择班级");
        }
        if (title == null) {
            throw new IllegalArgumentException("请填写作业标题");
        }
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("作业标题不能超过 " + MAX_TITLE + " 个字");
        }
        if (content == null) {
            throw new IllegalArgumentException("请填写作业内容");
        }
        if (content.length() > MAX_CONTENT) {
            throw new IllegalArgumentException("作业内容不能超过 " + MAX_CONTENT + " 个字");
        }
        if (form.getDueDate() != null && form.getPublishDate() != null
                && form.getDueDate().isBefore(form.getPublishDate())) {
            throw new IllegalArgumentException("截止日期不能早于发布日期");
        }
        ClassGroup group = classGroupMapper.selectById(form.getClassId());
        if (group == null) {
            throw new IllegalArgumentException("班级不存在");
        }
        homework.setCampusId(group.getCampusId());
        homework.setClassId(group.getId());
        homework.setClassName(group.getName());
        homework.setTeacherId(group.getTeacherId());
        homework.setTeacherName(group.getTeacherText());
        homework.setTitle(title);
        homework.setContent(content);
        homework.setDueDate(form.getDueDate());
        homework.setPublishDate(form.getPublishDate() == null ? LocalDate.now() : form.getPublishDate());
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
