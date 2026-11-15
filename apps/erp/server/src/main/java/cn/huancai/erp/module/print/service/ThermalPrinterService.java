package cn.huancai.erp.module.print.service;

import cn.huancai.erp.module.order.vo.SpOrderVO;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 58mm 热敏小票直发。
 *
 * <p>本机 H58 小票机没有厂商驱动，Windows 打印队列（spooler RAW）会被 USB 端口拒绝，
 * 因此直接通过 kernel32 打开 USB 打印设备接口，写入 ESC/POS 指令。</p>
 */
@Service
public class ThermalPrinterService {

    /** 单行半角字符数（58mm 纸、字体 A）。 */
    private static final int LINE_WIDTH = 32;
    private static final Charset GBK = Charset.forName("GBK");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int GENERIC_WRITE = 0x40000000;
    private static final int FILE_SHARE_READ = 0x00000001;
    private static final int FILE_SHARE_WRITE = 0x00000002;
    private static final int OPEN_EXISTING = 3;
    private static final int FILE_ATTRIBUTE_NORMAL = 0x80;

    /** ESC/POS 指令。 */
    private static final int ESC = 0x1B;
    /** 初始化打印机。 */
    private static final int[] CMD_INIT = {ESC, 0x40};
    /** 对齐：0 左、1 居中、2 右。 */
    private static final int[] CMD_ALIGN_LEFT = {ESC, 0x61, 0x00};
    private static final int[] CMD_ALIGN_CENTER = {ESC, 0x61, 0x01};
    /** 字体大小：0x00 常规，0x10 倍高。 */
    private static final int[] CMD_FONT_NORMAL = {ESC, 0x21, 0x00};
    private static final int[] CMD_FONT_DOUBLE_HEIGHT = {ESC, 0x21, 0x10};
    /** 加粗：1 开、0 关。 */
    private static final int[] CMD_BOLD_ON = {ESC, 0x45, 0x01};
    private static final int[] CMD_BOLD_OFF = {ESC, 0x45, 0x00};

    /** kernel32 缓存，只在 Windows 上加载。 */
    private static volatile Kernel32 KERNEL32;

    private final String device;

    /**
     * 设备路径里含 {@code #{...}} 形式的接口 GUID，用 @Value 会被 Spring 当成 SpEL 表达式解析，
     * 因此这里直接从 Environment 读取原始配置。
     */
    public ThermalPrinterService(Environment environment) {
        String configured = environment.getProperty("printer.receipt-device", "");
        this.device = configured.trim();
    }

    /** 打印订单小票，返回本次写入的设备与字节数。 */
    public Map<String, Object> printReceipt(SpOrderVO order) {
        byte[] data = renderReceipt(order);
        write(data);
        return result(data);
    }

    /** 打印一张自检小票，便于确认走纸与中文字体。 */
    public Map<String, Object> printTest() {
        byte[] data = renderTest();
        write(data);
        return result(data);
    }

