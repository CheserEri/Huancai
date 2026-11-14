package cn.huancai.erp.module.grade.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.grade.dto.GradeScoreRow;
import cn.huancai.erp.module.grade.dto.GradeScoreSaveForm;
import cn.huancai.erp.module.grade.entity.GradeExam;
import cn.huancai.erp.module.grade.entity.GradeExamClass;
import cn.huancai.erp.module.grade.entity.GradePart;
import cn.huancai.erp.module.grade.entity.GradeRule;
import cn.huancai.erp.module.grade.entity.GradeScore;
import cn.huancai.erp.module.grade.mapper.GradeExamClassMapper;
import cn.huancai.erp.module.grade.mapper.GradeExamMapper;
import cn.huancai.erp.module.grade.mapper.GradePartMapper;
import cn.huancai.erp.module.grade.mapper.GradeRuleMapper;
import cn.huancai.erp.module.grade.mapper.GradeScoreMapper;
import cn.huancai.erp.module.grade.service.GradeRankService;
import cn.huancai.erp.module.grade.vo.GradeAnalysisVO;
import cn.huancai.erp.module.grade.vo.GradeClassExamVO;
import cn.huancai.erp.module.grade.vo.GradeEntryStudentVO;
import cn.huancai.erp.module.grade.vo.GradeEntryVO;
import cn.huancai.erp.module.grade.vo.GradeQueryVO;
import cn.huancai.erp.module.grade.vo.GradeStudentItemVO;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 成绩录入 / 查询 / 分析。
 * 录入先存草稿，发布后锁定并按班级、校区计算排名；修改已发布成绩需先在考试管理中解锁。
 */
@RestController
@RequestMapping("/api/grades")
public class GradeScoreController {

    private static final List<String> ENTERED_STATUS = List.of("正常", "缺考", "免考");
    private static final List<String> LEVEL_COLORS = List.of("优秀", "良好", "合格", "待提高");

    private final GradeExamMapper examMapper;
    private final GradeExamClassMapper examClassMapper;
    private final GradePartMapper partMapper;
    private final GradeScoreMapper scoreMapper;
    private final GradeRuleMapper ruleMapper;
    private final GradeRankService rankService;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentMapper studentMapper;
    private final ObjectMapper objectMapper;

    public GradeScoreController(GradeExamMapper examMapper,
                                GradeExamClassMapper examClassMapper,
                                GradePartMapper partMapper,
                                GradeScoreMapper scoreMapper,
                                GradeRuleMapper ruleMapper,
                                GradeRankService rankService,
                                ClassGroupMapper classGroupMapper,
                                ClassStudentMapper classStudentMapper,
                                StudentMapper studentMapper,
                                ObjectMapper objectMapper) {
        this.examMapper = examMapper;
        this.examClassMapper = examClassMapper;
        this.partMapper = partMapper;
        this.scoreMapper = scoreMapper;
        this.ruleMapper = ruleMapper;
        this.rankService = rankService;
        this.classGroupMapper = classGroupMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentMapper = studentMapper;
        this.objectMapper = objectMapper;
    }

