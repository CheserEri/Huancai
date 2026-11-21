package cn.huancai.erp.module.grade.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.grade.dto.GradeExamForm;
import cn.huancai.erp.module.grade.dto.GradePartForm;
import cn.huancai.erp.module.grade.entity.GradeExam;
import cn.huancai.erp.module.grade.entity.GradeExamClass;
import cn.huancai.erp.module.grade.entity.GradePart;
import cn.huancai.erp.module.grade.entity.GradeScore;
import cn.huancai.erp.module.grade.mapper.GradeExamClassMapper;
import cn.huancai.erp.module.grade.mapper.GradeExamMapper;
import cn.huancai.erp.module.grade.mapper.GradePartMapper;
import cn.huancai.erp.module.grade.mapper.GradeScoreMapper;
import cn.huancai.erp.module.grade.service.GradeRankService;
import cn.huancai.erp.module.grade.vo.GradeClassOptionVO;
import cn.huancai.erp.module.grade.vo.GradeExamVO;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 考试管理：机构周考、月考与学校考试的新建、编辑、复制、发布、解锁、归档，以及试卷图片上传。
 * 发布后数据锁定，需解锁（留痕）后才能继续录入。
 */
@RestController
@RequestMapping("/api/grades/exams")
public class GradeExamController {

    private static final List<String> ENTERED_STATUS = List.of("正常", "缺考", "免考");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GradeExamMapper examMapper;
    private final GradeExamClassMapper examClassMapper;
    private final GradePartMapper partMapper;
    private final GradeScoreMapper scoreMapper;
    private final GradeRankService rankService;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final CourseMapper courseMapper;
    private final OperatorResolver operatorResolver;
    private final ObjectMapper objectMapper;
    private final String uploadDir;

    public GradeExamController(GradeExamMapper examMapper,
                               GradeExamClassMapper examClassMapper,
                               GradePartMapper partMapper,
                               GradeScoreMapper scoreMapper,
                               GradeRankService rankService,
                               ClassGroupMapper classGroupMapper,
                               ClassStudentMapper classStudentMapper,
                               CourseMapper courseMapper,
                               OperatorResolver operatorResolver,
                               ObjectMapper objectMapper,
                               @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.examMapper = examMapper;
        this.examClassMapper = examClassMapper;
        this.partMapper = partMapper;
        this.scoreMapper = scoreMapper;
        this.rankService = rankService;
        this.classGroupMapper = classGroupMapper;
        this.classStudentMapper = classStudentMapper;
        this.courseMapper = courseMapper;
        this.operatorResolver = operatorResolver;
        this.objectMapper = objectMapper;
        this.uploadDir = uploadDir;
    }