    /** 打印听课证小票。 */
    public Map<String, Object> printTicket(String campus, String studentName, String phone, String courseName,
                                           String className, String teacher, BigDecimal remainLessons,
                                           String expireDate) {
        byte[] data = renderTicket(campus, studentName, phone, courseName, className, teacher, remainLessons, expireDate);
        write(data);
        return result(data);
    }

    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", !device.isEmpty());
        data.put("device", device);
        data.put("lineWidth", LINE_WIDTH);
        return data;
    }

    private Map<String, Object> result(byte[] data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("device", device);
        result.put("bytes", data.length);
        return result;
    }

    private byte[] renderReceipt(SpOrderVO order) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String shop = blankToDefault(order.getCampusName(), "幻彩教育");
        boolean isReturn = Boolean.TRUE.equals(order.getIsReturn());

        cmd(out, CMD_INIT);
        cmd(out, CMD_ALIGN_CENTER);
        cmd(out, CMD_FONT_DOUBLE_HEIGHT);
        cmd(out, CMD_BOLD_ON);
        line(out, shop);
        cmd(out, CMD_BOLD_OFF);
        cmd(out, CMD_FONT_NORMAL);
        line(out, "收 费 小 票");
        line(out, "单号 " + blankToDefault(order.getOrderNo(), "-"));
        cmd(out, CMD_ALIGN_LEFT);
        line(out, divider());

        line(out, pair("学员", blankToDefault(order.getStuName(), "-")));
        line(out, pair("电话", blankToDefault(order.getStuTel(), "-")));
        line(out, pair("类型", blankToDefault(order.getTypeName(), "-")));
        line(out, pair("状态", blankToDefault(order.getStatusName(), "-")));
        line(out, divider());

        wrap(out, blankToDefault(order.getContent(), "-"));
        line(out, divider());

        line(out, pair("应收金额", money(order.getShouldPay())));
        cmd(out, CMD_BOLD_ON);
        line(out, pair(isReturn ? "实退金额" : "实收金额", money(order.getRealPay())));
        cmd(out, CMD_BOLD_OFF);
        line(out, divider());

        line(out, pair("账户变动", signedMoney(order.getRemain())));
        line(out, pair("欠费", plainMoney(order.getArrearage())));
        line(out, pair("积分变动", signedNumber(order.getCredit())));
        line(out, divider());

        line(out, pair("经办人", blankToDefault(order.getCreator(), "-")));
        line(out, pair("支付方式", blankToDefault(order.getPayMethod(), "-")));
        line(out, pair("经办日期", dealDate(order)));
        if (order.getCommentOuter() != null && !order.getCommentOuter().isBlank()) {
            line(out, divider());
            wrap(out, "备注：" + order.getCommentOuter().trim());
        }
        line(out, divider());

        cmd(out, CMD_ALIGN_CENTER);
        line(out, "谢谢惠顾，欢迎再次光临");
        line(out, "打印时间 " + LocalDateTime.now().format(TIME_FORMAT));
        line(out, "经办人签字：____________");

        feed(out, 5);
        return out.toByteArray();
    }

    private byte[] renderTicket(String campus, String studentName, String phone, String courseName,
                                String className, String teacher, BigDecimal remainLessons,
                                String expireDate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cmd(out, CMD_INIT);
        cmd(out, CMD_ALIGN_CENTER);
        cmd(out, CMD_FONT_DOUBLE_HEIGHT);
        cmd(out, CMD_BOLD_ON);
        line(out, blankToDefault(campus, "幻彩教育"));
        cmd(out, CMD_BOLD_OFF);
        cmd(out, CMD_FONT_NORMAL);
        line(out, "听 课 证");
        line(out, "学员 " + blankToDefault(studentName, "-"));
        cmd(out, CMD_ALIGN_LEFT);
        line(out, divider());

        line(out, pair("电话", blankToDefault(phone, "-")));
        line(out, divider());
        wrap(out, "课程 " + blankToDefault(courseName, "-"));
        wrap(out, "班级 " + blankToDefault(className, "未分班"));
        wrap(out, "教师 " + blankToDefault(teacher, "-"));
        line(out, pair("剩余课时", lessonText(remainLessons)));
        line(out, pair("有效期至", blankToDefault(expireDate, "未设置")));
        line(out, divider());

        cmd(out, CMD_ALIGN_CENTER);
        line(out, "上课时请出示此证");
        line(out, "打印时间 " + LocalDateTime.now().format(TIME_FORMAT));

        feed(out, 5);
        return out.toByteArray();
    }

    private String lessonText(BigDecimal lessons) {
        if (lessons == null) {
            return "0 课时";
        }
        return lessons.stripTrailingZeros().toPlainString() + " 课时";
    }

    private byte[] renderTest() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cmd(out, CMD_INIT);
        cmd(out, CMD_ALIGN_CENTER);
        cmd(out, CMD_FONT_DOUBLE_HEIGHT);
        cmd(out, CMD_BOLD_ON);
        line(out, "幻彩教育");
        cmd(out, CMD_BOLD_OFF);
        cmd(out, CMD_FONT_NORMAL);
        line(out, "小 票 机 测 试");
        line(out, divider());
        cmd(out, CMD_ALIGN_LEFT);
        line(out, "打印机：58mm 热敏小票机");
        line(out, "连接方式：USB 直发 ESC/POS");
        line(out, "时间：" + LocalDateTime.now().format(TIME_FORMAT));
        line(out, divider());
        cmd(out, CMD_ALIGN_CENTER);
        line(out, "打印正常，中文显示正常");
        feed(out, 5);
        return out.toByteArray();
    }

    /** 打开 USB 打印设备并写入原始字节。 */
    private void write(byte[] data) {
        if (device.isEmpty()) {
            throw new IllegalStateException("未配置小票打印机（printer.receipt-device）");
        }
        Kernel32 kernel32 = kernel32();
        if (kernel32 == null) {
            throw new IllegalStateException("小票打印依赖 Windows USB 设备，当前系统不支持");
        }
        Pointer handle = kernel32.CreateFileW(
                new WString(device),
                GENERIC_WRITE,
                FILE_SHARE_READ | FILE_SHARE_WRITE,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null);
        if (handle == null || Pointer.nativeValue(handle) == -1L) {
            throw new IllegalStateException("无法打开小票打印机设备，请确认 USB 已连接并已开机");
        }
        try {
            int offset = 0;
            IntByReference written = new IntByReference();
            while (offset < data.length) {
                int size = Math.min(data.length - offset, 1024);
                byte[] chunk = new byte[size];
                System.arraycopy(data, offset, chunk, 0, size);
                if (!kernel32.WriteFile(handle, chunk, size, written, null)) {
                    throw new IllegalStateException("写入小票打印机失败，请检查打印机状态");
                }
                if (written.getValue() <= 0) {
                    throw new IllegalStateException("小票打印机未接收数据，请检查是否缺纸或离线");
                }
                offset += size;
            }
        } finally {
            kernel32.CloseHandle(handle);
        }
    }

    private void cmd(ByteArrayOutputStream out, int[] command) {
        for (int value : command) {
            out.write(value);
        }
    }

    private void line(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(GBK));
        out.write('\n');
    }

    private void feed(ByteArrayOutputStream out, int lines) {
        cmd(out, new int[]{ESC, 0x64, lines});
    }

    /** 按半角列宽换行输出，避免长文本被截断。 */
    private void wrap(ByteArrayOutputStream out, String text) {
        String remaining = text.replace("\r", "").replace("\t", " ");
        for (String paragraph : remaining.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                line(out, "");
                continue;
            }
            StringBuilder current = new StringBuilder();
            int width = 0;
            for (int i = 0; i < paragraph.length(); ) {
                int codePoint = paragraph.codePointAt(i);
                int charWidth = codePoint < 128 ? 1 : 2;
                if (width + charWidth > LINE_WIDTH) {
                    line(out, current.toString());
                    current.setLength(0);
                    width = 0;
                }
                current.appendCodePoint(codePoint);
                width += charWidth;
                i += Character.charCount(codePoint);
            }
            if (current.length() > 0) {
                line(out, current.toString());
            }
        }
    }

    private String pair(String label, String value) {
        String right = truncate(value, LINE_WIDTH / 2);
        int padding = LINE_WIDTH - width(label) - width(right);
        if (padding < 1) {
            padding = 1;
        }
        return label + " ".repeat(padding) + right;
    }

    private String divider() {
        return "-".repeat(LINE_WIDTH);
    }

    private int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            width += codePoint < 128 ? 1 : 2;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    private String truncate(String text, int maxWidth) {
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charWidth = codePoint < 128 ? 1 : 2;
            if (width + charWidth > maxWidth) {
                break;
            }
            builder.appendCodePoint(codePoint);
            width += charWidth;
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private String money(BigDecimal value) {
        return "￥" + amount(value);
    }

    private String plainMoney(BigDecimal value) {
        return amount(value);
    }

    private String signedMoney(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return (amount.signum() > 0 ? "+" : "") + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String signedNumber(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        String text = amount.stripTrailingZeros().toPlainString();
        return (amount.signum() > 0 ? "+" : "") + text;
    }

    private String amount(BigDecimal value) {
        BigDecimal decimal = value == null ? BigDecimal.ZERO : value;
        return decimal.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String dealDate(SpOrderVO order) {
        LocalDateTime dealDate = order.getDealDate();
        return dealDate == null ? "-" : dealDate.toLocalDate().toString();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** kernel32 最小封装：只用于打开 USB 打印设备并写入原始数据。 */
    public interface Kernel32 extends Library {

        Pointer CreateFileW(WString fileName, int desiredAccess, int shareMode, Pointer securityAttributes,
                            int creationDisposition, int flagsAndAttributes, Pointer templateFile);

        boolean WriteFile(Pointer file, byte[] buffer, int bytesToWrite, IntByReference bytesWritten,
                          Pointer overlapped);

        boolean CloseHandle(Pointer handle);
    }

    /** kernel32 只在 Windows 上存在，非 Windows 返回 null，避免启动期加载本地库失败。 */
    private static Kernel32 kernel32() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null;
        }
        if (KERNEL32 == null) {
            synchronized (ThermalPrinterService.class) {
                if (KERNEL32 == null) {
                    KERNEL32 = Native.load("kernel32", Kernel32.class);
                }
            }
        }
        return KERNEL32;
    }
}
