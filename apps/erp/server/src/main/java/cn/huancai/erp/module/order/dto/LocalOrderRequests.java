package cn.huancai.erp.module.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 本地闭环办理请求。
 *
 * <p>原系统为实际在用的生产系统，本系统不向其写入任何业务数据，
 * 报名/续费/充值/退费/转课只落在本地库（sp_order + enrollment + lesson_txn + student）。</p>
 */
public final class LocalOrderRequests {

    private LocalOrderRequests() {
    }

    /** 报名 / 续费。 */
    public record Enroll(
            Long studentId,
            Long campusId,
            Long courseId,
            Long classId,
            BigDecimal lessons,
            BigDecimal giftLessons,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal shouldPay,
            BigDecimal received,
            List<EnrollMaterial> materials,
            String expireDate,
            String payMethod,
            String creator,
            String remark,
            String tags,
            Long priceTemplateId,
            /** 本系统活动模板（办理中心 - 缴费模板 - 活动模板），仅保存 id/名称快照，可多选叠加。 */
            List<Long> localActivityTemplateIds
    ) {
    }

    /** 续费时一并购买的教材杂费。 */
    public record EnrollMaterial(
            Long materialId,
            String name,
            BigDecimal price,
            BigDecimal quantity
    ) {
    }

    /** 余额充值。 */
    public record Recharge(
            Long studentId,
            Long campusId,
            BigDecimal amount,
            String payMethod,
            String creator,
            String remark,
            String tags
    ) {
    }

    /** 补费：按报读科目冲减该报读的应收未收/课耗超支。 */
    public record Subsidy(
            Long studentId,
            Long campusId,
            Long enrollmentId,
            BigDecimal amount,
            String payMethod,
            String creator,
            String remark,
            String tags
    ) {
    }

    /** 退费。可选扣减某个报读的剩余课时。 */
    public record Refund(
            Long studentId,
            Long campusId,
            Long enrollmentId,
            BigDecimal amount,
            BigDecimal lessons,
            String payMethod,
            String creator,
            String reason
    ) {
    }

    /**
     * 批量退费：整单退掉选中的报读（退剩余课时及绑定学费），并可同时退账户剩余未用学费。
     * 所选项目可为单个课程、账户余额，或多选一次性全退。
     */
    public record BatchRefund(
            Long studentId,
            Long campusId,
            List<Long> enrollmentIds,
            BigDecimal balanceAmount,
            String payMethod,
            String creator,
            String reason
    ) {
    }

    /** 退账户剩余未用学费：退学员账户中未绑定课时的余额。 */
    public record BalanceRefund(
            Long studentId,
            Long campusId,
            BigDecimal amount,
            String payMethod,
            String creator,
            String reason
    ) {
    }

    /**
     * 转课：把原报读的课时按金额转到另一门课程。
     *
     * <p>转出金额 = 本次转出课时 × 转出单价；转入应收 = 转入课时 × 转入单价 - 优惠。
     * 转出金额大于转入应收为应退，反之为应收。应退可实退现金或增加账户余额。</p>
     */
    public record Transfer(
            Long studentId,
            Long campusId,
            Long fromEnrollmentId,
            Long toCourseId,
            Long toClassId,
            /** 全部转出 / 部分转出。 */
            String transferType,
            /** 本次转出课时。全部转出时忽略，取原报读剩余课时。 */
            BigDecimal lessons,
            BigDecimal inLessons,
            BigDecimal inUnitPrice,
            BigDecimal discount,
            /** 应收差额的实收金额。 */
            BigDecimal extraPay,
            /** 应退差额的实退现金。 */
            BigDecimal refundCash,
            /** 应退差额中转入账户余额的金额。 */
            BigDecimal balanceAdd,
            String expireDate,
            String payMethod,
            String creator,
            /** 对内备注。 */
            String remark,
            /** 对外备注（会打印且家长端可见）。 */
            String outerRemark,
            String tags
    ) {
    }

    /**
     * 升期：把原报读剩余课时按价值换算成目标课程课时。
     * 目标课时未传时按「原剩余价值 ÷ 目标单价」自动换算；补差价未付部分挂账为欠费。
     */
    public record Upgrade(
            Long studentId,
            Long campusId,
            Long fromEnrollmentId,
            Long toCourseId,
            BigDecimal toLessons,
            BigDecimal toUnitPrice,
            BigDecimal extraPay,
            String payMethod,
            String creator,
            String remark
    ) {
    }

    /** 记课时（消课）：允许扣成负数，单价未传时按模板/报读折算。 */
    public record Consume(
            Long studentId,
            Long enrollmentId,
            BigDecimal lessons,
            BigDecimal unitPrice,
            String remark
    ) {
    }
}
