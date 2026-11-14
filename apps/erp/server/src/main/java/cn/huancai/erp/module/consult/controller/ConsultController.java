package cn.huancai.erp.module.consult.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.consult.mapper.ConsultMapper;
import cn.huancai.erp.module.consult.vo.ConsultSummaryVO;
import cn.huancai.erp.module.consult.vo.ConsultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/consult")
public class ConsultController {

    private final ConsultMapper consultMapper;

    public ConsultController(ConsultMapper consultMapper) {
        this.consultMapper = consultMapper;
    }

    @GetMapping
    public ApiResult<IPage<ConsultVO>> list(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer enrolled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        long safeSize = Math.min(Math.max(size, 1), 200);
        Page<ConsultVO> p = new Page<>(page, safeSize);
        return ApiResult.ok(consultMapper.selectConsultPage(p, campusId, keyword, source, status, enrolled, start, end));
    }

    @GetMapping("/summary")
    public ApiResult<ConsultSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer enrolled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        ConsultSummaryVO vo = consultMapper.selectSummary(campusId, keyword, source, status, enrolled, start, end);
        vo.setSources(consultMapper.selectSources(campusId, keyword, source, status, enrolled, start, end));
        return ApiResult.ok(vo);
    }
}
