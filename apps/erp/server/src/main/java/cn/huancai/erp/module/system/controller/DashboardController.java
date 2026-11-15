package cn.huancai.erp.module.system.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;
    private final ClassGroupMapper classGroupMapper;

    public DashboardController(JdbcTemplate jdbc, ClassGroupMapper classGroupMapper) {
        this.jdbc = jdbc;
        this.classGroupMapper = classGroupMapper;
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("students", jdbc.queryForObject("SELECT COUNT(*) FROM student", Long.class));
        data.put("activeStudents", jdbc.queryForObject(
                "SELECT COUNT(*) FROM student WHERE status = '在读'", Long.class));
        data.put("studyingStudents", jdbc.queryForObject(
                "SELECT COUNT(*) FROM student WHERE status = '在读'", Long.class));
        data.put("suspendedStudents", jdbc.queryForObject(
                "SELECT COUNT(*) FROM student WHERE status = '休学'", Long.class));
        data.put("pausedStudents", jdbc.queryForObject(
                "SELECT COUNT(*) FROM student WHERE status = '停课'", Long.class));
        data.put("arrearsStudents", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT student_id) FROM sales_order WHERE status <> '已作废' AND debt > 0", Long.class));
        data.put("remainLessons", jdbc.queryForObject(
                "SELECT COALESCE(SUM(remain_lessons),0) FROM enrollment WHERE status = '生效'", BigDecimal.class));
        data.put("debt", jdbc.queryForObject(
                "SELECT COALESCE(SUM(debt),0) FROM sales_order WHERE status <> '已作废'", BigDecimal.class));
        data.put("revenue", jdbc.queryForObject(
                "SELECT COALESCE(SUM(received),0) FROM sales_order WHERE status <> '已作废'", BigDecimal.class));
        data.put("classes", classGroupMapper.selectCount(null));
        data.put("todayClasses", jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE lesson_date = ?", Long.class, LocalDate.now()));
        data.put("todayOrders", jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE DATE(created_at) = ?", Long.class, LocalDate.now()));

        // 报名人数趋势：最近 30 天每天报名的学员数（按学员去重，缺失日期补 0）
        Map<String, Long> enrolledByDate = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT DATE(created_at) AS d, COUNT(DISTINCT student_id) AS c FROM enrollment "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)",
                LocalDate.now().minusDays(29))) {
            enrolledByDate.put(String.valueOf(row.get("d")), ((Number) row.get("c")).longValue());
        }
        List<Map<String, Object>> enrollTrend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("count", enrolledByDate.getOrDefault(day.toString(), 0L));
            enrollTrend.add(point);
        }
        data.put("enrollTrend", enrollTrend);

        List<Map<String, Object>> topCourses = jdbc.queryForList(
                "SELECT co.name AS courseName, COUNT(*) AS students, COALESCE(SUM(e.remain_lessons),0) AS remainLessons "
                        + "FROM enrollment e JOIN course co ON co.id = e.course_id "
                        + "WHERE e.status = '生效' GROUP BY co.name ORDER BY students DESC LIMIT 6");
        data.put("topCourses", topCourses);
        return ApiResult.ok(data);
    }
}
