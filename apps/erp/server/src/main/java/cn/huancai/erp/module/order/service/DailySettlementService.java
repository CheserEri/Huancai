package cn.huancai.erp.module.order.service;

import cn.huancai.erp.common.excel.SimpleXlsx;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.entity.SpOrderItem;
import cn.huancai.erp.module.order.mapper.SpOrderItemMapper;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日结收费交接单：按 26-11-1/7/8 的手工模板生成 Excel。
 * 上区块＝当天收费明细；中区块＝业务办理明细表（学费/教材/杂费/学员账户）；下区块＝账户收支明细表（按收款渠道）。
 */
@Service
public class DailySettlementService {

    private static final double[] COLUMN_WIDTHS = {7.6, 10.7, 19.2, 38.3, 6.4, 5.6, 6, 8.9, 5.3};
    private static final int COLUMNS = COLUMN_WIDTHS.length;
    private static final List<String> METHOD_ORDER =
            List.of("现金", "微信", "支付宝", "银行转账", "POS刷卡", "账户余额");
    private static final String[] CATEGORIES = {"学费", "教材", "杂费", "学员账户"};

    private final SpOrderMapper orderMapper;
    private final SpOrderItemMapper itemMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final ClassGroupMapper classGroupMapper;

    public DailySettlementService(SpOrderMapper orderMapper,
                                  SpOrderItemMapper itemMapper,
                                  EnrollmentMapper enrollmentMapper,
                                  ClassGroupMapper classGroupMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.classGroupMapper = classGroupMapper;
    }

    public byte[] build(LocalDate date) throws IOException {
        List<SpOrder> orders = loadOrders(date);
        Map<Long, List<SpOrderItem>> itemsByOrder = loadItems(orders);
        Map<Long, String> classNames = loadClassNames(itemsByOrder);

        List<SheetRow> rows = new ArrayList<>();
        for (SpOrder order : orders) {
            rows.add(toRow(order, itemsByOrder.getOrDefault(order.getId(), List.of()), classNames));
        }
        return render(rows, date);
    }

    /** 只负责按模板排版，便于脱离数据库校验版式。 */
    byte[] render(List<SheetRow> rows, LocalDate date) throws IOException {
        SimpleXlsx xlsx = new SimpleXlsx("Sheet1", COLUMN_WIDTHS);
        int row = 0;

        // 标题
        xlsx.mergedText(row, 0, row, COLUMNS - 1,
                date.format(DateTimeFormatter.ofPattern("yyyy.M.d")) + "收费情况", SimpleXlsx.STYLE_TITLE);
        xlsx.height(row, 32);
        row++;

        // 表头
        String[] headers = {"订单号", "姓名", "班级", "收费项目", "应收", "优惠", "方式", "实收", "备注"};
        for (int c = 0; c < headers.length; c++) {
            xlsx.text(row, c, headers[c], SimpleXlsx.STYLE_HEADER);
        }
        xlsx.height(row, 20);
        row++;

        // 明细
        BigDecimal totalReal = BigDecimal.ZERO;
        for (SheetRow sheetRow : rows) {
            int start = row;
            int lines = sheetRow.feeItems.size();
            int style = sheetRow.cash ? SimpleXlsx.STYLE_CASH : SimpleXlsx.STYLE_TEXT;
            int feeStyle = sheetRow.cash ? SimpleXlsx.STYLE_CASH_WRAP : SimpleXlsx.STYLE_TEXT_WRAP;
            for (int i = 0; i < lines; i++) {
                xlsx.text(row, 3, sheetRow.feeItems.get(i), feeStyle);
                for (int c : new int[]{0, 1, 2, 4, 5, 6, 7, 8}) {
                    xlsx.text(row, c, "", style);
                }
                xlsx.height(row, 20);
                row++;
            }
            if (lines > 1) {
                for (int c : new int[]{0, 1, 2, 4, 5, 6, 7, 8}) {
                    xlsx.merge(start, c, start + lines - 1, c);
                }
            }
            xlsx.text(start, 0, sheetRow.orderNo, style);
            xlsx.text(start, 1, sheetRow.stuName, style);
            xlsx.text(start, 2, sheetRow.className, style);
            xlsx.text(start, 4, amountText(sheetRow.shouldPay), style);
            xlsx.text(start, 5, amountText(sheetRow.discount), style);
            xlsx.text(start, 6, sheetRow.method, style);
            xlsx.text(start, 7, amountText(sheetRow.realPay), style);
            xlsx.text(start, 8, sheetRow.remark, style);
            totalReal = totalReal.add(nz(sheetRow.realPay));
        }

        // 总计行
        xlsx.text(row, 0, "总计", SimpleXlsx.STYLE_TEXT);
        xlsx.mergedText(row, 1, row, 6, "", SimpleXlsx.STYLE_TEXT);
        xlsx.number(row, 7, totalReal, SimpleXlsx.STYLE_TEXT);
        xlsx.text(row, 8, "", SimpleXlsx.STYLE_TEXT);
        xlsx.height(row, 20);
        row++;

        // 业务办理明细表
        row = writeBusinessDetail(xlsx, row, rows, date);
        // 账户收支明细表
        writeAccountDetail(xlsx, row, rows);

        return xlsx.build();
    }

