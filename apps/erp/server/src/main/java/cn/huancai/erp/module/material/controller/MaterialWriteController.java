package cn.huancai.erp.module.material.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.common.OrderNoGenerator;
import cn.huancai.erp.module.material.dto.MaterialForm;
import cn.huancai.erp.module.material.dto.MaterialPurchaseForm;
import cn.huancai.erp.module.material.dto.PointsExchangeForm;
import cn.huancai.erp.module.material.dto.StockChangeForm;
import cn.huancai.erp.module.material.entity.Material;
import cn.huancai.erp.module.material.entity.MaterialBuyRecord;
import cn.huancai.erp.module.material.entity.MaterialStockChange;
import cn.huancai.erp.module.material.mapper.MaterialBuyRecordMapper;
import cn.huancai.erp.module.material.mapper.MaterialMapper;
import cn.huancai.erp.module.material.mapper.MaterialStockChangeMapper;
import cn.huancai.erp.module.material.vo.MaterialOptionsVO;
import cn.huancai.erp.module.material.vo.MaterialPurchaseResultVO;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 教材设置、出入库、购买记录领用确认的写接口。 */
@RestController
@RequestMapping("/api/materials")
public class MaterialWriteController {

    private static final String LOCAL_SOURCE = "本地录入";
    private static final String SYNC_SOURCE = "历史教材设置";
    private static final String CHANGE_SYNC_SOURCE = "历史库存变动";

    private static final List<String> ROOMS = List.of("总库", "鲅鱼圈幻彩国学堂", "新民幻彩国学堂");

    private final MaterialMapper materialMapper;
    private final MaterialStockChangeMapper changeMapper;
    private final MaterialBuyRecordMapper buyRecordMapper;
    private final StudentMapper studentMapper;
    private final SpOrderMapper spOrderMapper;
    private final OperatorResolver operatorResolver;
    private final OrderNoGenerator orderNoGenerator;

    public MaterialWriteController(MaterialMapper materialMapper,
                                   MaterialStockChangeMapper changeMapper,
                                   MaterialBuyRecordMapper buyRecordMapper,
                                   StudentMapper studentMapper,
                                   SpOrderMapper spOrderMapper,
                                   OperatorResolver operatorResolver,
                                   OrderNoGenerator orderNoGenerator) {
        this.materialMapper = materialMapper;
        this.changeMapper = changeMapper;
        this.buyRecordMapper = buyRecordMapper;
        this.studentMapper = studentMapper;
        this.operatorResolver = operatorResolver;
        this.spOrderMapper = spOrderMapper;
        this.orderNoGenerator = orderNoGenerator;
    }

    /* ---------- 下拉选项 ---------- */

