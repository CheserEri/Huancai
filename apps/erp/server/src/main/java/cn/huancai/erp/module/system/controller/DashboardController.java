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

        List<Map<String, Object>> topCourses = jdbc.queryForList(
                "SELECT co.name AS courseName, COUNT(*) AS students, COALESCE(SUM(e.remain_lessons),0) AS remainLessons "
                        + "FROM enrollment e JOIN course co ON co.id = e.course_id "
                        + "WHERE e.status = '生效' GROUP BY co.name ORDER BY students DESC LIMIT 6");
        data.put("topCourses", topCourses);
        return ApiResult.ok(data);
    }
}
