package cn.huancai.erp.module.report.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.report.mapper.ReportMapper;
import cn.huancai.erp.module.report.vo.BusinessReportVO;
import cn.huancai.erp.module.report.vo.CourseConsumeVO;
import cn.huancai.erp.module.report.vo.FinanceReportVO;
import cn.huancai.erp.module.report.vo.InsightVO;
import cn.huancai.erp.module.report.vo.MaterialReportVO;
import cn.huancai.erp.module.report.vo.MaterialRowVO;
import cn.huancai.erp.module.report.vo.ScreenReportVO;
import cn.huancai.erp.module.report.vo.TeacherWorkloadVO;
import cn.huancai.erp.module.report.vo.TeachingReportVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportMapper reportMapper;

    public ReportController(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @GetMapping("/business")
    public ApiResult<BusinessReportVO> business(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        BusinessReportVO vo = reportMapper.selectBusiness(campusId, start, end);
        vo.setCampuses(reportMapper.selectCampusCompare(start, end));
        return ApiResult.ok(vo);
    }

    @GetMapping("/teaching")
    public ApiResult<TeachingReportVO> teaching(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        TeachingReportVO vo = reportMapper.selectTeaching(campusId, start, end);
        vo.setTeachers(reportMapper.selectTeacherWorkload(campusId, start, end));
        vo.setCourses(reportMapper.selectCourseConsume(campusId, start, end));
        return ApiResult.ok(vo);
    }

    @GetMapping("/finance")
    public ApiResult<FinanceReportVO> finance(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        FinanceReportVO vo = reportMapper.selectFinance(campusId, start, end);
        vo.setDaily(reportMapper.selectDailyFinance(campusId, start, end));
        return ApiResult.ok(vo);
    }

    @GetMapping("/material")
    public ApiResult<MaterialReportVO> material(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<MaterialRowVO> rows = reportMapper.selectMaterialRows(campusId, start, end);
        MaterialReportVO vo = new MaterialReportVO();
        vo.setRows(rows);
        long count = 0;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        for (MaterialRowVO row : rows) {
            count += row.getCount() == null ? 0 : row.getCount();
            receivable = receivable.add(row.getReceivable() == null ? BigDecimal.ZERO : row.getReceivable());
            received = received.add(row.getReceived() == null ? BigDecimal.ZERO : row.getReceived());
        }
        vo.setCount(count);
        vo.setReceivable(receivable);
        vo.setReceived(received);
        return ApiResult.ok(vo);
    }

    @GetMapping("/screen")
    public ApiResult<ScreenReportVO> screen(@RequestParam(required = false) Long campusId) {
        ScreenReportVO vo = reportMapper.selectScreen(campusId);
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        vo.setCampuses(reportMapper.selectCampusCompare(monthStart, today));
        vo.setTopCourses(reportMapper.selectTopCourses(campusId));
        return ApiResult.ok(vo);
    }

    /**
     * 经营洞察：全部指标来自本系统真实数据，由本地规则引擎生成，不调用外部模型。
     */
    @GetMapping("/insights")
    public ApiResult<List<InsightVO>> insights(@RequestParam(required = false) Long campusId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = monthStart.minusDays(1);

        BusinessReportVO now = reportMapper.selectBusiness(campusId, monthStart, today);
        BusinessReportVO prev = reportMapper.selectBusiness(campusId, lastMonthStart, lastMonthEnd);
        TeachingReportVO teachingNow = reportMapper.selectTeaching(campusId, monthStart, today);
        TeachingReportVO teachingPrev = reportMapper.selectTeaching(campusId, lastMonthStart, lastMonthEnd);
        List<TeacherWorkloadVO> teachers = reportMapper.selectTeacherWorkload(campusId, monthStart, today);
        ScreenReportVO screen = reportMapper.selectScreen(campusId);

        List<InsightVO> list = new ArrayList<>();

        list.add(insight("revenue", "财务", "本月收款",
                "本月已收款 " + money(now.getPeriodPayments()) + "，上月同期 " + money(prev.getPeriodPayments())
                        + "，环比 " + percentChange(now.getPeriodPayments(), prev.getPeriodPayments()) + "。",
                money(now.getPeriodPayments()), trend(now.getPeriodPayments(), prev.getPeriodPayments())));

        list.add(insight("lessons", "教务", "本月课耗",
                "本月消耗课时 " + number(now.getPeriodLessons()) + " 节，上月同期 " + number(prev.getPeriodLessons())
                        + " 节，环比 " + percentChange(now.getPeriodLessons(), prev.getPeriodLessons()) + "。",
                number(now.getPeriodLessons()) + " 节",
                trend(now.getPeriodLessons(), prev.getPeriodLessons())));

        list.add(insight("attendance", "教务", "本月到课率",
                "本月共 " + number(teachingNow.getRecords()) + " 条点名记录，出勤 " + number(teachingNow.getPresent())
                        + " 条，到课率 " + number(teachingNow.getAttendRate()) + "%，覆盖 "
                        + number(teachingNow.getStudents()) + " 名学员。",
                number(teachingNow.getAttendRate()) + "%",
                trend(teachingNow.getAttendRate(), teachingPrev.getAttendRate())));

        if (!teachers.isEmpty()) {
            TeacherWorkloadVO top = teachers.get(0);
            list.add(insight("teacher", "师资", "本月课时最多教师",
                    "本月 " + top.getTeacherName() + "（" + top.getCampusName() + "）课时数最高，共 "
                            + number(top.getLessons()) + " 节，涉及 " + number(top.getClasses()) + " 个班级，到课率 "
                            + number(top.getAttendRate()) + "%。",
                    number(top.getLessons()) + " 节", "up"));
        }

        long consults = now.getConsults() == null ? 0 : now.getConsults();
        long deals = now.getDeals() == null ? 0 : now.getDeals();
        String conversion = consults == 0 ? "—"
                : BigDecimal.valueOf(deals * 100.0 / consults).setScale(1, RoundingMode.HALF_UP) + "%";
        list.add(insight("enroll", "招生", "本月招生转化",
                "本月跟进咨询 " + number(consults) + " 条，产生报名订单 " + number(deals) + " 单，按跟进线索计算的转化率约 "
                        + conversion + "。",
                conversion, "flat"));

        list.add(insight("debt", "财务", "欠费情况",
                "当前订单欠费合计 " + money(now.getDebt()) + "，学员账户余额合计 " + money(now.getBalance())
                        + "，在读学员 " + number(screen.getActiveStudents()) + " 人。",
                money(now.getDebt()),
                now.getDebt() != null && now.getDebt().compareTo(BigDecimal.ZERO) > 0 ? "down" : "flat"));

        list.add(insight("remain", "教务", "课时储备",
                "在读报读 " + number(now.getEnrollmentCount()) + " 个课时账户，剩余课时 "
                        + number(now.getRemainLessons()) + " 节，可支撑约 "
                        + monthsOfLessons(now.getRemainLessons(), now.getPeriodLessons()) + " 个月的当前课耗。",
                number(now.getRemainLessons()) + " 节", "flat"));

        List<CourseConsumeVO> courses = reportMapper.selectCourseConsume(campusId, monthStart, today);
        if (!courses.isEmpty()) {
            CourseConsumeVO top = courses.get(0);
            list.add(insight("course", "教务", "本月课耗最高课程",
                    "本月「" + top.getCourseName() + "」课时消耗最多，共 " + number(top.getLessons()) + " 节，覆盖 "
                            + number(top.getStudents()) + " 名学员。",
                    number(top.getLessons()) + " 节", "up"));
        }
        return ApiResult.ok(list);
    }

    private InsightVO insight(String key, String tag, String title, String detail, String value, String trend) {
        InsightVO vo = new InsightVO();
        vo.setKey(key);
        vo.setTag(tag);
        vo.setTitle(title);
        vo.setDetail(detail);
        vo.setValue(value);
        vo.setTrend(trend);
        return vo;
    }

    private String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return String.format("¥%,.2f", v);
    }

    private String number(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String number(Long value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String percentChange(BigDecimal now, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return "—";
        }
        BigDecimal base = now == null ? BigDecimal.ZERO : now;
        BigDecimal pct = base.subtract(prev)
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, 1, RoundingMode.HALF_UP);
        return (pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + pct.stripTrailingZeros().toPlainString() + "%";
    }

    private String trend(BigDecimal now, BigDecimal prev) {
        if (now == null || prev == null) {
            return "flat";
        }
        int cmp = now.compareTo(prev);
        return cmp > 0 ? "up" : cmp < 0 ? "down" : "flat";
    }

    private String monthsOfLessons(BigDecimal remain, BigDecimal monthly) {
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return "—";
        }
        BigDecimal months = remain.divide(monthly, 1, RoundingMode.HALF_UP);
        return months.stripTrailingZeros().toPlainString();
    }
}
