package cn.huancai.erp.module.finance.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.finance.mapper.FinanceMapper;
import cn.huancai.erp.module.finance.vo.FinanceSummaryVO;
import cn.huancai.erp.module.finance.vo.MonthlyRow;
import cn.huancai.erp.module.finance.vo.PaymentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceMapper financeMapper;

    public FinanceController(FinanceMapper financeMapper) {
        this.financeMapper = financeMapper;
    }

    @GetMapping("/payments")
    public ApiResult<IPage<PaymentVO>> payments(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<PaymentVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return ApiResult.ok(financeMapper.selectPaymentPage(p, campusId, keyword, start, end));
    }

    @GetMapping("/summary")
    public ApiResult<FinanceSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(financeMapper.selectSummary(campusId, keyword, start, end));
    }

    @GetMapping("/monthly")
    public ApiResult<List<MonthlyRow>> monthly(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "12") int months) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.withDayOfMonth(1).minusMonths(Math.max(months, 1) - 1L);
        return ApiResult.ok(financeMapper.selectMonthly(campusId, keyword, from, today));
    }
}
