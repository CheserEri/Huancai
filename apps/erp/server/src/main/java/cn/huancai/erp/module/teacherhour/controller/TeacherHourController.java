package cn.huancai.erp.module.teacherhour.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.teacherhour.entity.TeacherHourRate;
import cn.huancai.erp.module.teacherhour.service.TeacherHourExportService;
import cn.huancai.erp.module.teacherhour.service.TeacherHourService;
import cn.huancai.erp.module.teacherhour.vo.TeacherHourMonthVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

/** 办理中心 - 教师课时：上课记录、课酬规则、月度课时费导出。 */
@RestController
@RequestMapping("/api/teacher-hours")
public class TeacherHourController {

    private final TeacherHourService teacherHourService;
    private final TeacherHourExportService exportService;

    public TeacherHourController(TeacherHourService teacherHourService,
                                 TeacherHourExportService exportService) {
        this.teacherHourService = teacherHourService;
        this.exportService = exportService;
    }

    @GetMapping("/month")
    public ApiResult<TeacherHourMonthVO> month(@RequestParam("month") String month,
                                               @RequestParam(value = "teacherId", required = false) Long teacherId,
                                               @RequestParam(value = "campusId", required = false) Long campusId) {
        return ApiResult.ok(teacherHourService.month(YearMonth.parse(month), teacherId, campusId));
    }

    @GetMapping("/rates")
    public ApiResult<List<TeacherHourRate>> rates() {
        return ApiResult.ok(teacherHourService.rates());
    }

    @PutMapping("/rates")
    public ApiResult<Void> saveRates(@RequestBody List<TeacherHourRate> rates) {
        teacherHourService.saveRates(rates);
        return ApiResult.ok(null);
    }

    @PutMapping("/records/{scheduleId}/amount")
    public ApiResult<Void> adjust(@PathVariable("scheduleId") Long scheduleId,
                                  @RequestBody AdjustRequest request) {
        teacherHourService.adjust(scheduleId, request.amount(), request.remark());
        return ApiResult.ok(null);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("month") String month,
                                         @RequestParam(value = "teacherId", required = false) Long teacherId,
                                         @RequestParam(value = "campusId", required = false) Long campusId)
            throws IOException {
        YearMonth yearMonth = YearMonth.parse(month);
        byte[] body = exportService.build(yearMonth, teacherId, campusId,
                teacherHourService.campusShortName(campusId));
        String filename = (yearMonth.getYear() % 100) + "年" + yearMonth.getMonthValue() + "月教师课时费.xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20") + "\"");
        headers.set(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    public record AdjustRequest(BigDecimal amount, String remark) {
    }
}