    /** 录入网格数据：考试 + 班级 + 分项 + 在读学员成绩。 */
    @GetMapping("/entry")
    public ApiResult<GradeEntryVO> entry(@RequestParam Long examId, @RequestParam Long classId) {
        GradeExam exam = examMapper.selectById(examId);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        ClassGroup group = classGroupMapper.selectById(classId);
        if (group == null) {
            return ApiResult.fail("班级不存在");
        }
        GradeEntryVO vo = new GradeEntryVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getName());
        vo.setExamType(exam.getType());
        vo.setExamDate(exam.getExamDate());
        vo.setScoringMode(exam.getScoringMode());
        vo.setFullScore(exam.getFullScore());
        vo.setStatus(exam.getStatus());
        vo.setClassId(group.getId());
        vo.setClassName(group.getName());
        vo.setParts(partMapper.selectList(new LambdaQueryWrapper<GradePart>()
                .eq(GradePart::getExamId, examId).orderByAsc(GradePart::getSort).orderByAsc(GradePart::getId)));
        List<GradeEntryStudentVO> students = scoreMapper.selectEntryStudents(examId, classId);
        for (GradeEntryStudentVO student : students) {
            student.setPartScores(parseParts(student.getPartScoresJson()));
        }
        vo.setStudents(students);
        return ApiResult.ok(vo);
    }

    /** 保存草稿。 */
    @PostMapping("/entry/save")
    @Transactional
    public ApiResult<GradeEntryVO> save(@RequestBody GradeScoreSaveForm form) {
        String error = checkEditable(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        GradeExam exam = examMapper.selectById(form.getExamId());
        error = persist(form, exam);
        if (error != null) {
            return ApiResult.fail(error);
        }
        return entry(form.getExamId(), form.getClassId());
    }

    /** 保存并发布：锁定成绩、计算排名。 */
    @PostMapping("/entry/publish")
    @Transactional
    public ApiResult<GradeEntryVO> publish(@RequestBody GradeScoreSaveForm form) {
        String error = checkEditable(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        GradeExam exam = examMapper.selectById(form.getExamId());
        error = persist(form, exam);
        if (error != null) {
            return ApiResult.fail(error);
        }
        Long count = scoreMapper.selectCount(new LambdaQueryWrapper<GradeScore>()
                .eq(GradeScore::getExamId, exam.getId()).in(GradeScore::getStatus, ENTERED_STATUS));
        if (count == null || count == 0) {
            return ApiResult.fail("该考试还没有录入任何成绩，无法发布");
        }
        rankService.computeRanks(exam.getId());
        exam.setStatus("已发布");
        exam.setUpdatedAt(LocalDateTime.now());
        examMapper.updateById(exam);
        return entry(form.getExamId(), form.getClassId());
    }

    @GetMapping("/query")
    public ApiResult<List<GradeQueryVO>> query(@RequestParam(required = false) Long examId,
                                               @RequestParam(required = false) Long classId,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String scoreStatus,
                                               @RequestParam(required = false) String keyword) {
        return ApiResult.ok(scoreMapper.selectQueryRows(examId, classId, studentId, type, scoreStatus,
                keyword == null || keyword.isBlank() ? null : keyword.trim()));
    }

    /** 学员详情「成绩」页签。 */
    @GetMapping("/student/{studentId}")
    public ApiResult<List<GradeStudentItemVO>> studentGrades(@PathVariable Long studentId) {
        List<GradeStudentItemVO> items = scoreMapper.selectStudentGrades(studentId);
        for (GradeStudentItemVO item : items) {
            item.setPartScores(parseParts(item.getPartScoresJson()));
        }
        return ApiResult.ok(items);
    }

    /** 班级详情「成绩」页签 / 成绩分析「班级趋势」：该班级的考试与整体表现。 */
    @GetMapping("/class/{classId}")
    public ApiResult<List<GradeClassExamVO>> classGrades(@PathVariable Long classId) {
        List<GradeClassExamVO> exams = scoreMapper.selectClassExams(classId);
        if (!exams.isEmpty()) {
            Map<Long, List<GradeScore>> scoresByExam = new HashMap<>();
            scoreMapper.selectList(new LambdaQueryWrapper<GradeScore>().eq(GradeScore::getClassId, classId))
                    .forEach(score -> scoresByExam.computeIfAbsent(score.getExamId(), key -> new ArrayList<>()).add(score));
            for (GradeClassExamVO item : exams) {
                List<GradeScore> valid = scoresByExam.getOrDefault(item.getExamId(), List.of()).stream()
                        .filter(score -> "正常".equals(score.getStatus()) && score.getTotalScore() != null)
                        .toList();
                BigDecimal fullScore = item.getFullScore() == null || item.getFullScore().signum() <= 0
                        ? BigDecimal.valueOf(100) : item.getFullScore();
                item.setPassRate(rate(valid, resolveLine("合格", "及格", fullScore, 0.6)));
                item.setExcellentRate(rate(valid, resolveLine("优秀", null, fullScore, 0.85)));
            }
        }
        return ApiResult.ok(exams);
    }

    /** 成绩分析：整体指标、分数段分布、班级对比、排名与等级分布。 */
    @GetMapping("/analysis")
    public ApiResult<GradeAnalysisVO> analysis(@RequestParam Long examId) {
        GradeExam exam = examMapper.selectById(examId);
        if (exam == null) {
            return ApiResult.fail("考试不存在");
        }
        List<GradeExamClass> relations = examClassMapper.selectList(
                new LambdaQueryWrapper<GradeExamClass>().eq(GradeExamClass::getExamId, examId));
        List<Long> classIds = relations.stream().map(GradeExamClass::getClassId).toList();
        Map<Long, String> classNames = new HashMap<>();
        if (!classIds.isEmpty()) {
            classGroupMapper.selectBatchIds(classIds).forEach(group -> classNames.put(group.getId(), group.getName()));
        }
        Map<Long, Integer> classStudentCounts = new HashMap<>();
        int studentCount = 0;
        for (Long classId : classIds) {
            Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStatus, "在读"));
            int value = count == null ? 0 : count.intValue();
            classStudentCounts.put(classId, value);
            studentCount += value;
        }
        List<GradeScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<GradeScore>().eq(GradeScore::getExamId, examId));
        BigDecimal fullScore = exam.getFullScore() == null || exam.getFullScore().signum() <= 0
                ? BigDecimal.valueOf(100) : exam.getFullScore();
        BigDecimal passLine = resolveLine("合格", "及格", fullScore, 0.6);
        BigDecimal excellentLine = resolveLine("优秀", null, fullScore, 0.85);

        GradeAnalysisVO vo = new GradeAnalysisVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getName());
        vo.setExamType(exam.getType());
        vo.setExamDate(exam.getExamDate());
        vo.setScoringMode(exam.getScoringMode());
        vo.setFullScore(fullScore);
        vo.setStudentCount(studentCount);

        List<GradeScore> entered = scores.stream().filter(item -> ENTERED_STATUS.contains(item.getStatus())).toList();
        vo.setEnteredCount(entered.size());
        vo.setAbsentCount((int) entered.stream().filter(item -> "缺考".equals(item.getStatus())).count());
        vo.setExemptCount((int) entered.stream().filter(item -> "免考".equals(item.getStatus())).count());
        vo.setMissingCount(Math.max(studentCount - entered.size(), 0));

        List<GradeScore> valid = scores.stream()
                .filter(item -> "正常".equals(item.getStatus()) && item.getTotalScore() != null)
                .toList();
        vo.setAvgScore(average(valid));
        vo.setMaxScore(valid.stream().map(GradeScore::getTotalScore).max(BigDecimal::compareTo).orElse(null));
        vo.setMinScore(valid.stream().map(GradeScore::getTotalScore).min(BigDecimal::compareTo).orElse(null));
        vo.setPassRate(rate(valid, passLine));
        vo.setExcellentRate(rate(valid, excellentLine));

        vo.setDistributions(distributions(valid, fullScore, exam.getScoringMode()));
        vo.setLevels(levelCounts(scores));

        List<GradeAnalysisVO.ClassStat> classStats = new ArrayList<>();
        for (Long classId : classIds) {
            List<GradeScore> classScores = valid.stream().filter(item -> classId.equals(item.getClassId())).toList();
            GradeAnalysisVO.ClassStat stat = new GradeAnalysisVO.ClassStat();
            stat.setClassId(classId);
            stat.setClassName(classNames.getOrDefault(classId, "班级#" + classId));
            stat.setCount(classScores.size());
            stat.setAvgScore(average(classScores));
            stat.setMaxScore(classScores.stream().map(GradeScore::getTotalScore).max(BigDecimal::compareTo).orElse(null));
            stat.setMinScore(classScores.stream().map(GradeScore::getTotalScore).min(BigDecimal::compareTo).orElse(null));
            stat.setPassRate(rate(classScores, passLine));
            stat.setExcellentRate(rate(classScores, excellentLine));
            classStats.add(stat);
        }
        classStats.sort(Comparator.comparing(GradeAnalysisVO.ClassStat::getClassName));
        vo.setClasses(classStats);

        List<GradeScore> sorted = new ArrayList<>(valid);
        sorted.sort(Comparator.comparing(GradeScore::getTotalScore).reversed());
        List<Long> rankStudentIds = sorted.stream().limit(50).map(GradeScore::getStudentId).distinct().toList();
        Map<Long, String> studentNames = new HashMap<>();
        if (!rankStudentIds.isEmpty()) {
            studentMapper.selectBatchIds(rankStudentIds).forEach(student -> studentNames.put(student.getId(), student.getName()));
        }
        List<GradeAnalysisVO.RankRow> rankings = new ArrayList<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int i = 0; i < sorted.size() && i < 50; i++) {
            GradeScore score = sorted.get(i);
            if (previous == null || score.getTotalScore().compareTo(previous) != 0) {
                rank = i + 1;
                previous = score.getTotalScore();
            }
            GradeAnalysisVO.RankRow row = new GradeAnalysisVO.RankRow();
            row.setRank(rank);
            row.setStudentId(score.getStudentId());
            row.setStudentName(studentNames.get(score.getStudentId()));
            row.setClassName(classNames.get(score.getClassId()));
            row.setTotalScore(score.getTotalScore());
            row.setLevel(score.getLevel());
            rankings.add(row);
        }
        vo.setRankings(rankings);
        return ApiResult.ok(vo);
    }


    private String checkEditable(GradeScoreSaveForm form) {
        if (form.getExamId() == null || form.getClassId() == null) {
            return "缺少考试或班级参数";
        }
        GradeExam exam = examMapper.selectById(form.getExamId());
        if (exam == null) {
            return "考试不存在";
        }
        if ("已发布".equals(exam.getStatus())) {
            return "该考试已发布，成绩已锁定，请先在考试管理中解锁";
        }
        if ("已归档".equals(exam.getStatus())) {
            return "该考试已归档，不能录入成绩";
        }
        if (form.getRows() == null || form.getRows().isEmpty()) {
            return "没有需要保存的成绩";
        }
        return null;
    }

    private String persist(GradeScoreSaveForm form, GradeExam exam) {
        BigDecimal fullScore = exam.getFullScore() == null ? BigDecimal.valueOf(100) : exam.getFullScore();
        List<GradeScoreRow> rows = form.getRows();
        for (GradeScoreRow row : rows) {
            if (row.getStudentId() == null) {
                continue;
            }
            String status = row.getStatus() == null || row.getStatus().isBlank() ? null : row.getStatus().trim();
            Map<String, BigDecimal> parts = row.getPartScores() == null ? Map.of() : row.getPartScores();
            boolean hasValue = row.getTotalScore() != null || (row.getLevel() != null && !row.getLevel().isBlank())
                    || parts.values().stream().anyMatch(Objects::nonNull);
            if (status == null) {
                status = hasValue ? "正常" : "未考";
            }
            if (!ENTERED_STATUS.contains(status) && !"未考".equals(status)) {
                return "成绩状态不正确：" + status;
            }
            GradeScore existing = scoreMapper.selectOne(new LambdaQueryWrapper<GradeScore>()
                    .eq(GradeScore::getExamId, form.getExamId())
                    .eq(GradeScore::getClassId, form.getClassId())
                    .eq(GradeScore::getStudentId, row.getStudentId()));
            if ("未考".equals(status)) {
                if (existing != null) {
                    scoreMapper.deleteById(existing.getId());
                }
                continue;
            }
            BigDecimal totalScore = row.getTotalScore();
            boolean hasParts = parts.values().stream().anyMatch(Objects::nonNull);
            if (hasParts) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal value : parts.values()) {
                    if (value != null) {
                        sum = sum.add(value);
                    }
                }
                totalScore = sum;
            }
            if (totalScore != null) {
                if (totalScore.signum() < 0) {
                    return "成绩不能为负数";
                }
                if (totalScore.compareTo(fullScore) > 0) {
                    return "成绩不能超过满分 " + fullScore.stripTrailingZeros().toPlainString();
                }
            }
            GradeScore score = existing == null ? new GradeScore() : existing;
            score.setExamId(form.getExamId());
            score.setClassId(form.getClassId());
            score.setStudentId(row.getStudentId());
            score.setEnrollmentId(row.getEnrollmentId());
            score.setTotalScore("正常".equals(status) ? totalScore : null);
            score.setLevel(row.getLevel() == null || row.getLevel().isBlank() ? null : row.getLevel().trim());
            score.setPartScores(hasParts ? writeParts(parts) : null);
            score.setStatus(status);
            score.setRemark(row.getRemark() == null || row.getRemark().isBlank() ? null : row.getRemark().trim());
            if (existing == null) {
                score.setCreatedAt(LocalDateTime.now());
                score.setUpdatedAt(LocalDateTime.now());
                scoreMapper.insert(score);
            } else {
                score.setUpdatedAt(LocalDateTime.now());
                scoreMapper.updateById(score);
            }
        }
        return null;
    }

    private BigDecimal average(List<GradeScore> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        BigDecimal sum = scores.stream().map(GradeScore::getTotalScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(List<GradeScore> scores, BigDecimal line) {
        if (scores.isEmpty() || line == null) {
            return null;
        }
        long hit = scores.stream().filter(item -> item.getTotalScore().compareTo(line) >= 0).count();
        return BigDecimal.valueOf(hit * 100.0 / scores.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveLine(String name, String alias, BigDecimal fullScore, double ratio) {
        List<GradeRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<GradeRule>().orderByAsc(GradeRule::getSort));
        for (GradeRule rule : rules) {
            boolean match = rule.getName() != null
                    && (rule.getName().contains(name) || (alias != null && rule.getName().contains(alias)));
            if (match && rule.getMinScore() != null) {
                return rule.getMinScore();
            }
        }
        return fullScore.multiply(BigDecimal.valueOf(ratio)).setScale(1, RoundingMode.HALF_UP);
    }

    private List<GradeAnalysisVO.Distribution> distributions(List<GradeScore> valid, BigDecimal fullScore, String mode) {
        String[] labels = fullScore.compareTo(BigDecimal.valueOf(100)) == 0
                ? new String[]{"0-59", "60-69", "70-79", "80-89", "90-100"}
                : new String[]{"0-60%", "60-70%", "70-80%", "80-90%", "90-100%"};
        int[] counts = new int[labels.length];
        for (GradeScore score : valid) {
            double percent = score.getTotalScore().doubleValue() * 100.0 / fullScore.doubleValue();
            int bucket = percent < 60 ? 0 : percent < 70 ? 1 : percent < 80 ? 2 : percent < 90 ? 3 : 4;
            counts[bucket]++;
        }
        List<GradeAnalysisVO.Distribution> list = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            GradeAnalysisVO.Distribution item = new GradeAnalysisVO.Distribution();
            item.setLabel(labels[i]);
            item.setCount(counts[i]);
            list.add(item);
        }
        return list;
    }

    private List<GradeAnalysisVO.LevelCount> levelCounts(List<GradeScore> scores) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String level : LEVEL_COLORS) {
            counter.put(level, 0);
        }
        for (GradeScore score : scores) {
            if (score.getLevel() != null && !score.getLevel().isBlank()) {
                counter.merge(score.getLevel().trim(), 1, Integer::sum);
            }
        }
        List<GradeAnalysisVO.LevelCount> list = new ArrayList<>();
        counter.forEach((level, count) -> {
            GradeAnalysisVO.LevelCount item = new GradeAnalysisVO.LevelCount();
            item.setLevel(level);
            item.setCount(count);
            list.add(item);
        });
        return list;
    }

    private Map<String, BigDecimal> parseParts(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, BigDecimal>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeParts(Map<String, BigDecimal> parts) {
        try {
            return objectMapper.writeValueAsString(parts);
        } catch (Exception ex) {
            return null;
        }
    }
}
