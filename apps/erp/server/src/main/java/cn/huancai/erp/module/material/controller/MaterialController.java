package cn.huancai.erp.module.material.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.material.mapper.MaterialBuyRecordMapper;
import cn.huancai.erp.module.material.mapper.MaterialMapper;
import cn.huancai.erp.module.material.vo.IncidentalVO;
import cn.huancai.erp.module.material.vo.MaterialBuyRecordVO;
import cn.huancai.erp.module.material.vo.MaterialChangeSummaryVO;
import cn.huancai.erp.module.material.vo.MaterialSettingVO;
import cn.huancai.erp.module.material.vo.MaterialStockChangeVO;
import cn.huancai.erp.module.material.vo.MaterialStockSummaryVO;
import cn.huancai.erp.module.material.vo.MaterialStockVO;
import cn.huancai.erp.module.material.vo.MaterialSummaryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialBuyRecordMapper buyRecordMapper;
    private final MaterialMapper materialMapper;

    public MaterialController(MaterialBuyRecordMapper buyRecordMapper, MaterialMapper materialMapper) {
        this.buyRecordMapper = buyRecordMapper;
        this.materialMapper = materialMapper;
    }

    @GetMapping("/buy-records")
    public ApiResult<IPage<MaterialBuyRecordVO>> buyRecords(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) String receiveStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(buyRecordMapper.selectBuyRecordPage(page(page, size), orderNo, tradeType, receiveStatus));
    }

    @GetMapping("/buy-records/summary")
    public ApiResult<MaterialSummaryVO> summary(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String tradeType,
            @RequestParam(required = false) String receiveStatus) {
        return ApiResult.ok(buyRecordMapper.selectSummary(orderNo, tradeType, receiveStatus));
    }

    @GetMapping("/stock")
    public ApiResult<IPage<MaterialStockVO>> stock(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return ApiResult.ok(materialMapper.selectStockPage(page(page, size), keyword, category, status, lowStock));
    }

    @GetMapping("/stock/summary")
    public ApiResult<MaterialStockSummaryVO> stockSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean lowStock) {
        return ApiResult.ok(materialMapper.selectStockSummary(keyword, category, status, lowStock));
    }

    @GetMapping("/settings")
    public ApiResult<IPage<MaterialSettingVO>> settings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialMapper.selectSettingPage(page(page, size), keyword, category, status));
    }

    @GetMapping("/incidentals")
    public ApiResult<IPage<IncidentalVO>> incidentals(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialMapper.selectIncidentalPage(page(page, size), keyword, category, status));
    }

    @GetMapping("/categories")
    public ApiResult<List<String>> categories() {
        return ApiResult.ok(materialMapper.selectCategories());
    }

    @GetMapping("/stock/changes")
    public ApiResult<IPage<MaterialStockChangeVO>> changes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialMapper.selectChangePage(
                page(page, size), keyword, room, bizType, materialName, start, end));
    }

    @GetMapping("/stock/changes/summary")
    public ApiResult<MaterialChangeSummaryVO> changeSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return ApiResult.ok(materialMapper.selectChangeSummary(keyword, room, bizType, materialName, start, end));
    }

    @GetMapping("/stock/changes/options")
    public ApiResult<Map<String, List<String>>> changeOptions() {
        Map<String, List<String>> data = new LinkedHashMap<>();
        data.put("rooms", materialMapper.selectChangeRooms());
        data.put("materials", materialMapper.selectChangeMaterials());
        return ApiResult.ok(data);
    }

    private <T> Page<T> page(long page, long size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 500));
    }
}
