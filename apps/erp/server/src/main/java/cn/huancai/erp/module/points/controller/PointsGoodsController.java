package cn.huancai.erp.module.points.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.points.dto.PointsGoodsExchangeForm;
import cn.huancai.erp.module.points.dto.PointsGoodsForm;
import cn.huancai.erp.module.points.entity.PointsExchangeRecord;
import cn.huancai.erp.module.points.entity.PointsGoods;
import cn.huancai.erp.module.points.mapper.PointsExchangeRecordMapper;
import cn.huancai.erp.module.points.mapper.PointsGoodsMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分兑换：兑换物维护（名称 / 所需积分 / 库存 / 状态）与学员积分兑换。
 * 所有积分字段均为积分口径，与金额无关，不得与账户余额混用。
 */
@RestController
@RequestMapping("/api/points-goods")
public class PointsGoodsController {

    private static final String LOCAL_SOURCE = "本地录入";

    private final PointsGoodsMapper goodsMapper;
    private final PointsExchangeRecordMapper recordMapper;
    private final StudentMapper studentMapper;
    private final SpOrderMapper spOrderMapper;
    private final OperatorResolver operatorResolver;

    public PointsGoodsController(PointsGoodsMapper goodsMapper,
                                 PointsExchangeRecordMapper recordMapper,
                                 StudentMapper studentMapper,
                                 SpOrderMapper spOrderMapper,
                                 OperatorResolver operatorResolver) {
        this.goodsMapper = goodsMapper;
        this.recordMapper = recordMapper;
        this.studentMapper = studentMapper;
        this.spOrderMapper = spOrderMapper;
        this.operatorResolver = operatorResolver;
    }

