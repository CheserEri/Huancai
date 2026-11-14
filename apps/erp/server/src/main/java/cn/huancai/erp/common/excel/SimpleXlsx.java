package cn.huancai.erp.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 xlsx 生成器：只覆盖日结收费表需要的合并单元格、字体、细边框、整行标红、列宽行高与 A4 打印设置。
 * 不引入 POI，直接拼 OOXML 后打 zip。
 */
public class SimpleXlsx {

    public static final int STYLE_TITLE = 1;
    public static final int STYLE_HEADER = 2;
    public static final int STYLE_TEXT = 3;
    public static final int STYLE_TEXT_WRAP = 4;
    public static final int STYLE_SUMMARY = 5;
    public static final int STYLE_SUMMARY_NUM = 6;
    public static final int STYLE_CASH = 7;
    public static final int STYLE_CASH_WRAP = 8;
    public static final int STYLE_SECTION = 9;
    /** 微软雅黑16：教师课时费汇总表标题 */
    public static final int STYLE_TITLE_16 = 10;
    /** 微软雅黑14：教师课时费明细页标题 */
    public static final int STYLE_TITLE_14 = 11;
    /** 微软雅黑12：明细表表头（日期/人数/价格） */
    public static final int STYLE_HEAD = 12;
    /** 微软雅黑12：左对齐正文（课程名） */
    public static final int STYLE_TEXT_LEFT = 13;
    /** 微软雅黑14 + 全框线：汇总表合计行 */
    public static final int STYLE_SUMMARY_14 = 14;

    private record Cell(String value, boolean numeric, int style) {
    }

    private record SheetState(String name, double[] widths, Map<Integer, Map<Integer, Cell>> cells,
                              List<String> merges, Map<Integer, Double> heights) {
    }

    private final List<SheetState> sheets = new ArrayList<>();
    private String sheetName;
    private double[] columnWidths;
    private Map<Integer, Map<Integer, Cell>> cells = new TreeMap<>();
    private List<String> merges = new ArrayList<>();
    private Map<Integer, Double> rowHeights = new HashMap<>();

    public SimpleXlsx(String sheetName, double[] columnWidths) {
        this.sheetName = sheetName;
        this.columnWidths = columnWidths;
    }

    /** 追加一个新 sheet，当前 sheet 内容保持不变；后续写入都落在新 sheet 上。 */
    public SimpleXlsx newSheet(String name, double[] widths) {
        sheets.add(snapshot());
        sheetName = name;
        columnWidths = widths;
        cells = new TreeMap<>();
        merges = new ArrayList<>();
        rowHeights = new HashMap<>();
        return this;
    }

    private SheetState snapshot() {
        return new SheetState(sheetName, columnWidths, cells, merges, rowHeights);
    }

    private List<SheetState> allSheets() {
        List<SheetState> all = new ArrayList<>(sheets);
        all.add(snapshot());
        return all;
    }

    public SimpleXlsx text(int row, int col, String text, int style) {
        cell(row, col, new Cell(text == null ? "" : text, false, style));
        return this;
    }

    public SimpleXlsx number(int row, int col, java.math.BigDecimal value, int style) {
        if (value == null) {
            return text(row, col, "", style);
        }
        String plain = value.stripTrailingZeros().toPlainString();
        cell(row, col, new Cell(plain, true, style));
        return this;
    }

    public SimpleXlsx merge(int row1, int col1, int row2, int col2) {
        merges.add(ref(row1, col1) + ":" + ref(row2, col2));
        return this;
    }

    /** 合并区域并给区域内每个单元格套同样式，保证外框线完整。 */
    public SimpleXlsx mergedText(int row1, int col1, int row2, int col2, String text, int style) {
        merge(row1, col1, row2, col2);
        for (int r = row1; r <= row2; r++) {
            for (int c = col1; c <= col2; c++) {
                text(r, c, r == row1 && c == col1 ? text : "", style);
            }
        }
        return this;
    }

    public SimpleXlsx height(int row, double points) {
        rowHeights.put(row, points);
        return this;
    }