    private List<SpOrder> loadOrders(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<SpOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SpOrder>()
                .ge(SpOrder::getDealDate, start)
                .lt(SpOrder::getDealDate, end)
                .ne(SpOrder::getStatusCode, 3));
        orders.sort(Comparator
                .comparing((SpOrder o) -> o.getDealDate() == null ? start : o.getDealDate())
                .thenComparing(o -> o.getCreatedTime() == null ? LocalDateTime.MIN : o.getCreatedTime())
                .thenComparingLong(DailySettlementService::orderNoValue));
        return orders;
    }

    private Map<Long, List<SpOrderItem>> loadItems(List<SpOrder> orders) {
        List<Long> orderIds = orders.stream().map(SpOrder::getId).toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<SpOrderItem>()
                        .in(SpOrderItem::getOrderId, orderIds)
                        .orderByAsc(SpOrderItem::getId))
                .stream().collect(Collectors.groupingBy(SpOrderItem::getOrderId));
    }

    private Map<Long, String> loadClassNames(Map<Long, List<SpOrderItem>> itemsByOrder) {
        Set<Long> enrollmentIds = itemsByOrder.values().stream()
                .flatMap(List::stream)
                .map(SpOrderItem::getEnrollmentId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> result = new LinkedHashMap<>();
        if (enrollmentIds.isEmpty()) {
            return result;
        }
        List<Enrollment> enrollments = enrollmentMapper.selectBatchIds(enrollmentIds);
        Set<Long> classIds = enrollments.stream()
                .map(Enrollment::getClassId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> classNames = classIds.isEmpty()
                ? Map.of()
                : classGroupMapper.selectBatchIds(classIds).stream()
                .filter(group -> group.getName() != null)
                .collect(Collectors.toMap(ClassGroup::getId, ClassGroup::getName, (a, b) -> a));
        for (Enrollment enrollment : enrollments) {
            String name = classNames.get(enrollment.getClassId());
            if (name != null) {
                result.put(enrollment.getId(), simplifyClassName(name));
            }
        }
        return result;
    }

    private SheetRow toRow(SpOrder order, List<SpOrderItem> items, Map<Long, String> classNames) {
        SheetRow row = new SheetRow();
        row.order = order;
        row.orderNo = order.getOrderNo() == null ? "" : order.getOrderNo();
        row.stuName = order.getStuName() == null ? "" : order.getStuName();
        row.realPay = nz(order.getRealPay());
        row.shouldPay = nz(order.getShouldPay());
        row.method = normalizeMethod(order.getPayMethod());
        row.cash = row.method.contains("现金");
        int type = order.getTypeCode() == null ? 0 : order.getTypeCode();

        if (type == 9) {
            // 积分单：金额与方式留空，与手工表一致
            row.shouldPay = null;
            row.realPay = null;
            row.method = "";
            row.remark = "积分";
        } else {
            BigDecimal discount = row.shouldPay.subtract(row.realPay);
            row.discount = discount.signum() > 0 ? discount : null;
            row.remark = buildRemark(order);
        }

        // 班级
        Set<String> names = items.stream()
                .map(SpOrderItem::getEnrollmentId)
                .filter(id -> id != null)
                .map(classNames::get)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!names.isEmpty()) {
            row.className = String.join("、", names);
        } else if (type == 1 || type == 10 || type == 3) {
            row.className = simplifyClassName(classNameFromContent(order.getContent()));
        } else {
            row.className = "";
        }

        row.feeItems = buildFeeItems(order, items);
        row.material = materialAmount(items);
        return row;
    }

    private List<String> buildFeeItems(SpOrder order, List<SpOrderItem> items) {
        int type = order.getTypeCode() == null ? 0 : order.getTypeCode();
        if (type == 9) {
            return List.of("积分");
        }
        if (type == 2) {
            return List.of(nz(order.getRealPay()).signum() >= 0 ? "账户充值" : "账户余额支出");
        }
        if (type == 7) {
            return List.of("退费");
        }
        if (type == 8 || type == 11) {
            return List.of(purchaseItemName(order.getContent(), type));
        }
        List<String> result = new ArrayList<>();
        String prefix = order.getContent() != null && order.getContent().startsWith("续费") ? "老生"
                : order.getContent() != null && order.getContent().startsWith("报名") ? "新生" : "";
        for (SpOrderItem item : items) {
            String period = periodText(item.getPriceTemplateName(), item.getLessons());
            if (period.isEmpty() && item.getCourseName() != null) {
                period = item.getCourseName();
            }
            result.add(prefix + period);
        }
        if (result.isEmpty()) {
            result.add(prefix.isEmpty() ? "补费" : prefix);
        }
        // 教材/资料费附加项只挂在第一行，避免重复
        StringBuilder extra = new StringBuilder();
        BigDecimal material = materialAmount(items);
        if (material.signum() > 0) {
            extra.append(containsDataFee(order.getContent()) ? "+资料费" : "+教材");
        }
        if (extra.length() > 0) {
            result.set(0, result.get(0) + extra);
        }
        return result;
    }

    private int writeBusinessDetail(SimpleXlsx xlsx, int row, List<SheetRow> rows, LocalDate date) {
        xlsx.mergedText(row, 0, row, 3,
                "[ " + date.format(DateTimeFormatter.ofPattern("yyyy-M-d")) + "收费交接单 ]",
                SimpleXlsx.STYLE_SECTION);
        xlsx.height(row, 22.1);
        row++;

        xlsx.mergedText(row, 0, row, 3, "业务办理明细表", SimpleXlsx.STYLE_SECTION);
        xlsx.height(row, 14.4);
        row++;

        xlsx.text(row, 0, "", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 1, "收入", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 2, "支出", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 3, "总计", SimpleXlsx.STYLE_SUMMARY);
        xlsx.height(row, 14.4);
        row++;

        Map<String, BigDecimal[]> sums = sumByCategory(rows);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (String category : CATEGORIES) {
            BigDecimal[] pair = sums.get(category);
            xlsx.text(row, 0, category, SimpleXlsx.STYLE_SUMMARY);
            xlsx.number(row, 1, pair[0].signum() == 0 ? null : pair[0], SimpleXlsx.STYLE_SUMMARY_NUM);
            xlsx.number(row, 2, pair[1].signum() == 0 ? null : pair[1], SimpleXlsx.STYLE_SUMMARY_NUM);
            BigDecimal net = pair[0].subtract(pair[1]);
            xlsx.number(row, 3, net.signum() == 0 ? null : net, SimpleXlsx.STYLE_SUMMARY_NUM);
            income = income.add(pair[0]);
            expense = expense.add(pair[1]);
            xlsx.height(row, 14.4);
            row++;
        }

        xlsx.text(row, 0, "总计", SimpleXlsx.STYLE_SUMMARY);
        xlsx.number(row, 1, income, SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.number(row, 2, expense.signum() == 0 ? null : expense, SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.number(row, 3, income.subtract(expense), SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.height(row, 14.4);
        return row + 1;
    }

    private void writeAccountDetail(SimpleXlsx xlsx, int row, List<SheetRow> rows) {
        xlsx.mergedText(row, 0, row, 3, "账户收支明细表", SimpleXlsx.STYLE_SECTION);
        xlsx.height(row, 14.4);
        row++;

        xlsx.text(row, 0, "", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 1, "收入", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 2, "支出", SimpleXlsx.STYLE_SUMMARY);
        xlsx.text(row, 3, "总计", SimpleXlsx.STYLE_SUMMARY);
        xlsx.height(row, 14.4);
        row++;

        Map<String, BigDecimal[]> channels = sumByMethod(rows);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> entry : channels.entrySet()) {
            xlsx.text(row, 0, entry.getKey(), SimpleXlsx.STYLE_SUMMARY);
            xlsx.number(row, 1, entry.getValue()[0].signum() == 0 ? null : entry.getValue()[0], SimpleXlsx.STYLE_SUMMARY_NUM);
            xlsx.number(row, 2, entry.getValue()[1].signum() == 0 ? null : entry.getValue()[1], SimpleXlsx.STYLE_SUMMARY_NUM);
            xlsx.number(row, 3, entry.getValue()[0].subtract(entry.getValue()[1]), SimpleXlsx.STYLE_SUMMARY_NUM);
            income = income.add(entry.getValue()[0]);
            expense = expense.add(entry.getValue()[1]);
            xlsx.height(row, 14.4);
            row++;
        }

        xlsx.text(row, 0, "总计", SimpleXlsx.STYLE_SUMMARY);
        xlsx.number(row, 1, income, SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.number(row, 2, expense.signum() == 0 ? null : expense, SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.number(row, 3, income.subtract(expense), SimpleXlsx.STYLE_SUMMARY_NUM);
        xlsx.height(row, 14.4);
    }

    private Map<String, BigDecimal[]> sumByCategory(List<SheetRow> rows) {
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            sums.put(category, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (SheetRow row : rows) {
            Split split = classify(row);
            add(sums, "学费", split.tuition);
            add(sums, "教材", split.textbook);
            add(sums, "杂费", split.misc);
            add(sums, "学员账户", split.account);
        }
        return sums;
    }

    private Map<String, BigDecimal[]> sumByMethod(List<SheetRow> rows) {
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (SheetRow row : rows) {
            if (row.realPay == null || row.realPay.signum() == 0) {
                continue;
            }
            sums.computeIfAbsent(row.method, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            addOne(sums.get(row.method), row.realPay);
        }
        return sortMethods(sums);
    }

    private static Map<String, BigDecimal[]> sortMethods(Map<String, BigDecimal[]> sums) {
        Map<String, BigDecimal[]> sorted = new LinkedHashMap<>();
        for (String method : METHOD_ORDER) {
            if (sums.containsKey(method)) {
                sorted.put(method, sums.get(method));
            }
        }
        sums.entrySet().stream()
                .filter(entry -> !METHOD_ORDER.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private Split classify(SheetRow row) {
        SpOrder order = row.order;
        Split split = new Split();
        int type = order.getTypeCode() == null ? 0 : order.getTypeCode();
        BigDecimal real = nz(order.getRealPay());
        if (type == 9 || real.signum() == 0) {
            return split;
        }
        if (type == 2) {
            split.account = real;
            return split;
        }
        if (type == 7) {
            split.tuition = real;
            return split;
        }
        if (type == 8 || type == 11) {
            if (type == 11 || containsDataFee(order.getContent())) {
                split.misc = real;
            } else {
                split.textbook = real;
            }
            return split;
        }
        if (type == 4 || type == 5 || type == 6) {
            return split;
        }
        BigDecimal material = row.material;
        BigDecimal sign = BigDecimal.valueOf(real.signum());
        BigDecimal absReal = real.abs();
        BigDecimal materialAbs = material.abs().min(absReal);
        if (materialAbs.signum() > 0) {
            if (containsDataFee(order.getContent())) {
                split.misc = materialAbs.multiply(sign);
            } else {
                split.textbook = materialAbs.multiply(sign);
            }
        }
        split.tuition = absReal.subtract(materialAbs).multiply(sign);
        return split;
    }

    private BigDecimal materialAmount(List<SpOrderItem> items) {
        return items.stream()
                .map(item -> nz(item.getMaterialAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void add(Map<String, BigDecimal[]> sums, String key, BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return;
        }
        addOne(sums.get(key), value);
    }

    private static void addOne(BigDecimal[] pair, BigDecimal value) {
        if (value.signum() >= 0) {
            pair[0] = pair[0].add(value);
        } else {
            pair[1] = pair[1].add(value.abs());
        }
    }

    private static String amountText(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String buildRemark(SpOrder order) {
        if (order.getComment() != null && !order.getComment().isBlank()) {
            return order.getComment().trim();
        }
        if (order.getArrearage() != null && order.getArrearage().signum() > 0) {
            return "欠费" + order.getArrearage().stripTrailingZeros().toPlainString();
        }
        return "";
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "未填写";
        }
        String value = method.trim();
        if (value.contains("现金")) {
            return "现金";
        }
        return value;
    }

    private static String periodText(String templateName, BigDecimal lessons) {
        if (templateName != null) {
            if (templateName.contains("半年")) {
                return "半年交";
            }
            if (templateName.contains("季")) {
                return "季交";
            }
            if (templateName.contains("年")) {
                return "年交";
            }
            if (templateName.contains("月")) {
                return "月交";
            }
        }
        if (lessons != null && lessons.signum() > 0) {
            return lessons.stripTrailingZeros().toPlainString() + "节交";
        }
        return "";
    }

    /** 从订单内容里取班级原文，例如 「报名-趣味拼音（趣味拼音周四C班）；16课时」。 */
    private static String classNameFromContent(String content) {
        if (content == null) {
            return "";
        }
        int start = content.indexOf('（');
        int end = content.indexOf('）', start + 1);
        if (start >= 0 && end > start) {
            return content.substring(start + 1, end);
        }
        return "";
    }

    private static String purchaseItemName(String content, int type) {
        if (content == null || content.isBlank()) {
            return type == 11 ? "资料费" : "教材";
        }
        String value = content;
        int colon = Math.max(value.indexOf('：'), value.indexOf(':'));
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        value = value.split("[；;]")[0].trim();
        String quantity = "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\*(\\d+)=").matcher(value);
        if (matcher.find()) {
            quantity = "×" + matcher.group(1);
        }
        value = value.replaceAll("\\s*\\d+(\\.\\d+)?元.*$", "").trim();
        return value.isEmpty() ? (type == 11 ? "资料费" : "教材") : value + quantity;
    }

    private static String simplifyClassName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        value = value.replaceAll("^【[^】]*】", "");
        value = value.replaceAll("[（(][^（()）]*[）)]\\s*$", "");
        value = value.replace("一年级", "1年").replace("二年级", "2年").replace("三年级", "3年")
                .replace("四年级", "4年").replace("五年级", "5年").replace("六年级", "6年")
                .replace("七年级", "7年").replace("八年级", "8年").replace("九年级", "9年");
        value = value.replace("年级", "年").replace("：", "点").replace(":", "点");
        value = value.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("班$", "");
        return value;
    }

    private static boolean containsDataFee(String content) {
        return content != null && (content.contains("资料费") || content.contains("资料") || content.contains("打印"));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long orderNoValue(SpOrder order) {
        String no = order.getOrderNo();
        if (no == null) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(no.trim());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE - 1;
        }
    }

    private static final class Split {
        BigDecimal tuition = BigDecimal.ZERO;
        BigDecimal textbook = BigDecimal.ZERO;
        BigDecimal misc = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;
    }

    static final class SheetRow {
        SpOrder order;
        String orderNo = "";
        String stuName = "";
        String className = "";
        List<String> feeItems = List.of();
        BigDecimal shouldPay;
        BigDecimal discount;
        String method = "";
        BigDecimal realPay;
        BigDecimal material = BigDecimal.ZERO;
        String remark = "";
        boolean cash;
    }
}
