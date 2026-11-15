package cn.huancai.erp.module.homeservice.service;

import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.homeservice.dto.PosterCreateForm;
import cn.huancai.erp.module.homeservice.entity.ActivityPoster;
import cn.huancai.erp.module.homeservice.entity.ActivityPosterImage;
import cn.huancai.erp.module.homeservice.entity.WxMessage;
import cn.huancai.erp.module.homeservice.mapper.ActivityPosterImageMapper;
import cn.huancai.erp.module.homeservice.mapper.ActivityPosterMapper;
import cn.huancai.erp.module.homeservice.mapper.WxMessageMapper;
import cn.huancai.erp.module.homeservice.vo.MessageParentVO;
import cn.huancai.erp.module.homeservice.vo.MessageReceiverVO;
import cn.huancai.erp.module.homeservice.vo.MessageStudentVO;
import cn.huancai.erp.module.homeservice.vo.PosterImageVO;
import cn.huancai.erp.module.homeservice.vo.PosterVO;
import cn.huancai.erp.module.wxapp.service.WxSubscribeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 家校服务-活动海报推送：一次推送带 1~9 张海报，按年级 / 班级 / 学员并集圈定学员，
 * 复用消息推送链路生成家长消息通知与接收记录，已读统计取消息接收表的已读条数。
 */