    @GetMapping
    public ApiResult<List<GradeExamVO>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long courseId,
                                             @RequestParam(required = false) Long classId,
                                             @RequestParam(required = false) Long campusId) {
        LambdaQueryWrapper<GradeExam> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(GradeExam::getName, keyword.trim());
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(GradeExam::getType, type.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(GradeExam::getStatus, status.trim());
        }
        if (courseId != null) {
            wrapper.eq(GradeExam::getCourseId, courseId);
        }
        if (campusId != null) {
            wrapper.eq(GradeExam::getCampusId, campusId);
        }
        if (classId != null) {
            List<Long> examIds = examClassMapper.selectList(new LambdaQueryWrapper<GradeExamClass>()
                            .eq(GradeExamClass::getClassId, classId))
                    .stream().map(GradeExamClass::getExamId).toList();
            if (examIds.isEmpty()) {
                return ApiResult.ok(List.of());
            }
            wrapper.in(GradeExam::getId, examIds);
        }
        wrapper.orderByDesc(GradeExam::getExamDate).orderByDesc(GradeExam::getId);
        List<GradeExamVO> list = examMapper.selectList(wrapper).stream().map(this::toVO).toList();
        return ApiResult.ok(list);
    }

    @GetMapping("/class-options")
    public ApiResult<List<GradeClassOptionVO>> classOptions() {
        List<ClassGroup> classes = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroup>()
                .ne(ClassGroup::getStatus, "已结班")
                .orderByAsc(ClassGroup::getName));
        Map<Long, String> courseNames = new HashMap<>();
        List<Long> courseIds = classes.stream().map(ClassGroup::getCourseId)
                .filter(Objects::nonNull).distinct().toList();
        if (!courseIds.isEmpty()) {
            courseMapper.selectBatchIds(courseIds).forEach(course -> courseNames.put(course.getId(), course.getName()));
        }
        List<GradeClassOptionVO> options = new ArrayList<>();
        for (ClassGroup group : classes) {
            GradeClassOptionVO vo = new GradeClassOptionVO();
            vo.setId(group.getId());
            vo.setName(group.getName());
            vo.setCampusId(group.getCampusId());
            vo.setCourseId(group.getCourseId());
            vo.setCourseName(group.getCourseId() == null ? null : courseNames.get(group.getCourseId()));
            options.add(vo);
        }
        return ApiResult.ok(options);
    }

    @GetMapping("/{id}")
    public ApiResult<GradeExamVO> detail(@PathVariable Long id) {
        GradeExam exam = examMapper.selectById(id);
        return exam == null ? ApiResult.fail("考试不存在") : ApiResult.ok(toVO(exam));
    }

    @PostMapping
    @Transactional
    public ApiResult<GradeExamVO> create(@RequestBody GradeExamForm form) {
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        GradeExam exam = new GradeExam();
        apply(exam, form);
        exam.setStatus("草稿");
        String creator = operatorResolver.currentName();
        exam.setCreator(creator == null || creator.isBlank() ? "系统" : creator);
        exam.setCampusId(resolveCampusId(form));
        LocalDateTime now = LocalDateTime.now();
        exam.setCreatedAt(now);
        exam.setUpdatedAt(now);
        examMapper.insert(exam);
        saveRelations(exam.getId(), form.getClassIds());
        saveParts(exam.getId(), form.getParts());
        return ApiResult.ok(toVO(exam));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResult<GradeExamVO> update(@PathVariable Long id, @RequestBody GradeExamForm form) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"草稿".equals(exam.getStatus())) {
            return ApiResult.fail("该考试已" + (("已发布".equals(exam.getStatus())) ? "发布，请先解锁后再编辑" : "归档，不能编辑"));
        }
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        apply(exam, form);
        exam.setCampusId(resolveCampusId(form));
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        saveRelations(id, form.getClassIds());
        saveParts(id, form.getParts());
        return ApiResult.ok(toVO(exam));
    }

    /** 成绩录入页直接调整满分（仅草稿），不影响已配置的分项与成绩。 */
    @PutMapping("/{id}/full-score")
    public ApiResult<GradeExamVO> updateFullScore(@PathVariable Long id,
                                                  @RequestParam BigDecimal fullScore) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"草稿".equals(exam.getStatus())) {
            return ApiResult.fail("该考试已发布，请先解锁后再修改满分");
        }
        if (fullScore == null || fullScore.signum() <= 0) {
            return ApiResult.fail("满分必须大于 0");
        }
        exam.setFullScore(fullScore);
        if (exam.getPassScore() == null || exam.getPassScore().compareTo(fullScore) > 0) {
            exam.setPassScore(fullScore.multiply(BigDecimal.valueOf(0.6)).setScale(1, RoundingMode.HALF_UP));
        }
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        return ApiResult.ok(toVO(exam));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResult<Void> delete(@PathVariable Long id) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"草稿".equals(exam.getStatus())) {
            return ApiResult.fail("只有草稿状态的考试可以删除");
        }
        scoreMapper.delete(new LambdaQueryWrapper<GradeScore>().eq(GradeScore::getExamId, id));
        partMapper.delete(new LambdaQueryWrapper<GradePart>().eq(GradePart::getExamId, id));
        examClassMapper.delete(new LambdaQueryWrapper<GradeExamClass>().eq(GradeExamClass::getExamId, id));
        examMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    /** 复制考试：保留考试信息、适用班级与分项，不带成绩。 */
    @PostMapping("/{id}/copy")
    @Transactional
    public ApiResult<GradeExamVO> copy(@PathVariable Long id) {
        GradeExam source = examMapper.selectById(id);
        if (source == null) {
            return ApiResult.fail("考试不存在");
        }
        GradeExam exam = new GradeExam();
        BeanUtils.copyProperties(source, exam);
        exam.setId(null);
        exam.setName(source.getName() + "（复制）");
        exam.setStatus("草稿");
        String creator = operatorResolver.currentName();
        exam.setCreator(creator == null || creator.isBlank() ? "系统" : creator);
        LocalDateTime now = LocalDateTime.now();
        exam.setCreatedAt(now);
        exam.setUpdatedAt(now);
        examMapper.insert(exam);
        List<Long> classIds = examClassMapper.selectList(new LambdaQueryWrapper<GradeExamClass>()
                        .eq(GradeExamClass::getExamId, id))
                .stream().map(GradeExamClass::getClassId).toList();
        saveRelations(exam.getId(), classIds);
        List<GradePart> parts = partMapper.selectList(new LambdaQueryWrapper<GradePart>()
                .eq(GradePart::getExamId, id).orderByAsc(GradePart::getSort));
        List<GradePartForm> partForms = parts.stream().map(part -> {
            GradePartForm partForm = new GradePartForm();
            partForm.setName(part.getName());
            partForm.setWeight(part.getWeight());
            partForm.setFullScore(part.getFullScore());
            partForm.setSort(part.getSort());
            return partForm;
        }).toList();
        saveParts(exam.getId(), partForms);
        return ApiResult.ok(toVO(exam));
    }

    /** 发布：锁定成绩并计算班级/校区排名。 */
    @PostMapping("/{id}/publish")
    @Transactional
    public ApiResult<GradeExamVO> publish(@PathVariable Long id) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if ("已归档".equals(exam.getStatus())) {
            return ApiResult.fail("已归档的考试不能发布");
        }
        Long count = scoreMapper.selectCount(new LambdaQueryWrapper<GradeScore>()
                .eq(GradeScore::getExamId, id).in(GradeScore::getStatus, ENTERED_STATUS));
        if (count == null || count == 0) {
            return ApiResult.fail("该考试还没有录入任何成绩，无法发布");
        }
        rankService.computeRanks(id);
        exam.setStatus("已发布");
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        return ApiResult.ok(toVO(exam));
    }

    /** 解锁：已发布 → 草稿，并在备注中留痕。 */
    @PostMapping("/{id}/unlock")
    public ApiResult<GradeExamVO> unlock(@PathVariable Long id) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"已发布".equals(exam.getStatus())) {
            return ApiResult.fail("只有已发布的考试需要解锁");
        }
        String operator = operatorResolver.resolve(null);
        String log = "【解锁】" + LocalDateTime.now().format(TIME_FORMAT) + " " + operator + " 解锁修改";
        String remark = exam.getRemark() == null ? "" : exam.getRemark().trim();
        remark = remark.isEmpty() ? log : remark + "；" + log;
        exam.setRemark(remark.length() > 500 ? remark.substring(remark.length() - 500) : remark);
        exam.setStatus("草稿");
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        return ApiResult.ok(toVO(exam));
    }

    @PostMapping("/{id}/archive")
    public ApiResult<GradeExamVO> archive(@PathVariable Long id) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"已发布".equals(exam.getStatus())) {
            return ApiResult.fail("只有已发布的考试可以归档");
        }
        exam.setStatus("已归档");
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        return ApiResult.ok(toVO(exam));
    }

    /** 上传试卷图片：支持手机拍照或本地图片，每场考试最多 9 张。 */
    @PostMapping("/{id}/paper-images")
    @Transactional
    public ApiResult<GradeExamVO> uploadPaperImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"草稿".equals(exam.getStatus())) {
            return ApiResult.fail("请先解锁考试后再修改试卷图片");
        }
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResult.fail("只能上传图片文件");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            return ApiResult.fail("单张图片不能超过 10MB");
        }
        List<String> images = parsePaperImages(exam.getPaperImages());
        if (images.size() >= 9) {
            return ApiResult.fail("每场考试最多上传 9 张试卷图片");
        }
        try {
            Path dir = Paths.get(uploadDir, "grade-paper", String.valueOf(id)).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + imageExtension(file);
            file.transferTo(dir.resolve(filename).toFile());
            images.add("/uploads/grade-paper/" + id + "/" + filename);
            exam.setPaperImages(objectMapper.writeValueAsString(images));
            exam.setUpdatedAt(LocalDateTime.now());
            examMapper.updateById(exam);
        } catch (Exception ex) {
            return ApiResult.fail("上传试卷图片失败：" + ex.getMessage());
        }
        return ApiResult.ok(toVO(exam));
    }

    /** 删除试卷图片（同时清理服务器文件）。 */
    @DeleteMapping("/{id}/paper-images")
    @Transactional
    public ApiResult<GradeExamVO> deletePaperImage(@PathVariable Long id, @RequestParam("url") String url) {
        GradeExam exam = examMapper.selectById(id);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        if (!"草稿".equals(exam.getStatus())) {
            return ApiResult.fail("请先解锁考试后再修改试卷图片");
        }
        List<String> images = parsePaperImages(exam.getPaperImages());
        if (!images.remove(url)) {
            return ApiResult.fail("图片不存在");
        }
        try {
            exam.setPaperImages(objectMapper.writeValueAsString(images));
            exam.setUpdatedAt(LocalDateTime.now());
            examMapper.updateById(exam);
            String prefix = "/uploads/grade-paper/" + id + "/";
            if (url.startsWith(prefix)) {
                Path target = Paths.get(uploadDir, "grade-paper", String.valueOf(id),
                        url.substring(prefix.length())).toAbsolutePath().normalize();
                Files.deleteIfExists(target);
            }
        } catch (IOException ex) {
            return ApiResult.fail("删除试卷图片失败：" + ex.getMessage());
        }
        return ApiResult.ok(toVO(exam));
    }

    private List<String> parsePaperImages(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String imageExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot).toLowerCase();
                if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
                    return ext;
                }
            }
        }
        String contentType = file.getContentType();
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        if ("image/gif".equals(contentType)) {
            return ".gif";
        }
        return ".jpg";
    }

    private GradeExamVO toVO(GradeExam exam) {
        GradeExamVO vo = new GradeExamVO();
        BeanUtils.copyProperties(exam, vo);
        vo.setPaperImages(parsePaperImages(exam.getPaperImages()));
        List<Long> classIds = examClassMapper.selectList(new LambdaQueryWrapper<GradeExamClass>()
                        .eq(GradeExamClass::getExamId, exam.getId()).orderByAsc(GradeExamClass::getId))
                .stream().map(GradeExamClass::getClassId).toList();
        vo.setClassIds(classIds);
        List<String> classNames = new ArrayList<>();
        int studentCount = 0;
        if (!classIds.isEmpty()) {
            Map<Long, String> nameMap = new LinkedHashMap<>();
            classGroupMapper.selectBatchIds(classIds).forEach(group -> nameMap.put(group.getId(), group.getName()));
            for (Long classId : classIds) {
                String name = nameMap.get(classId);
                classNames.add(name == null ? "班级#" + classId : name);
                Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStatus, "在读"));
                studentCount += count == null ? 0 : count.intValue();
            }
        }
        vo.setClassNames(classNames);
        vo.setStudentCount(studentCount);
        List<GradePart> parts = partMapper.selectList(new LambdaQueryWrapper<GradePart>()
                .eq(GradePart::getExamId, exam.getId()).orderByAsc(GradePart::getSort).orderByAsc(GradePart::getId));
        vo.setParts(parts);
        Long entered = scoreMapper.selectCount(new LambdaQueryWrapper<GradeScore>()
                .eq(GradeScore::getExamId, exam.getId()).in(GradeScore::getStatus, ENTERED_STATUS));
        vo.setEnteredCount(entered == null ? 0 : entered.intValue());
        return vo;
    }

    private void saveRelations(Long examId, List<Long> classIds) {
        examClassMapper.delete(new LambdaQueryWrapper<GradeExamClass>().eq(GradeExamClass::getExamId, examId));
        if (classIds == null) {
            return;
        }
        for (Long classId : classIds.stream().filter(Objects::nonNull).distinct().toList()) {
            GradeExamClass relation = new GradeExamClass();
            relation.setExamId(examId);
            relation.setClassId(classId);
            examClassMapper.insert(relation);
        }
    }

    private void saveParts(Long examId, List<GradePartForm> parts) {
        partMapper.delete(new LambdaQueryWrapper<GradePart>().eq(GradePart::getExamId, examId));
        if (parts == null) {
            return;
        }
        int sort = 0;
        for (GradePartForm form : parts) {
            if (form.getName() == null || form.getName().isBlank()) {
                continue;
            }
            GradePart part = new GradePart();
            part.setExamId(examId);
            part.setName(form.getName().trim());
            part.setWeight(form.getWeight() == null ? BigDecimal.ZERO : form.getWeight());
            part.setFullScore(form.getFullScore() == null ? BigDecimal.ZERO : form.getFullScore());
            part.setSort(form.getSort() == null ? sort : form.getSort());
            sort++;
            partMapper.insert(part);
        }
    }

    private String validate(GradeExamForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "请填写考试名称";
        }
        if (form.getClassIds() == null || form.getClassIds().isEmpty()) {
            return "请选择适用班级";
        }
        if (form.getFullScore() != null && form.getFullScore().signum() <= 0) {
            return "满分必须大于 0";
        }
        return null;
    }

    private void apply(GradeExam exam, GradeExamForm form) {
        exam.setName(form.getName().trim());
        exam.setType(form.getType() == null || form.getType().isBlank() ? "周考" : form.getType().trim());
        exam.setCourseId(form.getCourseId());
        exam.setCourseName(resolveCourseName(form));
        exam.setExamDate(form.getExamDate());
        BigDecimal fullScore = form.getFullScore() == null ? BigDecimal.valueOf(100) : form.getFullScore();
        exam.setFullScore(fullScore);
        BigDecimal passScore = form.getPassScore();
        if (passScore == null || passScore.signum() <= 0) {
            passScore = fullScore.multiply(BigDecimal.valueOf(0.6)).setScale(1, RoundingMode.HALF_UP);
        }
        if (passScore.compareTo(fullScore) > 0) {
            passScore = fullScore;
        }
        exam.setPassScore(passScore);
        exam.setScoringMode(form.getScoringMode() == null || form.getScoringMode().isBlank()
                ? "分数" : form.getScoringMode().trim());
        exam.setRemark(form.getRemark() == null ? null : form.getRemark().trim());
    }

    private String resolveCourseName(GradeExamForm form) {
        if (form.getCourseName() != null && !form.getCourseName().isBlank()) {
            return form.getCourseName().trim();
        }
        if (form.getCourseId() == null) {
            return null;
        }
        Course course = courseMapper.selectById(form.getCourseId());
        return course == null ? null : course.getName();
    }

    private Long resolveCampusId(GradeExamForm form) {
        if (form.getCampusId() != null) {
            return form.getCampusId();
        }
        if (form.getClassIds() != null && !form.getClassIds().isEmpty()) {
            ClassGroup group = classGroupMapper.selectById(form.getClassIds().get(0));
            if (group != null && group.getCampusId() != null) {
                return group.getCampusId();
            }
        }
        return 1L;
    }
}
