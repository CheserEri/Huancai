package cn.huancai.erp.module.print.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.order.service.SpOrderService;
import cn.huancai.erp.module.order.vo.SpOrderVO;
import cn.huancai.erp.module.print.service.ThermalPrinterService;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.student.vo.EnrollmentVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 58mm 小票打印机接口：直发 ESC/POS，不经过 Windows 打印队列。 */
@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final ThermalPrinterService printerService;
    private final SpOrderService orderService;
    private final StudentMapper studentMapper;

    public PrintController(ThermalPrinterService printerService, SpOrderService orderService,
                           StudentMapper studentMapper) {
        this.printerService = printerService;
        this.orderService = orderService;
        this.studentMapper = studentMapper;
    }

    /** 打印订单小票。 */
    @PostMapping("/receipt/{orderId}")
    public ApiResult<Map<String, Object>> receipt(@PathVariable Long orderId) {
        SpOrderVO order = orderService.detail(orderId);
        if (order == null) {
            return ApiResult.fail("订单不存在");
        }
        try {
            return ApiResult.ok(printerService.printReceipt(order));
        } catch (Exception e) {
            return ApiResult.fail("小票打印失败：" + e.getMessage());
        }
    }

    /** 打印学员听课证小票。 */
    @PostMapping("/ticket/{studentId}/{enrollmentId}")
    public ApiResult<Map<String, Object>> ticket(@PathVariable Long studentId,
                                                 @PathVariable Long enrollmentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        EnrollmentVO enrollment = studentMapper.selectEnrollments(studentId).stream()
                .filter(item -> item.getId().equals(enrollmentId))
                .findFirst()
                .orElse(null);
        if (enrollment == null) {
            return ApiResult.fail("报读记录不存在");
        }
        try {
            return ApiResult.ok(printerService.printTicket(
                    student.getCampusText(),
                    student.getName(),
                    student.getPhone(),
                    enrollment.getCourseName(),
                    enrollment.getClassName(),
                    enrollment.getTeacher(),
                    enrollment.getRemainLessons(),
                    enrollment.getExpireDate() == null ? null : enrollment.getExpireDate().toString()));
        } catch (Exception e) {
            return ApiResult.fail("小票打印失败：" + e.getMessage());
        }
    }

    /** 打印自检小票，用于确认设备走纸与中文输出。 */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test() {
        try {
            return ApiResult.ok(printerService.printTest());
        } catch (Exception e) {
            return ApiResult.fail("测试打印失败：" + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        return ApiResult.ok(printerService.status());
    }
}
