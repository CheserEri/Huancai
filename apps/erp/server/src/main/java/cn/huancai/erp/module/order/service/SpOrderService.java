package cn.huancai.erp.module.order.service;

import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.order.dto.SpOrderQuery;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.order.vo.SpOrderPageVO;
import cn.huancai.erp.module.order.vo.SpOrderSummaryVO;
import cn.huancai.erp.module.order.vo.SpOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校宝订单镜像查询。数据由一次性迁移脚本导入，页面保持只读，写操作仍在校宝端完成。 */
@Service
public class SpOrderService {

    public static final int EXPORT_LIMIT = 5000;

    private static final Map<Integer, String> TYPE_NAMES = Map.ofEntries(
            Map.entry(1, "报名"), Map.entry(2, "账户余额变动"), Map.entry(3, "转课"), Map.entry(4, "停课"),
            Map.entry(5, "复课"), Map.entry(6, "结课"), Map.entry(7, "退费"), Map.entry(8, "教材杂费"),
            Map.entry(9, "积分"), Map.entry(10, "补费"), Map.entry(11, "资料费")
    );
    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            1, "待支付", 2, "已完成", 3, "已作废", 4, "已失效"
    );
    private static final Map<Integer, String> SOURCE_NAMES = Map.of(
            0, "其他", 1, "线下办理", 2, "校宝秀", 3, "导入", 4, "校宝家", 5, "线上办理"
    );
    private static final Set<String> SORT_FIELDS = Set.of("orderNo", "stuName", "dealDate");

    private final SpOrderMapper orderMapper;
    private final CampusMapper campusMapper;

    public SpOrderService(SpOrderMapper orderMapper, CampusMapper campusMapper) {
        this.orderMapper = orderMapper;
        this.campusMapper = campusMapper;
    }

    public SpOrderPageVO query(SpOrderQuery query) {
        normalize(query);
        long total = orderMapper.countByQuery(query);
        SpOrderPageVO page = new SpOrderPageVO();
        page.setTotal(total);
        int offset = (query.getPageIndex() - 1) * query.getPageSize();
        page.setList(enrich(orderMapper.selectPageByQuery(query, offset, query.getPageSize())));
        page.setSummary(emptyIfNull(orderMapper.selectSummary(query)));
        return page;
    }

    public List<SpOrderVO> export(SpOrderQuery query) {
        normalize(query);
        return enrich(orderMapper.selectPageByQuery(query, 0, EXPORT_LIMIT));
    }

    public SpOrderVO detail(Long id) {
        SpOrderVO row = orderMapper.selectDetail(id);
        if (row == null) {
            return null;
        }
        enrich(List.of(row));
        return row;
    }

    /** 作废订单：状态置为已作废并记录原因，作废后不可还原。 */
    public SpOrderVO voidOrder(Long id, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 100) {
            throw new IllegalArgumentException("作废原因需填写 5~100 个字");
        }
        SpOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatusCode() != null && order.getStatusCode() == 3) {
            throw new IllegalArgumentException("该订单已作废，无需重复操作");
        }
        order.setStatusCode(3);
        order.setVoidReason(trimmed);
        order.setVoidAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return detail(id);
    }

    public Map<String, Object> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campuses", campusMapper.selectList(
                        new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream().map(c -> Map.of("id", c.getId(), "name", c.getName())).toList());
        data.put("creators", orderMapper.selectCreators());
        data.put("salesmen", orderMapper.selectSalesmen());
        data.put("courses", orderMapper.selectCourseNames());
        data.put("items", orderMapper.selectMaterialNames());
        data.put("tags", orderMapper.selectTags());
        // 校宝历史订单没有回写售前人员类型，保留筛选入口但无选项。
        data.put("followUpTypes", List.of());
        return data;
    }

    private void normalize(SpOrderQuery query) {
        if (query.getPageIndex() == null || query.getPageIndex() < 1) {
            query.setPageIndex(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(50);
        }
        if (query.getPageSize() > 200) {
            query.setPageSize(200);
        }
        query.setDealStart(dateOrNull(query.getDealStart()));
        query.setDealEnd(dateOrNull(query.getDealEnd()));
        query.setKeyword(trimToNull(query.getKeyword()));
        if (!SORT_FIELDS.contains(query.getSortField())) {
            query.setSortField("orderNo");
        }
        if (!"asc".equalsIgnoreCase(query.getSortOrder())) {
            query.setSortOrder("desc");
        }
        query.setKeywordType(trimToNull(query.getKeywordType()));
    }

    private List<SpOrderVO> enrich(List<SpOrderVO> rows) {
        for (SpOrderVO row : rows) {
            row.setTypeName(TYPE_NAMES.getOrDefault(row.getTypeCode(), "-"));
            row.setStatusName(STATUS_NAMES.getOrDefault(row.getStatusCode(), "-"));
            row.setSourceName(SOURCE_NAMES.getOrDefault(row.getSourceType(), "-"));
        }
        return rows;
    }

    private SpOrderSummaryVO emptyIfNull(SpOrderSummaryVO summary) {
        if (summary != null) {
            return summary;
        }
        SpOrderSummaryVO empty = new SpOrderSummaryVO();
        empty.setCount(0L);
        empty.setShouldPay(BigDecimal.ZERO);
        empty.setRealPay(BigDecimal.ZERO);
        empty.setRemain(BigDecimal.ZERO);
        empty.setArrearage(BigDecimal.ZERO);
        empty.setCredit(BigDecimal.ZERO);
        return empty;
    }

    private String dateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches("\\d{4}-\\d{2}-\\d{2}") ? trimmed : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