@Service
public class ActivityPosterService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityPosterService.class);
    private static final int MAX_TITLE = 128;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_GRADE_TEXT = 255;
    private static final int MAX_CLASS_TEXT = 512;
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final String UPLOAD_PREFIX = "/uploads/";
    private static final String URL_PREFIX = "/uploads/activity-poster/";
    private static final String MSG_TYPE = "活动海报";

    private final ActivityPosterMapper posterMapper;
    private final ActivityPosterImageMapper imageMapper;
    private final WxMessageMapper messageMapper;
    private final MessageService messageService;
    private final OperatorResolver operatorResolver;
    private final WxSubscribeService subscribeService;
    private final Path root;

    public ActivityPosterService(ActivityPosterMapper posterMapper,
                                 ActivityPosterImageMapper imageMapper,
                                 WxMessageMapper messageMapper,
                                 MessageService messageService,
                                 OperatorResolver operatorResolver,
                                 WxSubscribeService subscribeService,
                                 @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.posterMapper = posterMapper;
        this.imageMapper = imageMapper;
        this.messageMapper = messageMapper;
        this.messageService = messageService;
        this.operatorResolver = operatorResolver;
        this.subscribeService = subscribeService;
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public IPage<PosterVO> list(String keyword, LocalDate start, LocalDate end, long page, long size) {
        Page<PosterVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return posterMapper.selectPosterPage(p, trim(keyword), start, end);
    }

    public List<PosterImageVO> images(Long posterId) {
        return posterMapper.selectImages(posterId);
    }

    public IPage<MessageReceiverVO> receivers(Long posterId, Integer readFlag, long page, long size) {
        ActivityPoster poster = posterMapper.selectById(posterId);
        if (poster == null || poster.getMessageId() == null) {
            return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        }
        return messageService.receivers(poster.getMessageId(), readFlag, page, size);
    }

    /** 上传一张海报，返回可直接写入推送表单的访问地址。 */
    public PosterImageVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的海报");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("单张海报不能超过 10MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、GIF 格式的图片");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String month = LocalDate.now().toString().substring(0, 7).replace("-", "");
        Path dir = root.resolve("activity-poster").resolve(month);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("海报保存失败，请稍后重试", e);
        }
        PosterImageVO vo = new PosterImageVO();
        vo.setUrl(URL_PREFIX + month + "/" + fileName);
        return vo;
    }

    /** 新建活动海报推送。 */
    @Transactional
    public PosterVO create(PosterCreateForm form) {
        String title = trim(form.getTitle());
        if (title == null) {
            throw new IllegalArgumentException("请填写活动名称");
        }
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("活动名称不能超过 " + MAX_TITLE + " 个字");
        }
        String description = trim(form.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("推送说明不能超过 " + MAX_DESCRIPTION + " 个字");
        }

        List<String> imageUrls = new ArrayList<>();
        if (form.getImageUrls() != null) {
            for (String url : new LinkedHashSet<>(form.getImageUrls())) {
                String value = trim(url);
                if (value == null) {
                    continue;
                }
                if (!value.startsWith(URL_PREFIX)) {
                    throw new IllegalArgumentException("海报地址不合法，请重新上传");
                }
                imageUrls.add(value);
            }
        }
        if (imageUrls.isEmpty()) {
            throw new IllegalArgumentException("请至少上传一张活动海报");
        }
        if (imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("一次推送最多 " + MAX_IMAGES + " 张海报");
        }

        List<String> grades = distinctTexts(form.getGrades());
        List<Long> classIds = distinctIds(form.getClassIds());
        List<Long> studentIds = distinctIds(form.getStudentIds());
        if (grades.isEmpty() && classIds.isEmpty() && studentIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择年级、班级或学员中的一项");
        }

        LocalDate startDate = parseDate(form.getStartDate(), "开始日期");
        LocalDate endDate = parseDate(form.getEndDate(), "结束日期");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }

        List<MessageStudentVO> students = messageMapper.selectStudentsByAudience(grades, classIds, studentIds);
        if (students.isEmpty()) {
            throw new IllegalArgumentException("所选范围没有可推送的学员，请检查年级、班级或学员");
        }

        String classNameText = null;
        if (!classIds.isEmpty()) {
            List<String> names = messageMapper.selectClassNames(classIds);
            classNameText = names.isEmpty() ? null : cut(String.join("、", names), MAX_CLASS_TEXT);
        }
        String gradeText = grades.isEmpty() ? null : cut(String.join("、", grades), MAX_GRADE_TEXT);
        String audienceType = audienceLabel(grades, classIds, studentIds);

        String content = description == null ? "点击查看活动海报" : description;
        WxMessage message = messageService.sendToStudents(title, content, MSG_TYPE, audienceType,
                classNameText, students);

        LocalDateTime now = LocalDateTime.now();
        ActivityPoster poster = new ActivityPoster();
        poster.setTitle(title);
        poster.setDescription(description);
        poster.setAudienceType(audienceType);
        poster.setGradeText(gradeText);
        poster.setClassNameText(classNameText);
        poster.setTargetCount(students.size());
        poster.setReceiverCount(message.getReceiverCount());
        poster.setMessageId(message.getId());
        poster.setSender(message.getSender());
        poster.setStartDate(startDate);
        poster.setEndDate(endDate);
        poster.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
        poster.setCreatedAt(now);
        posterMapper.insert(poster);

        for (int i = 0; i < imageUrls.size(); i++) {
            ActivityPosterImage image = new ActivityPosterImage();
            image.setPosterId(poster.getId());
            image.setUrl(imageUrls.get(i));
            image.setSortNo(i);
            image.setCreatedAt(now);
            imageMapper.insert(image);
        }
        posterMapper.insertPosterTargets(poster.getId(), students);
        notifySubscribe(students, title, classNameText, gradeText);

        PosterVO vo = toVO(poster);
        vo.setImageCount(imageUrls.size());
        vo.setCoverUrl(imageUrls.get(0));
        vo.setReadCount(0);
        return vo;
    }

    /** 删除推送记录：海报、目标学员、关联消息与接收记录、磁盘文件一并清理。 */
    @Transactional
    public boolean delete(Long id) {
        ActivityPoster poster = posterMapper.selectById(id);
        if (poster == null) {
            return false;
        }
        List<PosterImageVO> images = posterMapper.selectImages(id);
        posterMapper.deletePosterImages(id);
        posterMapper.deletePosterTargets(id);
        posterMapper.deleteById(id);
        Long messageId = poster.getMessageId();
        if (messageId != null) {
            posterMapper.deleteMessageTargets(messageId);
            posterMapper.deleteMessageReceivers(messageId);
            posterMapper.deleteMessage(messageId);
        }
        for (PosterImageVO image : images) {
            deleteFile(image.getUrl());
        }
        return true;
    }

    /** 活动海报落库后按家长授权额度补发微信订阅消息，失败只记日志，不影响推送结果。 */
    private void notifySubscribe(List<MessageStudentVO> students, String title,
                                 String classNameText, String gradeText) {
        try {
            List<Long> studentIds = new ArrayList<>();
            for (MessageStudentVO student : students) {
                studentIds.add(student.getId());
            }
            Set<Long> parentIds = new LinkedHashSet<>();
            for (MessageParentVO parent : messageMapper.selectParentsByStudentIds(studentIds)) {
                parentIds.add(parent.getParentId());
            }
            String place = classNameText != null ? classNameText : gradeText;
            subscribeService.sendActivityPoster(parentIds, title, place);
        } catch (Exception ex) {
            LOG.warn("活动海报订阅消息通知失败：title={}", title, ex);
        }
    }

    private PosterVO toVO(ActivityPoster poster) {
        PosterVO vo = new PosterVO();
        vo.setId(poster.getId());
        vo.setTitle(poster.getTitle());
        vo.setDescription(poster.getDescription());
        vo.setAudienceType(poster.getAudienceType());
        vo.setGradeText(poster.getGradeText());
        vo.setClassNameText(poster.getClassNameText());
        vo.setTargetCount(poster.getTargetCount());
        vo.setReceiverCount(poster.getReceiverCount());
        vo.setMessageId(poster.getMessageId());
        vo.setSender(poster.getSender());
        vo.setStartDate(poster.getStartDate());
        vo.setEndDate(poster.getEndDate());
        vo.setSentAt(poster.getSentAt());
        return vo;
    }

    private String audienceLabel(List<String> grades, List<Long> classIds, List<Long> studentIds) {
        List<String> labels = new ArrayList<>();
        if (!grades.isEmpty()) {
            labels.add("年级");
        }
        if (!classIds.isEmpty()) {
            labels.add("班级");
        }
        if (!studentIds.isEmpty()) {
            labels.add("学员");
        }
        return labels.isEmpty() ? "学员" : String.join("+", labels);
    }

    private List<String> distinctTexts(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : new LinkedHashSet<>(values)) {
            String text = trim(value);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private List<Long> distinctIds(List<Long> values) {
        List<Long> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Long value : new LinkedHashSet<>(values)) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private LocalDate parseDate(String value, String label) {
        String text = trim(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    private String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void deleteFile(String url) {
        if (url == null || !url.startsWith(UPLOAD_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(UPLOAD_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 文件不存在时不影响记录删除
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
