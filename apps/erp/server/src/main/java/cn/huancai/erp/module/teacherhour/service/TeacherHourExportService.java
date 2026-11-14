package cn.huancai.erp.module.teacherhour.service;

import cn.huancai.erp.common.excel.SimpleXlsx;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourRecordVO;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourSummaryVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师课时费月度导出：第 1 页为汇总（姓名、课时数、金额、明细），
 * 其后每位教师一页（课程 × 日期 的人数/价格矩阵 + 总计 + 备注），版式对齐手工模板。
 */
@Service
public class TeacherHourExportService {

    private static final double[] SUMMARY_WIDTHS = {9.1, 7.4, 7.2, 9.9, 67.9};

    private final TeacherHourService teacherHourService;

    public TeacherHourExportService(TeacherHourService teacherHourService) {
        this.teacherHourService = teacherHourService;
    }

    public byte[] build(YearMonth month, Long teacherId, Long campusId, String campusShortName) throws IOException {
        List<TeacherHourRecordVO> records = teacherHourService.records(month, teacherId, campusId);
        List<TeacherHourSummaryVO> summaries = teacherHourService.summarize(records);

        String prefix = campusShortName == null ? "" : campusShortName;
        if (prefix.isBlank()) {
            Set<String> campusNames = new LinkedHashSet<>();
            records.stream().map(TeacherHourRecordVO::getCampusName)
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(campusNames::add);
            if (campusNames.size() == 1) {
                prefix = campusNames.iterator().next()
                        .replace("幻彩国学堂", "").replace("幻彩", "").replace("校区", "").trim();
            }
        }
        String title = prefix + "幻彩教师" + (month.getYear() % 100) + "年" + month.getMonthValue() + "月课时费";

        SimpleXlsx xlsx = new SimpleXlsx("汇总", SUMMARY_WIDTHS);
        writeSummary(xlsx, title, summaries);

        Map<Long, List<TeacherHourRecordVO>> byTeacher = new LinkedHashMap<>();
        for (TeacherHourRecordVO record : records) {
            byTeacher.computeIfAbsent(record.getTeacherId(), key -> new ArrayList<>()).add(record);
        }
        Set<String> usedNames = new LinkedHashSet<>();
        for (TeacherHourSummaryVO summary : summaries) {
            List<TeacherHourRecordVO> teacherRecords = byTeacher.get(summary.getTeacherId());
            if (teacherRecords == null || teacherRecords.isEmpty()) {
                continue;
            }
            String sheetName = uniqueSheetName(usedNames, summary.getTeacherName());
            xlsx.newSheet(sheetName, detailWidths(dates(teacherRecords).size()));
            writeTeacherSheet(xlsx, month, summary, teacherRecords);
        }
        return xlsx.build();
    }

    private void writeSummary(SimpleXlsx xlsx, String title, List<TeacherHourSummaryVO> summaries) {
        xlsx.mergedText(0, 0, 0, 4, title, SimpleXlsx.STYLE_TITLE_16);
        xlsx.height(0, 40.1);
        xlsx.height(1, 20);
        xlsx.text(1, 0, "姓名", SimpleXlsx.STYLE_HEAD);
        xlsx.mergedText(1, 1, 1, 2, "课时数", SimpleXlsx.STYLE_HEAD);
        xlsx.text(1, 3, "金额", SimpleXlsx.STYLE_HEAD);
        xlsx.text(1, 4, "明细", SimpleXlsx.STYLE_HEAD);

        int row = 2;
        BigDecimal total = BigDecimal.ZERO;
        for (TeacherHourSummaryVO summary : summaries) {
            xlsx.text(row, 0, summary.getTeacherName(), SimpleXlsx.STYLE_TEXT);
            xlsx.number(row, 1, BigDecimal.valueOf(summary.getLessonCount() == null ? 0 : summary.getLessonCount()),
                    SimpleXlsx.STYLE_TEXT);
            xlsx.text(row, 2, "节", SimpleXlsx.STYLE_TEXT);
            xlsx.number(row, 3, summary.getAmount(), SimpleXlsx.STYLE_TEXT);
            xlsx.text(row, 4, summary.getDetail(), SimpleXlsx.STYLE_TEXT_LEFT);
            xlsx.height(row, 25.1);
            total = total.add(summary.getAmount() == null ? BigDecimal.ZERO : summary.getAmount());
            row++;
        }
        xlsx.mergedText(row, 0, row, 2, "合计金额", SimpleXlsx.STYLE_SUMMARY_14);
        xlsx.number(row, 3, total, SimpleXlsx.STYLE_SUMMARY_14);
        xlsx.text(row, 4, "", SimpleXlsx.STYLE_SUMMARY_14);
        xlsx.height(row, 28.1);
    }

    private void writeTeacherSheet(SimpleXlsx xlsx, YearMonth month, TeacherHourSummaryVO summary,
                                   List<TeacherHourRecordVO> records) {
        List<LocalDate> dates = dates(records);
        int cols = 1 + dates.size() * 2 + 1;
        String title = summary.getTeacherName() + (month.getYear() % 100) + "年" + month.getMonthValue()
                + "月份教师课时费";
        xlsx.mergedText(0, 0, 0, cols - 1, title, SimpleXlsx.STYLE_TITLE_14);
        xlsx.height(0, 36);
        xlsx.height(1, 20);
        xlsx.height(2, 20);

        xlsx.mergedText(1, 0, 2, 0, "课程", SimpleXlsx.STYLE_HEAD);
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            xlsx.mergedText(1, 1 + i * 2, 1, 2 + i * 2,
                    date.getMonthValue() + "月" + date.getDayOfMonth() + "日", SimpleXlsx.STYLE_HEAD);
            xlsx.text(2, 1 + i * 2, "人数", SimpleXlsx.STYLE_HEAD);
            xlsx.text(2, 2 + i * 2, "价格", SimpleXlsx.STYLE_HEAD);
        }
        xlsx.mergedText(1, cols - 1, 2, cols - 1, "合计", SimpleXlsx.STYLE_HEAD);

