package cn.huancai.erp.module.order.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.order.dto.SpOrderQuery;
import cn.huancai.erp.module.order.dto.VoidOrderRequest;
import cn.huancai.erp.module.order.service.DailySettlementService;
import cn.huancai.erp.module.order.service.SpOrderService;
import cn.huancai.erp.module.order.vo.SpOrderPageVO;
import cn.huancai.erp.module.order.vo.SpOrderVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 校宝订单列表镜像接口（只读）。 */
@RestController
@RequestMapping("/api/sp-orders")
public class SpOrderController {

    private final SpOrderService orderService;
    private final DailySettlementService dailySettlementService;

    public SpOrderController(SpOrderService orderService, DailySettlementService dailySettlementService) {
        this.orderService = orderService;
        this.dailySettlementService = dailySettlementService;
    }

    @PostMapping("/query")
    public ApiResult<SpOrderPageVO> query(@RequestBody SpOrderQuery query) {
        return ApiResult.ok(orderService.query(query));
    }

    @PostMapping("/export")
    public ApiResult<List<SpOrderVO>> export(@RequestBody SpOrderQuery query) {
        return ApiResult.ok(orderService.export(query));
    }

    @GetMapping("/options")
    public ApiResult<Map<String, Object>> options() {
        return ApiResult.ok(orderService.options());
    }

    /** 日结收费交接单：按 26-11-1/7/8 的手工模板生成单日 xlsx。 */
    @GetMapping("/daily-settlement")
    public ResponseEntity<byte[]> dailySettlement(@RequestParam("date") String date) throws IOException {
        LocalDate day = LocalDate.parse(date);
        byte[] body = dailySettlementService.build(day);
        String filename = String.format("%02d-%d-%d.xlsx",
                day.getYear() % 100, day.getMonthValue(), day.getDayOfMonth());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/{id}")
    public ApiResult<SpOrderVO> detail(@PathVariable Long id) {
        SpOrderVO order = orderService.detail(id);
        return order == null ? ApiResult.fail("订单不存在") : ApiResult.ok(order);
    }

    /** 作废订单：只改本地状态，不写校宝。 */
    @PostMapping("/{id}/void")
    public ApiResult<SpOrderVO> voidOrder(@PathVariable Long id, @RequestBody VoidOrderRequest request) {
        try {
            return ApiResult.ok(orderService.voidOrder(id, request.reason()));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