    public byte[] build() throws IOException {
        List<SheetState> all = allSheets();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", contentTypes(all.size()));
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook(all));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(all.size()));
            put(zip, "xl/styles.xml", styles());
            for (int i = 0; i < all.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(all.get(i), i == 0));
            }
        }
        return out.toByteArray();
    }

    private void cell(int row, int col, Cell cell) {
        cells.computeIfAbsent(row, key -> new TreeMap<>()).put(col, cell);
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String ref(int row, int col) {
        StringBuilder sb = new StringBuilder();
        int n = col + 1;
        while (n > 0) {
            int mod = (n - 1) % 26;
            sb.insert(0, (char) ('A' + mod));
            n = (n - 1) / 26;
        }
        return sb.append(row + 1).toString();
    }

    private String contentTypes(int sheetCount) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
                .append("</Types>");
        return sb.toString();
    }

    private String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbook(List<SheetState> all) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>");
        for (int i = 0; i < all.size(); i++) {
            sb.append("<sheet name=\"").append(escape(all.get(i).name())).append("\" sheetId=\"")
                    .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRels(int sheetCount) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                .append("</Relationships>");
        return sb.toString();
    }

    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"7\">"
                + "<font><sz val=\"11\"/><name val=\"宋体\"/></font>"
                + "<font><sz val=\"20\"/><name val=\"宋体\"/></font>"
                + "<font><sz val=\"12\"/><name val=\"微软雅黑\"/></font>"
                + "<font><sz val=\"10.5\"/><name val=\"Arial\"/></font>"
                + "<font><sz val=\"11\"/><name val=\"Arial\"/></font>"
                + "<font><sz val=\"16\"/><name val=\"微软雅黑\"/></font>"
                + "<font><sz val=\"14\"/><name val=\"微软雅黑\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFF0000\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"4\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color indexed=\"64\"/></left><right style=\"thin\"><color indexed=\"64\"/></right>"
                + "<top style=\"thin\"><color indexed=\"64\"/></top><bottom style=\"thin\"><color indexed=\"64\"/></bottom><diagonal/></border>"
                + "<border><left/><right/><top style=\"thin\"><color indexed=\"64\"/></top>"
                + "<bottom style=\"thin\"><color indexed=\"64\"/></bottom><diagonal/></border>"
                + "<border><left/><right/><top/><bottom style=\"thin\"><color indexed=\"64\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"15\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                // 1 标题：宋体20，仅下框线
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"3\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 2 表头：微软雅黑12，全框线，换行
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                // 3 正文
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 4 正文换行
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                // 5 汇总区文字
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 6 汇总区数字
                + "<xf numFmtId=\"0\" fontId=\"4\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 7 现金行标红
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 8 现金行标红 + 换行
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                // 9 区块标题
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"2\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 10 汇总表标题：微软雅黑16，无框线
                + "<xf numFmtId=\"0\" fontId=\"5\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 11 明细页标题：微软雅黑14，无框线
                + "<xf numFmtId=\"0\" fontId=\"6\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 12 明细表头：微软雅黑12，全框线
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                // 13 课程名：微软雅黑12，全框线，左对齐
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"center\"/></xf>"
                // 14 合计行：微软雅黑14，全框线
                + "<xf numFmtId=\"0\" fontId=\"6\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private String sheet(SheetState state, boolean selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
                .append("<sheetViews><sheetView showGridLines=\"0\"")
                .append(selected ? " tabSelected=\"1\"" : "")
                .append(" workbookViewId=\"0\"/></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"20\"/>");
        sb.append("<cols>");
        for (int i = 0; i < state.widths().length; i++) {
            // xlsx 的 width 与 Excel 显示的 ColumnWidth 存在固定差（约 0.8 字符），补偿后与手工模板一致
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(state.widths()[i] + 0.8).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols>");
        sb.append("<sheetData>");
        for (Map.Entry<Integer, Map<Integer, Cell>> rowEntry : state.cells().entrySet()) {
            Double height = state.heights().get(rowEntry.getKey());
            sb.append("<row r=\"").append(rowEntry.getKey() + 1).append("\"");
            if (height != null) {
                sb.append(" ht=\"").append(height).append("\" customHeight=\"1\"");
            }
            sb.append(">");
            for (Map.Entry<Integer, Cell> colEntry : rowEntry.getValue().entrySet()) {
                Cell cell = colEntry.getValue();
                sb.append("<c r=\"").append(ref(rowEntry.getKey(), colEntry.getKey()))
                        .append("\" s=\"").append(cell.style()).append("\"");
                if (cell.numeric()) {
                    sb.append("><v>").append(cell.value()).append("</v></c>");
                } else if (!cell.value().isEmpty()) {
                    sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(cell.value())).append("</t></is></c>");
                } else {
                    sb.append("/>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (!state.merges().isEmpty()) {
            sb.append("<mergeCells count=\"").append(state.merges().size()).append("\">");
            for (String merge : state.merges()) {
                sb.append("<mergeCell ref=\"").append(merge).append("\"/>");
            }
            sb.append("</mergeCells>");
        }
        sb.append("<printOptions horizontalCentered=\"1\" verticalCentered=\"0\"/>")
                .append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>")
                .append("<pageSetup paperSize=\"9\" orientation=\"portrait\" fitToWidth=\"1\" fitToHeight=\"1\"/>")
                .append("</worksheet>");
        return sb.toString();
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            // 去掉 XML 1.0 不允许的控制字符，避免 Excel 打开时出现问号或修复提示
            if (ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') {
                continue;
            }
            if (Character.isSurrogate(ch) && !isValidSurrogate(value, i)) {
                continue;
            }
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isValidSurrogate(String value, int index) {
        char ch = value.charAt(index);
        if (Character.isHighSurrogate(ch)) {
            return index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1));
        }
        return index > 0 && Character.isHighSurrogate(value.charAt(index - 1));
    }
}