        List<List<TeacherHourRecordVO>> classRows = classRows(records);
        int row = 3;
        for (List<TeacherHourRecordVO> classRecords : classRows) {
            xlsx.text(row, 0, classLabel(classRecords.get(0)), SimpleXlsx.STYLE_TEXT_LEFT);
            xlsx.height(row, 20);
            for (int i = 0; i < dates.size(); i++) {
                LocalDate date = dates.get(i);
                List<TeacherHourRecordVO> cells = classRecords.stream()
                        .filter(r -> date.equals(r.getLessonDate())).toList();
                if (cells.isEmpty()) {
                    xlsx.text(row, 1 + i * 2, "×", SimpleXlsx.STYLE_TEXT);
                    xlsx.text(row, 2 + i * 2, "×", SimpleXlsx.STYLE_TEXT);
                } else {
                    int students = cells.stream().mapToInt(TeacherHourService::studentCount).sum();
                    BigDecimal amount = cells.stream().map(r -> TeacherHourService.nz(r.getAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    xlsx.number(row, 1 + i * 2, BigDecimal.valueOf(students), SimpleXlsx.STYLE_TEXT);
                    xlsx.number(row, 2 + i * 2, amount, SimpleXlsx.STYLE_TEXT);
                }
            }
            xlsx.text(row, cols - 1, "", SimpleXlsx.STYLE_TEXT);
            row++;
        }

        xlsx.text(row, 0, "总计", SimpleXlsx.STYLE_HEAD);
        xlsx.height(row, 20);
        BigDecimal grand = BigDecimal.ZERO;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            BigDecimal dayTotal = records.stream()
                    .filter(r -> date.equals(r.getLessonDate()))
                    .map(r -> TeacherHourService.nz(r.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            xlsx.text(row, 1 + i * 2, "", SimpleXlsx.STYLE_HEAD);
            xlsx.number(row, 2 + i * 2, dayTotal, SimpleXlsx.STYLE_HEAD);
            grand = grand.add(dayTotal);
        }
        xlsx.number(row, cols - 1, grand, SimpleXlsx.STYLE_HEAD);
        row++;

        xlsx.mergedText(row, 0, row, cols - 1, "备注：" + remark(records), SimpleXlsx.STYLE_TEXT_LEFT);
        xlsx.height(row, 20);
    }

    private static String remark(List<TeacherHourRecordVO> records) {
        StringBuilder sb = new StringBuilder();
        for (TeacherHourRecordVO record : records) {
            if (record.getAdjustRemark() != null && !record.getAdjustRemark().isBlank()) {
                sb.append(record.getLessonDate().getMonthValue()).append("月")
                        .append(record.getLessonDate().getDayOfMonth()).append("日")
                        .append(record.getAdjustRemark().trim());
                if (!record.getAdjustRemark().trim().endsWith("。")) {
                    sb.append("。");
                }
            }
        }
        return sb.toString();
    }

    private static List<LocalDate> dates(List<TeacherHourRecordVO> records) {
        return records.stream().map(TeacherHourRecordVO::getLessonDate).distinct().sorted().toList();
    }

    private static double[] detailWidths(int dateCount) {
        double[] widths = new double[1 + dateCount * 2 + 1];
        widths[0] = 20.6;
        for (int i = 0; i < dateCount; i++) {
            widths[1 + i * 2] = 5.9;
            widths[2 + i * 2] = 5.9;
        }
        widths[widths.length - 1] = 9.2;
        return widths;
    }

    private static List<List<TeacherHourRecordVO>> classRows(List<TeacherHourRecordVO> records) {
        Map<String, List<TeacherHourRecordVO>> grouped = new LinkedHashMap<>();
        for (TeacherHourRecordVO record : records) {
            String key = record.getClassId() != null ? "c" + record.getClassId() : "n" + record.getClassName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }
        List<List<TeacherHourRecordVO>> rows = new ArrayList<>(grouped.values());
        rows.sort(Comparator
                .comparingInt((List<TeacherHourRecordVO> list) -> gradeOrder(list.get(0)))
                .thenComparing(list -> list.get(0).getStartTime() == null ? "" : list.get(0).getStartTime().toString())
                .thenComparing(list -> classLabel(list.get(0))));
        return rows;
    }

    private static int gradeOrder(TeacherHourRecordVO record) {
        String grade = TeacherHourService.shortGrade(record.getGrade());
        return switch (grade) {
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

    private static String classLabel(TeacherHourRecordVO record) {
        if (record.getClassName() != null && !record.getClassName().isBlank()) {
            return record.getClassName();
        }
        return record.getCourseName() == null ? "课程" : record.getCourseName();
    }

    private static String uniqueSheetName(Set<String> used, String teacherName) {
        String base = teacherName == null || teacherName.isBlank() ? "教师" : teacherName;
        base = base.replaceAll("[\\\\/:*?\\[\\]]", "").trim();
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }
        String name = base;
        int index = 2;
        while (!used.add(name)) {
            name = base.length() > 28 ? base.substring(0, 28) + index : base + index;
            index++;
        }
        return name;
    }
}
