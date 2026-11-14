package cn.huancai.erp.module.teacherhour.service;

import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.teacherhour.entity.TeacherHourAdjust;
import cn.huancai.erp.module.teacherhour.entity.TeacherHourRate;
import cn.huancai.erp.module.teacherhour.mapper.TeacherHourAdjustMapper;
import cn.huancai.erp.module.teacherhour.mapper.TeacherHourMapper;
import cn.huancai.erp.module.teacherhour.mapper.TeacherHourRateMapper;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourMonthVO;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourRecordVO;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 教师课时：上课记录、按「教师 + 适用年级区间」规则计算课酬、月度汇总。 */
@Service
public class TeacherHourService {

    private static final List<String> GRADE_ORDER = List.of(
            "一年级", "二年级", "三年级", "四年级", "五年级", "六年级", "七年级", "八年级", "九年级");

    private final TeacherHourMapper hourMapper;
    private final TeacherHourRateMapper rateMapper;
    private final TeacherHourAdjustMapper adjustMapper;
    private final StaffMapper staffMapper;
    private final CampusMapper campusMapper;

    public TeacherHourService(TeacherHourMapper hourMapper, TeacherHourRateMapper rateMapper,
                             TeacherHourAdjustMapper adjustMapper, StaffMapper staffMapper,
                             CampusMapper campusMapper) {
        this.hourMapper = hourMapper;
        this.rateMapper = rateMapper;
        this.adjustMapper = adjustMapper;
        this.staffMapper = staffMapper;
        this.campusMapper = campusMapper;
    }