    @GetMapping("/list")
    public ApiResult<List<PointsGoods>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PointsGoods> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PointsGoods::getName, keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PointsGoods::getStatus, status.trim());
        }
        wrapper.orderByAsc(PointsGoods::getId);
        return ApiResult.ok(goodsMapper.selectList(wrapper));
    }

    @PostMapping
    public ApiResult<PointsGoods> create(@RequestBody PointsGoodsForm form) {
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        PointsGoods goods = new PointsGoods();
        apply(goods, form);
        LocalDateTime now = LocalDateTime.now();
        goods.setCreatedAt(now);
        goods.setUpdatedAt(now);
        goodsMapper.insert(goods);
        return ApiResult.ok(goods);
    }

    @PutMapping("/{id}")
    public ApiResult<PointsGoods> update(@PathVariable Long id, @RequestBody PointsGoodsForm form) {
        PointsGoods goods = goodsMapper.selectById(id);
        if (goods == null) {
            return ApiResult.fail("兑换物不存在");
        }
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        apply(goods, form);
        goods.setUpdatedAt(LocalDateTime.now());
        goodsMapper.updateById(goods);
        return ApiResult.ok(goods);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        Long used = recordMapper.selectCount(new LambdaQueryWrapper<PointsExchangeRecord>()
                .eq(PointsExchangeRecord::getGoodsId, id));
        if (used != null && used > 0) {
            return ApiResult.fail("该兑换物已产生兑换记录，禁止删除，可将状态改为「停用」");
        }
        goodsMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    /** 学员使用积分兑换兑换物：扣积分、扣库存、生成兑换记录与积分订单。 */
    @PostMapping("/exchange")
    @Transactional
    public ApiResult<PointsExchangeRecord> exchange(@RequestBody PointsGoodsExchangeForm form) {
        if (form.getStudentId() == null) {
            return ApiResult.fail("请选择学员");
        }
        Student student = studentMapper.selectById(form.getStudentId());
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        if (form.getGoodsId() == null) {
            return ApiResult.fail("请选择兑换物");
        }
        PointsGoods goods = goodsMapper.selectById(form.getGoodsId());
        if (goods == null) {
            return ApiResult.fail("兑换物不存在");
        }
        if (!"正常".equals(goods.getStatus())) {
            return ApiResult.fail("兑换物「" + goods.getName() + "」已停用");
        }
        int quantity = form.getQuantity() == null ? 0 : form.getQuantity();
        if (quantity <= 0) {
            return ApiResult.fail("兑换数量必须大于 0");
        }
        int stock = goods.getStock() == null ? 0 : goods.getStock();
        if (stock < quantity) {
            return ApiResult.fail("库存不足，当前剩余 " + stock + " 件");
        }
        BigDecimal unitPoints = goods.getPointsValue() == null ? BigDecimal.ZERO : goods.getPointsValue();
        if (unitPoints.signum() <= 0) {
            return ApiResult.fail("兑换物「" + goods.getName() + "」未设置所需积分，无法兑换");
        }
        int needPoints = unitPoints.multiply(BigDecimal.valueOf(quantity)).intValue();
        int availablePoints = student.getPoints() == null ? 0 : student.getPoints();
        if (availablePoints < needPoints) {
            return ApiResult.fail("学员积分不足，当前 " + availablePoints + " 积分，本次需 " + needPoints + " 积分");
        }
        String handler = operatorResolver.resolve(form.getHandler());
        String remark = form.getRemark() == null ? null : form.getRemark().trim();

        goods.setStock(stock - quantity);
        goods.setUpdatedAt(LocalDateTime.now());
        goodsMapper.updateById(goods);

        student.setPoints(availablePoints - needPoints);
        student.setUpdatedAt(LocalDateTime.now());
        studentMapper.updateById(student);

        String orderNo = String.valueOf(spOrderMapper.selectNextLocalOrderNo());

        PointsExchangeRecord record = new PointsExchangeRecord();
        record.setOrderNo(orderNo);
        record.setStudentId(student.getId());
        record.setStudentName(student.getName());
        record.setGoodsId(goods.getId());
        record.setGoodsName(goods.getName());
        record.setQuantity(quantity);
        record.setPointsCost(BigDecimal.valueOf(needPoints));
        record.setHandler(handler);
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        SpOrder order = new SpOrder();
        order.setOrderNo(orderNo);
        order.setCampusId(student.getCampusId());
        order.setCampusName(student.getCampusText());
        order.setStudentId(student.getId());
        order.setStuName(student.getName());
        order.setStuTel(student.getPhone());
        order.setStuNamePinyin(student.getName());
        order.setTypeCode(9);
        order.setContent("积分兑换：" + goods.getName() + " × " + quantity);
        order.setShouldPay(BigDecimal.ZERO);
        order.setRealPay(BigDecimal.ZERO);
        order.setRemain(BigDecimal.ZERO);
        order.setArrearage(BigDecimal.ZERO);
        order.setCredit(BigDecimal.valueOf(-needPoints));
        order.setSourceType(1);
        order.setCreator(handler);
        order.setIsReturn(false);
        order.setStatusCode(2);
        order.setContractStatus(0);
        order.setComment("积分兑换，消耗 " + needPoints + " 积分（" + LOCAL_SOURCE + "）");
        order.setHasConfirmedDetail(false);
        order.setHasTextbook(false);
        order.setIsOnlinePayment(false);
        order.setHasMultiLessonEnroll(false);
        LocalDateTime now = LocalDateTime.now();
        order.setDealDate(now);
        order.setCreatedTime(now);
        spOrderMapper.insert(order);
        return ApiResult.ok(record);
    }

    private String validate(PointsGoodsForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "请填写兑换物名称";
        }
        if (form.getPointsValue() == null || form.getPointsValue().signum() <= 0) {
            return "所需积分必须大于 0（积分为独立口径，与金额无关）";
        }
        if (form.getStock() == null || form.getStock() < 0) {
            return "库存数量不能为负数";
        }
        return null;
    }

    private void apply(PointsGoods goods, PointsGoodsForm form) {
        goods.setName(form.getName().trim());
        goods.setPointsValue(form.getPointsValue());
        goods.setStock(form.getStock());
        goods.setStatus(form.getStatus() == null || form.getStatus().isBlank() ? "正常" : form.getStatus().trim());
        goods.setRemark(form.getRemark() == null ? null : form.getRemark().trim());
    }
}