    @GetMapping("/options")
    public ApiResult<MaterialOptionsVO> options() {
        List<Material> list = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .in(Material::getSource, SYNC_SOURCE, LOCAL_SOURCE)
                .orderByAsc(Material::getId));
        List<MaterialOptionsVO.MaterialOption> materials = list.stream()
                .map(item -> new MaterialOptionsVO.MaterialOption(
                        item.getId(), item.getName(), item.getCategory(), item.getStatus(), money(item.getSalePrice())))
                .toList();
        Set<String> categories = new LinkedHashSet<>();
        for (Material item : list) {
            if (item.getCategory() != null && !item.getCategory().isBlank()) {
                categories.add(item.getCategory());
            }
        }
        return ApiResult.ok(new MaterialOptionsVO(materials, ROOMS, new ArrayList<>(categories)));
    }

    /* ---------- 教材设置 ---------- */

    @PostMapping("/settings")
    public ApiResult<Material> createSetting(@RequestBody MaterialForm form) {
        String error = validateSetting(form, null);
        if (error != null) {
            return ApiResult.fail(error);
        }
        Material material = new Material();
        material.setName(form.getName().trim());
        material.setCostPrice(money(form.getCostPrice()));
        material.setSalePrice(money(form.getSalePrice()));
        material.setPoints(money(form.getPoints()));
        material.setCategory(trim(form.getCategory()));
        material.setStatus(form.getStatus() == null || form.getStatus().isBlank() ? "正常" : form.getStatus());
        material.setQtyWarehouse(0);
        material.setQtyBayuquan(0);
        material.setQtyXinmin(0);
        material.setQtyTotal(0);
        material.setSource(LOCAL_SOURCE);
        materialMapper.insert(material);
        return ApiResult.ok(material);
    }

    @PutMapping("/settings/{id}")
    public ApiResult<Material> updateSetting(@PathVariable Long id, @RequestBody MaterialForm form) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            return ApiResult.fail("教材不存在");
        }
        String error = validateSetting(form, id);
        if (error != null) {
            return ApiResult.fail(error);
        }
        String name = form.getName().trim();
        String oldName = material.getName();
        boolean renamed = !name.equals(oldName);
        material.setName(name);
        material.setCostPrice(money(form.getCostPrice()));
        material.setSalePrice(money(form.getSalePrice()));
        material.setPoints(money(form.getPoints()));
        material.setCategory(trim(form.getCategory()));
        if (form.getStatus() != null && !form.getStatus().isBlank()) {
            material.setStatus(form.getStatus());
        }
        materialMapper.updateById(material);
        if (renamed) {
            for (MaterialStockChange change : changeMapper.selectList(new LambdaQueryWrapper<MaterialStockChange>()
                    .eq(MaterialStockChange::getMaterialName, oldName))) {
                change.setMaterialName(name);
                changeMapper.updateById(change);
            }
        }
        return ApiResult.ok(material);
    }

    @DeleteMapping("/settings/{id}")
    public ApiResult<Void> deleteSetting(@PathVariable Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            return ApiResult.fail("教材不存在");
        }
        Long refs = changeMapper.selectCount(new LambdaQueryWrapper<MaterialStockChange>()
                .eq(MaterialStockChange::getMaterialName, material.getName()));
        if (refs != null && refs > 0) {
            return ApiResult.fail("该教材已有 " + refs + " 条库存变动记录，不能删除");
        }
        if (material.getQtyTotal() != null && material.getQtyTotal() != 0) {
            return ApiResult.fail("该教材库存不为 0，不能删除");
        }
        materialMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    /* ---------- 出入库 ---------- */

    @PostMapping("/stock/changes")
    @Transactional
    public ApiResult<MaterialStockChange> createChange(@RequestBody StockChangeForm form) {
        String error = validateChange(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        Material material = findMaterial(form.getMaterialName());
        if (material == null) {
            return ApiResult.fail("教材「" + form.getMaterialName() + "」不存在，请先在教材设置中新建");
        }
        MaterialStockChange change = new MaterialStockChange();
        change.setRoomName(form.getRoomName());
        change.setMaterialName(material.getName());
        change.setBizType(form.getBizType());
        int signed = "入库".equals(form.getBizType()) ? form.getQuantity() : -form.getQuantity();
        change.setQuantity(signed);
        change.setUnitPrice(form.getUnitPrice() == null ? material.getCostPrice() : form.getUnitPrice());
        change.setChangeDate(form.getChangeDate() == null ? LocalDate.now() : form.getChangeDate());
        change.setHandler(operatorResolver.resolve(form.getHandler()));
        change.setStudentName(trim(form.getStudentName()));
        change.setExtOrderNo(trim(form.getExtOrderNo()));
        change.setRemark(trim(form.getRemark()));
        change.setSeq(nextSeq());
        change.setSource(LOCAL_SOURCE);
        changeMapper.insert(change);
        applyQty(material, change.getRoomName(), signed);
        return ApiResult.ok(change);
    }

    @PutMapping("/stock/changes/{id}")
    @Transactional
    public ApiResult<MaterialStockChange> updateChange(@PathVariable Long id, @RequestBody StockChangeForm form) {
        MaterialStockChange change = changeMapper.selectById(id);
        if (change == null) {
            return ApiResult.fail("库存变动记录不存在");
        }
        String error = validateChange(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        Material target = findMaterial(form.getMaterialName());
        if (target == null) {
            return ApiResult.fail("教材「" + form.getMaterialName() + "」不存在");
        }
        Material origin = findMaterial(change.getMaterialName());
        if (origin != null) {
            applyQty(origin, change.getRoomName(), -change.getQuantity());
        }
        int signed = "入库".equals(form.getBizType()) ? form.getQuantity() : -form.getQuantity();
        change.setRoomName(form.getRoomName());
        change.setMaterialName(target.getName());
        change.setBizType(form.getBizType());
        change.setQuantity(signed);
        change.setUnitPrice(form.getUnitPrice() == null ? target.getCostPrice() : form.getUnitPrice());
        change.setChangeDate(form.getChangeDate() == null ? change.getChangeDate() : form.getChangeDate());
        change.setHandler(operatorResolver.resolve(form.getHandler()));
        change.setStudentName(trim(form.getStudentName()));
        change.setExtOrderNo(trim(form.getExtOrderNo()));
        change.setRemark(trim(form.getRemark()));
        changeMapper.updateById(change);
        applyQty(target, change.getRoomName(), signed);
        return ApiResult.ok(change);
    }

    @DeleteMapping("/stock/changes/{id}")
    @Transactional
    public ApiResult<Void> deleteChange(@PathVariable Long id) {
        MaterialStockChange change = changeMapper.selectById(id);
        if (change == null) {
            return ApiResult.fail("库存变动记录不存在");
        }
        Material material = findMaterial(change.getMaterialName());
        if (material != null) {
            applyQty(material, change.getRoomName(), -change.getQuantity());
        }
        changeMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    /* ---------- 学员购买教材 ---------- */

    @PostMapping("/purchases")
    @Transactional
    public ApiResult<MaterialPurchaseResultVO> purchase(@RequestBody MaterialPurchaseForm form) {
        if (form.getStudentId() == null) {
            return ApiResult.fail("请选择学员");
        }
        Student student = studentMapper.selectById(form.getStudentId());
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        if (form.getRoomName() == null || !ROOMS.contains(form.getRoomName())) {
            return ApiResult.fail("请选择有效的出库校区：" + String.join("、", ROOMS));
        }
        if (form.getItems() == null || form.getItems().isEmpty()) {
            return ApiResult.fail("请选择教材");
        }
        String payMethod = isBlank(form.getPayMethod()) ? "现金" : form.getPayMethod().trim();

        List<PurchaseLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (MaterialPurchaseForm.Item item : form.getItems()) {
            if (item.getMaterialName() == null || item.getMaterialName().isBlank()) {
                return ApiResult.fail("请选择教材");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                return ApiResult.fail("教材「" + item.getMaterialName() + "」购买数量必须大于 0");
            }
            Material material = findMaterial(item.getMaterialName());
            if (material == null) {
                return ApiResult.fail("教材「" + item.getMaterialName() + "」不存在");
            }
            if ("停用".equals(material.getStatus())) {
                return ApiResult.fail("教材「" + material.getName() + "」已停用");
            }
            int stock = roomStock(material, form.getRoomName());
            if (stock < item.getQuantity()) {
                return ApiResult.fail("教材「" + material.getName() + "」库存不足，当前 "
                        + form.getRoomName() + " 剩余 " + stock + " 本");
            }
            BigDecimal unitPrice = item.getUnitPrice() == null
                    ? money(material.getSalePrice()) : money(item.getUnitPrice());
            if (unitPrice.signum() < 0) {
                return ApiResult.fail("教材「" + material.getName() + "」售价不能为负数");
            }
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(amount);
            lines.add(new PurchaseLine(material, item.getQuantity(), unitPrice, amount));
        }

        // 账户剩余未用学费抵扣：由经办人在购买弹窗中选择，余额不足部分收现金
        BigDecimal balanceAmount = form.getBalanceAmount() == null
                ? BigDecimal.ZERO : money(form.getBalanceAmount());
        if (balanceAmount.signum() < 0) {
            return ApiResult.fail("抵扣金额不能为负数");
        }
        if (balanceAmount.compareTo(total) > 0) {
            return ApiResult.fail("抵扣金额不能大于本次教材金额 ￥" + total.toPlainString());
        }
        BigDecimal available = money(student.getBalance());
        if (balanceAmount.compareTo(available) > 0) {
            return ApiResult.fail("账户剩余未用学费不足，当前可用 ￥" + available.toPlainString());
        }
        BigDecimal cashPay = total.subtract(balanceAmount);
        String orderNo = orderNoGenerator.next();
        String handler = operatorResolver.resolve(form.getHandler());
        String remark = trim(form.getRemark());

        StringBuilder content = new StringBuilder("教材购买：");
        int quantityTotal = 0;
        for (int i = 0; i < lines.size(); i++) {
            PurchaseLine line = lines.get(i);
            if (i > 0) {
                content.append("、");
            }
            content.append(line.material().getName()).append(" × ").append(line.quantity());
            quantityTotal += line.quantity();

            MaterialStockChange change = new MaterialStockChange();
            change.setRoomName(form.getRoomName());
            change.setMaterialName(line.material().getName());
            change.setBizType("出库");
            change.setQuantity(-line.quantity());
            change.setUnitPrice(line.unitPrice());
            change.setChangeDate(LocalDate.now());
            change.setHandler(handler);
            change.setStudentName(student.getName());
            change.setExtOrderNo(orderNo);
            change.setRemark(remark);
            change.setSeq(nextSeq());
            change.setSource(LOCAL_SOURCE);
            changeMapper.insert(change);
            applyQty(line.material(), change.getRoomName(), change.getQuantity());

            MaterialBuyRecord record = new MaterialBuyRecord();
            record.setExtOrderNo(orderNo);
            record.setTradeType("教材购买");
            record.setStudentName(student.getName());
            record.setItemName(line.material().getName());
            record.setQuantity(line.quantity());
            record.setPaidText("¥" + line.amount().stripTrailingZeros().toPlainString());
            record.setPaidAmount(line.amount());
            record.setPaidPoints(0);
            record.setReceiveStatus("已领用");
            record.setCampusName(form.getRoomName());
            record.setHandler(handler);
            record.setHandledDate(LocalDate.now());
            record.setRemark(remark);
            record.setSource(LOCAL_SOURCE);
            buyRecordMapper.insert(record);
        }

        String comment = "教材出库单号：" + orderNo + "；支付方式：" + payMethod;
        if (balanceAmount.signum() > 0) {
            content.append("（账户余额抵扣 ￥").append(balanceAmount.toPlainString())
                    .append("，现金 ￥").append(cashPay.toPlainString()).append("）");
            comment = comment + "；账户剩余未用学费抵扣 ￥" + balanceAmount.toPlainString();
            student.setBalance(available.subtract(balanceAmount));
            student.setUpdatedAt(LocalDateTime.now());
            studentMapper.updateById(student);
        }

        SpOrder order = new SpOrder();
        order.setOrderNo(String.valueOf(spOrderMapper.selectNextLocalOrderNo()));
        order.setCampusId(student.getCampusId());
        order.setCampusName(student.getCampusText());
        order.setStudentId(student.getId());
        order.setStuName(student.getName());
        order.setStuTel(student.getPhone());
        order.setStuNamePinyin(student.getName());
        order.setTypeCode(8);
        order.setContent(content.toString());
        order.setShouldPay(total);
        order.setRealPay(cashPay);
        order.setRemain(BigDecimal.ZERO);
        order.setArrearage(BigDecimal.ZERO);
        order.setCredit(BigDecimal.ZERO);
        order.setPayMethod(payMethod);
        order.setSourceType(1);
        order.setCreator(handler);
        order.setIsReturn(false);
        order.setStatusCode(2);
        order.setContractStatus(0);
        order.setComment(comment);
        order.setHasConfirmedDetail(true);
        order.setHasTextbook(true);
        order.setIsOnlinePayment(false);
        order.setHasMultiLessonEnroll(false);
        LocalDateTime now = LocalDateTime.now();
        order.setDealDate(now);
        order.setCreatedTime(now);
        spOrderMapper.insert(order);
        return ApiResult.ok(new MaterialPurchaseResultVO(
                orderNo, lines.size(), quantityTotal, total, balanceAmount, cashPay, payMethod));
    }

    /* ---------- 学员积分兑换教材 ---------- */

    @PostMapping("/points-exchange")
    @Transactional
    public ApiResult<MaterialBuyRecord> exchangeByPoints(@RequestBody PointsExchangeForm form) {
        if (form.getStudentId() == null) {
            return ApiResult.fail("请选择学员");
        }
        Student student = studentMapper.selectById(form.getStudentId());
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        if (form.getRoomName() == null || !ROOMS.contains(form.getRoomName())) {
            return ApiResult.fail("请选择有效的出库校区：" + String.join("、", ROOMS));
        }
        if (form.getQuantity() == null || form.getQuantity() <= 0) {
            return ApiResult.fail("兑换数量必须大于 0");
        }
        Material material = findMaterial(form.getMaterialName());
        if (material == null) {
            return ApiResult.fail("教材「" + form.getMaterialName() + "」不存在");
        }
        if ("停用".equals(material.getStatus())) {
            return ApiResult.fail("教材「" + material.getName() + "」已停用");
        }
        int stock = roomStock(material, form.getRoomName());
        if (stock < form.getQuantity()) {
            return ApiResult.fail("库存不足，当前 " + form.getRoomName() + " 剩余 " + stock + " 本");
        }
        BigDecimal unitPoints = material.getPoints() == null ? BigDecimal.ZERO : money(material.getPoints());
        if (unitPoints.signum() <= 0) {
            return ApiResult.fail("教材「" + material.getName() + "」未设置积分价格，无法兑换");
        }
        int needPoints = unitPoints.multiply(BigDecimal.valueOf(form.getQuantity())).intValue();
        int availablePoints = student.getPoints() == null ? 0 : student.getPoints();
        if (availablePoints < needPoints) {
            return ApiResult.fail("学员积分不足，当前 " + availablePoints + " 积分，本次需 " + needPoints + " 积分");
        }
        String orderNo = orderNoGenerator.next();
        String handler = operatorResolver.resolve(form.getHandler());
        String remark = trim(form.getRemark());

        MaterialStockChange change = new MaterialStockChange();
        change.setRoomName(form.getRoomName());
        change.setMaterialName(material.getName());
        change.setBizType("出库");
        change.setQuantity(-form.getQuantity());
        change.setUnitPrice(BigDecimal.ZERO);
        change.setChangeDate(LocalDate.now());
        change.setHandler(handler);
        change.setStudentName(student.getName());
        change.setExtOrderNo(orderNo);
        change.setRemark(remark == null ? "积分兑换" : "积分兑换：" + remark);
        change.setSeq(nextSeq());
        change.setSource(LOCAL_SOURCE);
        changeMapper.insert(change);
        applyQty(material, change.getRoomName(), change.getQuantity());

        MaterialBuyRecord record = new MaterialBuyRecord();
        record.setExtOrderNo(orderNo);
        record.setTradeType("积分兑换");
        record.setStudentName(student.getName());
        record.setItemName(material.getName());
        record.setQuantity(form.getQuantity());
        record.setPaidText(needPoints + " 积分");
        record.setPaidAmount(BigDecimal.ZERO);
        record.setPaidPoints(needPoints);
        record.setReceiveStatus("已领用");
        record.setCampusName(form.getRoomName());
        record.setHandler(handler);
        record.setHandledDate(LocalDate.now());
        record.setRemark(remark);
        record.setSource(LOCAL_SOURCE);
        buyRecordMapper.insert(record);

        student.setPoints(availablePoints - needPoints);
        student.setUpdatedAt(LocalDateTime.now());
        studentMapper.updateById(student);

        SpOrder order = new SpOrder();
        order.setOrderNo(String.valueOf(spOrderMapper.selectNextLocalOrderNo()));
        order.setCampusId(student.getCampusId());
        order.setCampusName(student.getCampusText());
        order.setStudentId(student.getId());
        order.setStuName(student.getName());
        order.setStuTel(student.getPhone());
        order.setStuNamePinyin(student.getName());
        order.setTypeCode(9);
        order.setContent("积分兑换：" + material.getName() + " × " + form.getQuantity());
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
        order.setComment("积分兑换，消耗 " + needPoints + " 积分，出库单号：" + orderNo);
        order.setHasConfirmedDetail(true);
        order.setHasTextbook(true);
        order.setIsOnlinePayment(false);
        order.setHasMultiLessonEnroll(false);
        LocalDateTime now = LocalDateTime.now();
        order.setDealDate(now);
        order.setCreatedTime(now);
        spOrderMapper.insert(order);
        return ApiResult.ok(record);
    }

    /* ---------- 购买记录领用确认 ---------- */

    @PostMapping("/buy-records/{id}/receive")
    public ApiResult<MaterialBuyRecord> receive(@PathVariable Long id) {
        return updateReceiveStatus(id, "已领用");
    }

    @PostMapping("/buy-records/{id}/cancel-receive")
    public ApiResult<MaterialBuyRecord> cancelReceive(@PathVariable Long id) {
        return updateReceiveStatus(id, "未领用");
    }

    /* ---------- 内部方法 ---------- */

    private ApiResult<MaterialBuyRecord> updateReceiveStatus(Long id, String status) {
        MaterialBuyRecord record = buyRecordMapper.selectById(id);
        if (record == null) {
            return ApiResult.fail("购买记录不存在");
        }
        if (status.equals(record.getReceiveStatus())) {
            return ApiResult.fail("该记录已是「" + status + "」状态");
        }
        record.setReceiveStatus(status);
        buyRecordMapper.updateById(record);
        return ApiResult.ok(record);
    }

    private String validateSetting(MaterialForm form, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "教材名称不能为空";
        }
        String name = form.getName().trim();
        LambdaQueryWrapper<Material> query = new LambdaQueryWrapper<Material>()
                .eq(Material::getName, name)
                .in(Material::getSource, SYNC_SOURCE, LOCAL_SOURCE);
        if (excludeId != null) {
            query.ne(Material::getId, excludeId);
        }
        Long exists = materialMapper.selectCount(query);
        if (exists != null && exists > 0) {
            return "已存在同名教材：" + name;
        }
        if (form.getCostPrice() != null && form.getCostPrice().signum() < 0) {
            return "成本价不能为负数";
        }
        if (form.getSalePrice() != null && form.getSalePrice().signum() < 0) {
            return "售价不能为负数";
        }
        if (form.getPoints() != null && form.getPoints().signum() < 0) {
            return "积分不能为负数";
        }
        return null;
    }

    private String validateChange(StockChangeForm form) {
        if (form.getRoomName() == null || !ROOMS.contains(form.getRoomName())) {
            return "请选择有效的仓库/校区：" + String.join("、", ROOMS);
        }
        if (form.getMaterialName() == null || form.getMaterialName().isBlank()) {
            return "请选择教材";
        }
        if (form.getBizType() == null || (!"入库".equals(form.getBizType()) && !"出库".equals(form.getBizType()))) {
            return "业务类型只能是「入库」或「出库」";
        }
        if (form.getQuantity() == null || form.getQuantity() <= 0) {
            return "数量必须大于 0";
        }
        return null;
    }

    private Material findMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getName, name)
                .in(Material::getSource, SYNC_SOURCE, LOCAL_SOURCE)
                .last("LIMIT 1"));
    }

    private int roomStock(Material material, String roomName) {
        return switch (roomName) {
            case "总库" -> nz(material.getQtyWarehouse());
            case "鲅鱼圈幻彩国学堂" -> nz(material.getQtyBayuquan());
            case "新民幻彩国学堂" -> nz(material.getQtyXinmin());
            default -> 0;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int nextSeq() {
        MaterialStockChange last = changeMapper.selectOne(new LambdaQueryWrapper<MaterialStockChange>()
                .orderByDesc(MaterialStockChange::getSeq)
                .last("LIMIT 1"));
        return last == null || last.getSeq() == null ? 1 : last.getSeq() + 1;
    }

    private void applyQty(Material material, String roomName, int delta) {
        int warehouse = nz(material.getQtyWarehouse());
        int bayuquan = nz(material.getQtyBayuquan());
        int xinmin = nz(material.getQtyXinmin());
        switch (roomName) {
            case "总库" -> warehouse += delta;
            case "鲅鱼圈幻彩国学堂" -> bayuquan += delta;
            case "新民幻彩国学堂" -> xinmin += delta;
            default -> throw new IllegalArgumentException("未知仓库：" + roomName);
        }
        material.setQtyWarehouse(warehouse);
        material.setQtyBayuquan(bayuquan);
        material.setQtyXinmin(xinmin);
        material.setQtyTotal(warehouse + bayuquan + xinmin);
        materialMapper.updateById(material);
    }

    private record PurchaseLine(Material material, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
