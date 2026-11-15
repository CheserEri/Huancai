package cn.huancai.erp.module.homeservice.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.homeservice.dto.MessageSendForm;
import cn.huancai.erp.module.homeservice.entity.WxMessage;
import cn.huancai.erp.module.homeservice.entity.WxMessageReceiver;
import cn.huancai.erp.module.homeservice.mapper.WxMessageMapper;
import cn.huancai.erp.module.homeservice.mapper.WxMessageReceiverMapper;
import cn.huancai.erp.module.homeservice.vo.MessageParentVO;
import cn.huancai.erp.module.homeservice.vo.MessageReceiverVO;
import cn.huancai.erp.module.homeservice.vo.MessageStudentVO;
import cn.huancai.erp.module.homeservice.vo.MessageVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 家校服务-消息推送：按全员 / 班级 / 学员圈定目标学员，
 * 再为已绑定微信小程序的家长生成接收记录，家长端小程序按接收记录展示消息。
 */
@Service
public class MessageService {

    private static final Set<String> AUDIENCE_TYPES = Set.of("全员", "班级", "学员");
    private static final int MAX_TITLE = 128;
    private static final int MAX_CONTENT = 1000;

    private final WxMessageMapper messageMapper;
    private final WxMessageReceiverMapper receiverMapper;
    private final OperatorResolver operatorResolver;

    public MessageService(WxMessageMapper messageMapper,
                          WxMessageReceiverMapper receiverMapper,
                          OperatorResolver operatorResolver) {
        this.messageMapper = messageMapper;
        this.receiverMapper = receiverMapper;
        this.operatorResolver = operatorResolver;
    }

    public IPage<MessageVO> list(String keyword, String msgType, LocalDate start, LocalDate end,
                                 long page, long size) {
        Page<MessageVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return messageMapper.selectMessagePage(p, trim(keyword), trim(msgType), start, end);
    }

    public IPage<MessageReceiverVO> receivers(Long messageId, Integer readFlag, long page, long size) {
        Page<MessageReceiverVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return messageMapper.selectReceiverPage(p, messageId, readFlag);
    }

    /** 手动新建推送：校验并落库。 */
    public WxMessage send(MessageSendForm form) {
        String title = trim(form.getTitle());
        String content = trim(form.getContent());
        if (title == null) {
            throw new IllegalArgumentException("请填写消息标题");
        }
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("消息标题不能超过 " + MAX_TITLE + " 个字");
        }
        if (content == null) {
            throw new IllegalArgumentException("请填写消息内容");
        }
        if (content.length() > MAX_CONTENT) {
            throw new IllegalArgumentException("消息内容不能超过 " + MAX_CONTENT + " 个字");
        }
        String audienceType = trim(form.getAudienceType());
        if (audienceType == null || !AUDIENCE_TYPES.contains(audienceType)) {
            throw new IllegalArgumentException("推送范围只能是「全员」「班级」「学员」");
        }
        if ("班级".equals(audienceType) && (form.getClassIds() == null || form.getClassIds().isEmpty())) {
            throw new IllegalArgumentException("请选择要推送的班级");
        }
        if ("学员".equals(audienceType) && (form.getStudentIds() == null || form.getStudentIds().isEmpty())) {
            throw new IllegalArgumentException("请选择要推送的学员");
        }
        return sendMessage(title, content, "系统通知", audienceType, form.getClassIds(), form.getStudentIds());
    }

    /**
     * 消息落库并生成家长接收记录。
     *
     * @return 已写入的消息主记录（含目标学员数、触达家长数）
     */
    @Transactional
    public WxMessage sendMessage(String title, String content, String msgType, String audienceType,
                                 List<Long> classIds, List<Long> studentIds) {
        List<MessageStudentVO> students = resolveStudents(audienceType, classIds, studentIds);
        String classNameText = null;
        if (classIds != null && !classIds.isEmpty()) {
            List<String> names = messageMapper.selectClassNames(classIds);
            classNameText = names.isEmpty() ? null : String.join("、", names);
        }
        return sendToStudents(title, content, msgType, audienceType, classNameText, students);
    }

    /**
     * 指定目标学员落库并生成家长接收记录，供消息推送与活动海报推送复用。
     *
     * @return 已写入的消息主记录（含目标学员数、触达家长数）
     */
    @Transactional
    public WxMessage sendToStudents(String title, String content, String msgType, String audienceType,
                                    String classNameText, List<MessageStudentVO> students) {
        if (students == null || students.isEmpty()) {
            throw new IllegalArgumentException("所选范围没有可推送的学员，请检查班级或学员");
        }

        Map<Long, String> studentNames = new HashMap<>();
        for (MessageStudentVO student : students) {
            studentNames.put(student.getId(), student.getName());
        }
        // 当前已成立绑定关系的家长只用于统计触达，未绑定不影响消息生成
        Set<String> seen = new HashSet<>();
        List<MessageParentVO> parents = new ArrayList<>();
        List<Long> ids = new ArrayList<>(studentNames.keySet());
        for (MessageParentVO parent : messageMapper.selectParentsByStudentIds(ids)) {
            if (seen.add(parent.getParentId() + "-" + parent.getStudentId())) {
                parents.add(parent);
            }
        }

        WxMessage message = new WxMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMsgType(msgType);
        message.setAudienceType(audienceType);
        message.setClassNameText(classNameText);
        message.setTargetCount(students.size());
        message.setReceiverCount(parents.size());
        message.setSender(operatorResolver.resolve(null));
        LocalDateTime now = LocalDateTime.now();
        message.setSentAt(now);
        message.setCreatedAt(now);
        messageMapper.insert(message);
        messageMapper.insertTargets(message.getId(), students);

        for (MessageParentVO parent : parents) {
            WxMessageReceiver receiver = new WxMessageReceiver();
            receiver.setMessageId(message.getId());
            receiver.setParentId(parent.getParentId());
            receiver.setStudentId(parent.getStudentId());
            receiver.setStudentName(studentNames.get(parent.getStudentId()));
            receiver.setReadFlag(0);
            receiver.setCreatedAt(now);
            receiverMapper.insert(receiver);
        }
        return message;
    }

    private List<MessageStudentVO> resolveStudents(String audienceType, List<Long> classIds, List<Long> studentIds) {
        if ("班级".equals(audienceType)) {
            if (classIds == null || classIds.isEmpty()) {
                throw new IllegalArgumentException("请选择要推送的班级");
            }
            return messageMapper.selectClassStudents(classIds);
        }
        if ("学员".equals(audienceType)) {
            if (studentIds == null || studentIds.isEmpty()) {
                throw new IllegalArgumentException("请选择要推送的学员");
            }
            return messageMapper.selectStudentsByIds(studentIds);
        }
        return messageMapper.selectAllStudents();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
