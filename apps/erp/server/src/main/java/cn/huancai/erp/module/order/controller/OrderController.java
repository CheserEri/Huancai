package cn.huancai.erp.module.order.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.order.mapper.OrderMapper;
import cn.huancai.erp.module.order.vo.OrderSummaryVO;
import cn.huancai.erp.module.order.vo.OrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderMapper orderMapper;

    public OrderController(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @GetMapping
    public ApiResult<IPage<OrderVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<OrderVO> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        return ApiResult.ok(orderMapper.selectOrderPage(p, keyword, campusId, type, status, start, end));
    }

    @GetMapping("/summary")
    public ApiResult<OrderSummaryVO> summary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResult.ok(orderMapper.selectSummary(keyword, campusId, type, status, start, end));
    }
}