    public TeacherHourMonthVO month(YearMonth month, Long teacherId, Long campusId) {
        List<TeacherHourRecordVO> records = records(month, teacherId, campusId);
        List<TeacherHourSummaryVO> summaries = summarize(records);

        TeacherHourMonthVO vo = new TeacherHourMonthVO();
        vo.setRecords(records);
        vo.setTeachers(summaries);
        vo.setTeacherOptions(teacherOptions());
        vo.setCampusOptions(campusOptions());
        vo.setLessonCount(records.size());
        vo.setStudentCount(records.stream().mapToInt(TeacherHourService::studentCount).sum());
        vo.setAmount(records.stream().map(r -> nz(r.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        long missing = summaries.stream()
                .filter(s -> records.stream().anyMatch(r -> !Boolean.TRUE.equals(r.getRateConfigured())
                        && Objects.equals(r.getTeacherId(), s.getTeacherId())))
                .count();
        vo.setMissingRateCount((int) missing);
        return vo;
    }

    public List<TeacherHourRecordVO> records(YearMonth month, Long teacherId, Long campusId) {
        LocalDate start = month.atDay(1);
        List<TeacherHourRecordVO> records = hourMapper.selectRecords(start, start.plusMonths(1), teacherId, campusId);
        Map<Long, List<TeacherHourRate>> rates = loadRates();
        for (TeacherHourRecordVO record : records) {
            applyRate(record, findRate(rates, record.getTeacherId(), gradeLevel(record.getGrade())));
        }
        return records;
    }

    /** 按教师聚合：课时数、上课人次、课酬、年级明细。 */
    public List<TeacherHourSummaryVO> summarize(List<TeacherHourRecordVO> records) {
        Map<Long, List<TeacherHourRecordVO>> grouped = new LinkedHashMap<>();
        for (TeacherHourRecordVO record : records) {
            grouped.computeIfAbsent(record.getTeacherId(), key -> new ArrayList<>()).add(record);
        }
        List<TeacherHourSummaryVO> summaries = new ArrayList<>();
        for (List<TeacherHourRecordVO> list : grouped.values()) {
            TeacherHourRecordVO first = list.get(0);
            TeacherHourSummaryVO summary = new TeacherHourSummaryVO();
            summary.setTeacherId(first.getTeacherId());
            summary.setTeacherName(first.getTeacherName());
            summary.setLessonCount(list.size());
            summary.setStudentCount(list.stream().mapToInt(TeacherHourService::studentCount).sum());
            summary.setAmount(list.stream().map(r -> nz(r.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            summary.setDetail(detail(list));
            summary.setRemarks(list.stream()
                    .filter(r -> r.getAdjustRemark() != null && !r.getAdjustRemark().isBlank())
                    .map(r -> formatDate(r.getLessonDate()) + r.getAdjustRemark().trim())
                    .toList());
            summaries.add(summary);
        }
        summaries.sort(Comparator.comparing(TeacherHourSummaryVO::getTeacherName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return summaries;
    }

    public List<TeacherHourRate> rates() {
        return rateMapper.selectList(new LambdaQueryWrapper<TeacherHourRate>()
                .orderByAsc(TeacherHourRate::getTeacherName)
                .orderByAsc(TeacherHourRate::getTeacherId)
                .orderByAsc(TeacherHourRate::getGradeStart));
    }

    /** 整份保存：规则可自由增删，先清空再写入。 */
    @Transactional
    public void saveRates(List<TeacherHourRate> rates) {
        rateMapper.delete(new LambdaQueryWrapper<TeacherHourRate>().isNotNull(TeacherHourRate::getId));
        for (TeacherHourRate rate : rates) {
            if (rate.getTeacherId() == null) {
                continue;
            }
            int start = clampGrade(rate.getGradeStart(), 1);
            int end = clampGrade(rate.getGradeEnd(), 9);
            if (end < start) {
                int swap = start;
                start = end;
                end = swap;
            }
            rate.setId(null);
            rate.setGradeStart(start);
            rate.setGradeEnd(end);
            if (rate.getTeacherName() == null || rate.getTeacherName().isBlank()) {
                Staff staff = staffMapper.selectById(rate.getTeacherId());
                rate.setTeacherName(staff == null ? null : staff.getName());
            }
            rateMapper.insert(rate);
        }
    }

    /** 单节课酬手工调整；amount 与 remark 都为空时清除调整。 */
    public void adjust(Long scheduleId, BigDecimal amount, String remark) {
        if (amount == null && (remark == null || remark.isBlank())) {
            adjustMapper.deleteById(scheduleId);
            return;
        }
        TeacherHourAdjust adjust = new TeacherHourAdjust();
        adjust.setScheduleId(scheduleId);
        adjust.setAmount(amount);
        adjust.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        if (adjustMapper.selectById(scheduleId) == null) {
            adjustMapper.insert(adjust);
        } else {
            adjustMapper.updateById(adjust);
        }
    }

    public List<TeacherHourMonthVO.Option> teacherOptions() {
        List<TeacherHourMonthVO.Option> options = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Staff staff : staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getRole, "teacher")
                .eq(Staff::getStatus, 1)
                .orderByAsc(Staff::getName))) {
            if (seen.add(staff.getId())) {
                options.add(new TeacherHourMonthVO.Option(staff.getId(), staff.getName()));
            }
        }
        return options;
    }

    public List<TeacherHourMonthVO.Option> campusOptions() {
        List<TeacherHourMonthVO.Option> options = new ArrayList<>();
        for (Campus campus : campusMapper.selectList(new LambdaQueryWrapper<Campus>()
                .eq(Campus::getStatus, 1)
                .orderByAsc(Campus::getId))) {
            options.add(new TeacherHourMonthVO.Option(campus.getId(), campus.getName()));
        }
        return options;
    }

    /** 导出标题用的校区简称，如「鲅鱼圈」。 */
    public String campusShortName(Long campusId) {
        if (campusId == null) {
            return "";
        }
        Campus campus = campusMapper.selectById(campusId);
        if (campus == null) {
            return "";
        }
        String name = campus.getShortName();
        if (name == null || name.isBlank()) {
            name = campus.getName();
        }
        if (name == null) {
            return "";
        }
        return name.replace("幻彩国学堂", "").replace("幻彩", "").replace("校区", "").trim();
    }

    private Map<Long, List<TeacherHourRate>> loadRates() {
        Map<Long, List<TeacherHourRate>> rates = new LinkedHashMap<>();
        for (TeacherHourRate rate : rateMapper.selectList(null)) {
            rates.computeIfAbsent(rate.getTeacherId(), key -> new ArrayList<>()).add(rate);
        }
        // 区间越窄越优先，便于「1-3 年级」覆盖「1-6 年级」的通用规则
        rates.values().forEach(list -> list.sort(Comparator
                .comparingInt((TeacherHourRate rate) -> gradeSpan(rate))
                .thenComparingInt(rate -> intOf(rate.getGradeStart(), 1))));
        return rates;
    }

    private static int gradeSpan(TeacherHourRate rate) {
        return Math.max(0, intOf(rate.getGradeEnd(), 9) - intOf(rate.getGradeStart(), 1));
    }

    private static TeacherHourRate findRate(Map<Long, List<TeacherHourRate>> rates, Long teacherId, int level) {
        List<TeacherHourRate> list = rates.get(teacherId);
        if (list == null) {
            return null;
        }
        for (TeacherHourRate rate : list) {
            int start = clampGrade(rate.getGradeStart(), 1);
            int end = clampGrade(rate.getGradeEnd(), 9);
            if (level > 0) {
                if (level >= start && level <= end) {
                    return rate;
                }
            } else if (start <= 1 && end >= GRADE_ORDER.size()) {
                // 年级缺失（如一对一未登记年级）时，仅用覆盖全部年级的规则兜底
                return rate;
            }
        }
        return null;
    }

    private static int intOf(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int clampGrade(Integer value, int fallback) {
        return Math.max(1, Math.min(9, intOf(value, fallback)));
    }

    private static void applyRate(TeacherHourRecordVO record, TeacherHourRate rate) {
        if (rate != null) {
            record.setRateConfigured(true);
            record.setUnitPrice(rate.getUnitPrice());
            record.setThresholdStudents(rate.getThresholdStudents());
            record.setFixedAmount(rate.getFixedAmount());
            int students = studentCount(record);
            int threshold = rate.getThresholdStudents() == null ? 0 : rate.getThresholdStudents();
            // 人数未达到额度按「人数 × 单价」，达到/超过额度按单节固定金额
            record.setAmount(threshold > 0 && students >= threshold
                    ? nz(rate.getFixedAmount())
                    : nz(rate.getUnitPrice()).multiply(BigDecimal.valueOf(students)));
        } else {
            record.setRateConfigured(false);
            record.setAmount(BigDecimal.ZERO);
        }
        if (record.getAdjustAmount() != null) {
            record.setAmount(record.getAdjustAmount());
        }
    }

    public static int studentCount(TeacherHourRecordVO record) {
        return record.getStudentCount() == null ? 0 : record.getStudentCount();
    }

    /** 年级文本转年级序号（1~9），无法识别时返回 0。 */
    public static int gradeLevel(String grade) {
        if (grade == null || grade.isBlank()) {
            return 0;
        }
        String value = grade.trim();
        for (int i = 0; i < GRADE_ORDER.size(); i++) {
            if (value.contains(GRADE_ORDER.get(i))) {
                return i + 1;
            }
        }
        char first = value.charAt(0);
        if (first >= '1' && first <= '9') {
            return first - '0';
        }
        return 0;
    }

    /** 年级简写：一~三年级用中文，四~七年级用数字，与手工表一致。 */
    public static String shortGrade(String grade) {
        if (grade == null || grade.isBlank()) {
            return "未分年级";
        }
        String value = grade.trim().replace("年级", "年");
        return switch (value) {
            case "四年" -> "4年";
            case "五年" -> "5年";
            case "六年" -> "6年";
            case "七年" -> "7年";
            default -> value;
        };
    }

    private static String detail(List<TeacherHourRecordVO> records) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TeacherHourRecordVO record : records) {
            counts.merge(shortGrade(record.getGrade()), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> gradeIndex(entry.getKey())))
                .forEach(entry -> sb.append(entry.getKey()).append(entry.getValue()).append("节、"));
        if (sb.length() == 0) {
            return "";
        }
        sb.setCharAt(sb.length() - 1, '。');
        return sb.toString();
    }

    private static int gradeIndex(String shortGrade) {
        return switch (shortGrade) {
            case "一年" -> 1;
            case "二年" -> 2;
            case "三年" -> 3;
            case "4年" -> 4;
            case "5年" -> 5;
            case "6年" -> 6;
            case "7年" -> 7;
            default -> 99;
        };
    }

    static String formatDate(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日 ";
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
