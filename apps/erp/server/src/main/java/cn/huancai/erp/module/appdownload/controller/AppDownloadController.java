package cn.huancai.erp.module.appdownload.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.appdownload.dto.AppDownloadInfoVO;
import cn.huancai.erp.module.appdownload.entity.AppDownloadLog;
import cn.huancai.erp.module.appdownload.mapper.AppDownloadMapper;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 安卓试用 App 安装包下载：每个登录账号每日最多下载 5 次。 */
@RestController
@RequestMapping("/api/app")
public class AppDownloadController {

    /** 每个账号每日下载次数上限。 */
    private static final int DAILY_LIMIT = 5;

    private final AppDownloadMapper downloadMapper;
    private final StaffMapper staffMapper;
    private final ObjectMapper objectMapper;
    private final String apkPath;
    private final String apkVersion;

    public AppDownloadController(AppDownloadMapper downloadMapper,
                                 StaffMapper staffMapper,
                                 ObjectMapper objectMapper,
                                 @Value("${app.apk-path:./storage/app/huancai-app.apk}") String apkPath,
                                 @Value("${app.apk-version:1.0.0}") String apkVersion) {
        this.downloadMapper = downloadMapper;
        this.staffMapper = staffMapper;
        this.objectMapper = objectMapper;
        this.apkPath = apkPath;
        this.apkVersion = apkVersion;
    }

    /** 当日剩余下载次数与安装包信息。 */
    @GetMapping("/info")
    public ApiResult<AppDownloadInfoVO> info(
            @RequestAttribute(AuthInterceptor.ATTR_STAFF_ID) Long staffId) {
        int used = countToday(staffId);
        Path file = resolveApk();
        long size = 0L;
        String updatedAt = null;
        try {
            if (file != null) {
                size = Files.size(file);
                updatedAt = Files.getLastModifiedTime(file).toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
        } catch (IOException ignored) {
            // 文件读取失败时按未上传处理
        }
        return ApiResult.ok(new AppDownloadInfoVO(
                DAILY_LIMIT, used, Math.max(0, DAILY_LIMIT - used),
                apkVersion, "幻彩学堂-v" + apkVersion + ".apk", size, updatedAt));
    }

    /** 下载安装包；超出当日次数直接返回 403，避免重复占用带宽。 */
    @GetMapping("/download")
    public void download(@RequestAttribute(AuthInterceptor.ATTR_STAFF_ID) Long staffId,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        int used = countToday(staffId);
        if (used >= DAILY_LIMIT) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "今日下载次数已用完（每个账号每日限 " + DAILY_LIMIT + " 次），请明天再试");
            return;
        }
        Path file = resolveApk();
        if (file == null) {
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "安装包尚未上传到服务器，请联系管理员");
            return;
        }
        // 先记录再输出，保证并发点击时也不会突破每日上限
        Staff staff = staffMapper.selectById(staffId);
        AppDownloadLog log = new AppDownloadLog();
        log.setStaffId(staffId);
        log.setStaffName(staff == null ? null : staff.getName());
        log.setIp(clientIp(request));
        log.setDownloadDate(LocalDate.now());
        log.setCreatedAt(LocalDateTime.now());
        downloadMapper.insert(log);

        String fileName = "幻彩学堂-v" + apkVersion + ".apk";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.android.package-archive");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"huancai-app.apk\"; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(Files.size(file));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
            out.flush();
        }
    }

    private int countToday(Long staffId) {
        Long count = downloadMapper.selectCount(new LambdaQueryWrapper<AppDownloadLog>()
                .eq(AppDownloadLog::getStaffId, staffId)
                .eq(AppDownloadLog::getDownloadDate, LocalDate.now()));
        return count == null ? 0 : count.intValue();
    }

    private Path resolveApk() {
        Path file = Paths.get(apkPath).toAbsolutePath().normalize();
        return Files.isRegularFile(file) ? file : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiResult<>(status, message, null)));
    }
}
