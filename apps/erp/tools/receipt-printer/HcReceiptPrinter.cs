using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using Microsoft.Win32;

namespace HcReceipt
{
    /// <summary>
    /// 幻彩 ERP 本地小票打印小助手。
    ///
    /// 浏览器不能直接写 USB 打印机，因此在打印电脑上常驻一个极小的本地服务：
    /// 监听 127.0.0.1:18666，网页点「打印」时把订单数据发过来，由本程序直写
    /// ESC/POS 指令到 58mm 热敏小票机（不经 Windows 打印队列，不会乱码/卡队列）。
    /// </summary>
    internal static class Program
    {
        private const string Version = "1.0.0";
        private const int Port = 18666;

        /// <summary>订单小票与后端 ThermalPrinterService 使用同一套排版规则。</summary>
        private const int LineWidth = 32;

        private static readonly object PrintLock = new object();
        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();
        private static readonly Encoding Gbk = Encoding.GetEncoding(936);

        private static string _dataDir;
        private static string _logPath;
        private static string _deviceOverride;

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);

        private static void Main(string[] args)
        {
            _dataDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "HcReceiptPrinter");
            try { Directory.CreateDirectory(_dataDir); } catch { }
            _logPath = Path.Combine(_dataDir, "printer.log");
            _deviceOverride = ReadDeviceOverride();

            string mode = args.Length > 0 ? args[0].ToLowerInvariant() : string.Empty;

            if (mode == "--uninstall")
            {
                SetAutoStart(false);
                Log("uninstall");
                return;
            }

            if (mode == "--run")
            {
                SetAutoStart(true);
                RunServer();
                return;
            }

            // 从下载目录双击运行时，先把自己复制到 LocalAppData，再从那里启动，
            // 这样用户删除下载文件也不影响后续使用。
            bool relocated = RelocateAndRestart();
            if (relocated) return;

            SetAutoStart(true);
            MessageBoxW(IntPtr.Zero,
                "打印小助手已启动，网页上点「打印」会直接出纸。\n\n"
                + "程序已设置为开机自动启动，下次开机会自动运行，无需再次双击。\n"
                + "如需卸载：运行 HcReceiptPrinter.exe --uninstall",
                "幻彩打印小助手", 0x40);
            RunServer();
        }

        /* ---------------- 安装 / 自启动 ---------------- */

        private static bool RelocateAndRestart()
        {
            string exe;
            try { exe = Process.GetCurrentProcess().MainModule.FileName; }
            catch { return false; }

            string target = Path.Combine(_dataDir, "HcReceiptPrinter.exe");
            if (string.Equals(exe, target, StringComparison.OrdinalIgnoreCase)) return false;

            try { File.Copy(exe, target, true); }
            catch (Exception ex)
            {
                Log("copy self failed: " + ex.Message);
                if (!File.Exists(target)) return false;
            }

            try { Process.Start(target, "--run"); }
            catch (Exception ex) { Log("restart failed: " + ex.Message); }
            return true;
        }

        private static void SetAutoStart(bool enable)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(
                    @"Software\Microsoft\Windows\CurrentVersion\Run", true))
                {
                    if (key == null) return;
                    if (enable)
                    {
                        string exe = Path.Combine(_dataDir, "HcReceiptPrinter.exe");
                        if (!File.Exists(exe))
                        {
                            exe = Process.GetCurrentProcess().MainModule.FileName;
                        }
                        key.SetValue("HcReceiptPrinter", "\"" + exe + "\" --run");
                    }
                    else
                    {
                        key.DeleteValue("HcReceiptPrinter", false);
                    }
                }
            }
            catch (Exception ex) { Log("autostart failed: " + ex.Message); }
        }

        /* ---------------- HTTP 服务 ---------------- */

        private static void RunServer()
        {
            bool first;
            using (Mutex mutex = new Mutex(true, "HcReceiptPrinter_Single", out first))
            {
                if (!first) { Log("already running"); return; }

                TcpListener listener;
                try
                {
                    listener = new TcpListener(IPAddress.Loopback, Port);
                    listener.Start();
                }
                catch (Exception ex)
                {
                    Log("listen failed: " + ex.Message);
                    return;
                }

                Log("listening on 127.0.0.1:" + Port);
                while (true)
                {
                    TcpClient client;
                    try { client = listener.AcceptTcpClient(); }
                    catch (Exception ex) { Log("accept failed: " + ex.Message); continue; }

                    ThreadPool.QueueUserWorkItem(delegate(object state) { Handle((TcpClient)state); }, client);
                }
            }
        }

        private static void Handle(TcpClient client)
        {
            try
            {
                using (client)
                {
                    client.ReceiveTimeout = 15000;
                    client.SendTimeout = 15000;
                    NetworkStream stream = client.GetStream();

                    byte[] rest;
                    byte[] head = ReadRequestHead(stream, out rest);
                    if (head == null) return;

                    string text = Encoding.ASCII.GetString(head);
                    string[] lines = text.Split(new string[] { "\r\n" }, StringSplitOptions.None);
                    string requestLine = lines.Length > 0 ? lines[0] : string.Empty;
                    string[] parts = requestLine.Split(' ');
                    string method = parts.Length > 0 ? parts[0].ToUpperInvariant() : "GET";
                    string path = parts.Length > 1 ? parts[1] : "/";
                    int query = path.IndexOf('?');
                    if (query >= 0) path = path.Substring(0, query);
                    path = path.TrimEnd('/').ToLowerInvariant();
                    if (path.Length == 0) path = "/";

                    int contentLength = 0;
                    for (int i = 1; i < lines.Length; i++)
                    {
                        int colon = lines[i].IndexOf(':');
                        if (colon <= 0) continue;
                        if (string.Equals(lines[i].Substring(0, colon).Trim(), "Content-Length",
                                StringComparison.OrdinalIgnoreCase))
                        {
                            int.TryParse(lines[i].Substring(colon + 1).Trim(), out contentLength);
                        }
                    }

                    MemoryStream body = new MemoryStream();
                    if (rest != null && rest.Length > 0) body.Write(rest, 0, rest.Length);
                    while (contentLength > 0 && body.Length < contentLength)
                    {
                        byte[] buffer = new byte[4096];
                        int read = stream.Read(buffer, 0, buffer.Length);
                        if (read <= 0) break;
                        body.Write(buffer, 0, read);
                    }

                    string bodyText = Encoding.UTF8.GetString(body.ToArray());

                    if (method == "OPTIONS") { Respond(stream, 204, string.Empty); return; }

                    switch (path)
                    {
                        case "/health":
                        case "/status":
                            Respond(stream, 200, Json.Serialize(HealthPayload()));
                            return;
                        case "/print/test":
                            Respond(stream, 200, Json.Serialize(PrintTest()));
                            return;
                        case "/print/receipt":
                            Respond(stream, 200, Json.Serialize(PrintReceipt(bodyText)));
                            return;
                        case "/print/preview":
                            Respond(stream, 200, Json.Serialize(PreviewReceipt(bodyText)));
                            return;
                        case "/print/ticket":
                            Respond(stream, 200, Json.Serialize(PrintTicket(bodyText)));
                            return;
                        default:
                            Respond(stream, 404, Json.Serialize(Fail("未知接口 " + path)));
                            return;
                    }
                }
            }
            catch (Exception ex)
            {
                Log("handle failed: " + ex.Message);
            }
        }

        /// <summary>读取到空行为止的请求头，返回头部字节与已多读的正文。</summary>
        private static byte[] ReadRequestHead(NetworkStream stream, out byte[] rest)
        {
            rest = null;
            MemoryStream buffer = new MemoryStream();
            byte[] chunk = new byte[8192];
            while (buffer.Length < 1024 * 1024)
            {
                int read = stream.Read(chunk, 0, chunk.Length);
                if (read <= 0) break;
                buffer.Write(chunk, 0, read);

                byte[] all = buffer.ToArray();
                for (int i = 3; i < all.Length; i++)
                {
                    if (all[i] == (byte)'\n' && all[i - 1] == (byte)'\r'
                        && all[i - 2] == (byte)'\n' && all[i - 3] == (byte)'\r')
                    {
                        byte[] head = new byte[i + 1];
                        Array.Copy(all, 0, head, 0, i + 1);
                        if (all.Length > i + 1)
                        {
                            rest = new byte[all.Length - i - 1];
                            Array.Copy(all, i + 1, rest, 0, rest.Length);
                        }
                        return head;
                    }
                }
            }
            return null;
        }

        private static void Respond(NetworkStream stream, int status, string json)
        {
            byte[] payload = Encoding.UTF8.GetBytes(json ?? string.Empty);
            string reason = status == 200 ? "OK" : status == 204 ? "No Content" : "Not Found";
            StringBuilder head = new StringBuilder();
            head.Append("HTTP/1.1 ").Append(status).Append(' ').Append(reason).Append("\r\n");
            head.Append("Content-Type: application/json; charset=utf-8\r\n");
            head.Append("Content-Length: ").Append(status == 204 ? 0 : payload.Length).Append("\r\n");
            head.Append("Access-Control-Allow-Origin: *\r\n");
            head.Append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
            head.Append("Access-Control-Allow-Headers: Content-Type\r\n");
            head.Append("Access-Control-Allow-Private-Network: true\r\n");
            head.Append("Access-Control-Max-Age: 86400\r\n");
            head.Append("Cache-Control: no-store\r\n");
            head.Append("Connection: close\r\n\r\n");

            byte[] headBytes = Encoding.ASCII.GetBytes(head.ToString());
            stream.Write(headBytes, 0, headBytes.Length);
            if (status != 204) stream.Write(payload, 0, payload.Length);
            stream.Flush();
        }

        /* ---------------- 接口实现 ---------------- */

        private static Dictionary<string, object> HealthPayload()
        {
            string device;
            string error;
            bool ok = TryFindDevice(out device, out error);

            Dictionary<string, object> data = new Dictionary<string, object>();
            data["ok"] = true;
            data["version"] = Version;
            data["port"] = Port;
            data["deviceAvailable"] = ok;
            data["device"] = device ?? string.Empty;
            if (!ok) data["deviceMessage"] = error;
            return data;
        }

        private static Dictionary<string, object> PrintTest()
        {
            lock (PrintLock)
            {
                return Print(TestSlip());
            }
        }

        private static Dictionary<string, object> PrintReceipt(string body)
        {
            Dictionary<string, object> order = ParseObject(body);
            if (order == null) return Fail("订单数据格式不正确");
            if (order.ContainsKey("order") && order["order"] is Dictionary<string, object>)
            {
                order = (Dictionary<string, object>)order["order"];
            }

            lock (PrintLock)
            {
                return Print(Receipt(order));
            }
        }

        /// <summary>只渲染不打印，返回字节内容（Base64），用于核对排版与中文字节。</summary>
        private static Dictionary<string, object> PreviewReceipt(string body)
        {
            Dictionary<string, object> order = ParseObject(body) ?? new Dictionary<string, object>();
            if (order.ContainsKey("order") && order["order"] is Dictionary<string, object>)
            {
                order = (Dictionary<string, object>)order["order"];
            }

            byte[] data = Receipt(order);
            Dictionary<string, object> result = new Dictionary<string, object>();
            result["ok"] = true;
            result["bytes"] = data.Length;
            result["base64"] = Convert.ToBase64String(data);
            return result;
        }

        private static Dictionary<string, object> PrintTicket(string body)
        {
            Dictionary<string, object> data = ParseObject(body);
            if (data == null) return Fail("听课证数据格式不正确");

            lock (PrintLock)
            {
                return Print(Ticket(data));
            }
        }

        private static Dictionary<string, object> ParseObject(string body)
        {
            if (string.IsNullOrWhiteSpace(body)) return null;
            try
            {
                return Json.DeserializeObject(body) as Dictionary<string, object>;
            }
            catch (Exception ex)
            {
                Log("json parse failed: " + ex.Message);
                return null;
            }
        }

        private static Dictionary<string, object> Print(byte[] data)
        {
            string device;
            string error;
            if (!TryFindDevice(out device, out error))
            {
                return Fail(error ?? "未检测到小票打印机，请确认 USB 已连接并已开机");
            }

            try
            {
                WriteRaw(device, data);
            }
            catch (Exception ex)
            {
                Log("print failed: " + ex.Message);
                return Fail(ex.Message);
            }

            Dictionary<string, object> result = new Dictionary<string, object>();
            result["ok"] = true;
            result["bytes"] = data.Length;
            result["device"] = device;
            Log("printed " + data.Length + " bytes to " + device);
            return result;
        }

        private static Dictionary<string, object> Fail(string message)
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            result["ok"] = false;
            result["message"] = message;
            return result;
        }

        /* ---------------- USB 打印设备 ---------------- */

        [StructLayout(LayoutKind.Sequential)]
        private struct SpDeviceInterfaceData
        {
            public int Size;
            public Guid InterfaceClassGuid;
            public int Flags;
            public IntPtr Reserved;
        }

        [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr SetupDiGetClassDevs(ref Guid classGuid, IntPtr enumerator,
            IntPtr hwndParent, uint flags);

        [DllImport("setupapi.dll", SetLastError = true)]
        private static extern bool SetupDiEnumDeviceInterfaces(IntPtr deviceInfoSet, IntPtr deviceInfoData,
            ref Guid interfaceClassGuid, uint memberIndex, ref SpDeviceInterfaceData deviceInterfaceData);

        [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr deviceInfoSet,
            ref SpDeviceInterfaceData deviceInterfaceData, IntPtr deviceInterfaceDetailData,
            uint deviceInterfaceDetailDataSize, out uint requiredSize, IntPtr deviceInfoData);

        [DllImport("setupapi.dll", SetLastError = true)]
        private static extern bool SetupDiDestroyDeviceInfoList(IntPtr deviceInfoSet);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateFileW(string name, uint access, uint share, IntPtr security,
            uint disposition, uint flags, IntPtr template);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool WriteFile(IntPtr handle, byte[] buffer, uint count, out uint written,
            IntPtr overlapped);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);

        private static List<string> EnumerateUsbPrinterInterfaces()
        {
            List<string> paths = new List<string>();
            Guid guid = new Guid("28d78fad-5a12-11d1-ae5b-0000f803a8c2");

            IntPtr set = SetupDiGetClassDevs(ref guid, IntPtr.Zero, IntPtr.Zero, 0x02 | 0x10);
            if (set == new IntPtr(-1)) return paths;

            try
            {
                SpDeviceInterfaceData data = new SpDeviceInterfaceData();
                data.Size = Marshal.SizeOf(typeof(SpDeviceInterfaceData));

                for (uint index = 0; ; index++)
                {
                    if (!SetupDiEnumDeviceInterfaces(set, IntPtr.Zero, ref guid, index, ref data)) break;

                    uint required;
                    SetupDiGetDeviceInterfaceDetail(set, ref data, IntPtr.Zero, 0, out required, IntPtr.Zero);
                    if (required == 0) continue;

                    IntPtr buffer = Marshal.AllocHGlobal((int)required);
                    try
                    {
                        Marshal.WriteInt32(buffer, IntPtr.Size == 8 ? 8 : 6);
                        uint ignored;
                        if (SetupDiGetDeviceInterfaceDetail(set, ref data, buffer, required, out ignored,
                                IntPtr.Zero))
                        {
                            string path = Marshal.PtrToStringUni(new IntPtr(buffer.ToInt64() + 4));
                            if (!string.IsNullOrEmpty(path)) paths.Add(path);
                        }
                    }
                    finally
                    {
                        Marshal.FreeHGlobal(buffer);
                    }
                }
            }
            finally
            {
                SetupDiDestroyDeviceInfoList(set);
            }

            return paths;
        }

        private static bool TryFindDevice(out string device, out string error)
        {
            device = null;
            error = null;

            List<string> candidates = new List<string>();
            if (!string.IsNullOrEmpty(_deviceOverride)) candidates.Add(_deviceOverride);
            candidates.AddRange(EnumerateUsbPrinterInterfaces());

            if (candidates.Count == 0)
            {
                error = "未检测到 USB 小票打印机，请确认打印机已开机并插在打印电脑上";
                return false;
            }

            foreach (string path in candidates)
            {
                IntPtr handle = OpenDevice(path);
                if (handle == IntPtr.Zero || handle.ToInt64() == -1) continue;
                CloseHandle(handle);
                device = path;
                return true;
            }

            error = "小票打印机已连接但无法打开（可能被其他程序占用），请重新插拔 USB 后重试";
            return false;
        }

        private static IntPtr OpenDevice(string path)
        {
            return CreateFileW(path, 0x40000000, 0x00000003, IntPtr.Zero, 3, 0x80, IntPtr.Zero);
        }

        private static void WriteRaw(string path, byte[] data)
        {
            IntPtr handle = OpenDevice(path);
            if (handle == IntPtr.Zero || handle.ToInt64() == -1)
            {
                int code = Marshal.GetLastWin32Error();
                throw new InvalidOperationException("无法打开小票打印机（错误码 " + code + "），请确认已开机并重插 USB");
            }

            try
            {
                int offset = 0;
                while (offset < data.Length)
                {
                    int size = Math.Min(data.Length - offset, 1024);
                    byte[] chunk = new byte[size];
                    Array.Copy(data, offset, chunk, 0, size);

                    uint written;
                    if (!WriteFile(handle, chunk, (uint)size, out written, IntPtr.Zero))
                    {
                        int code = Marshal.GetLastWin32Error();
                        throw new InvalidOperationException("写入小票打印机失败（错误码 " + code + "），请检查纸卷与电源");
                    }
                    if (written == 0)
                    {
                        throw new InvalidOperationException("小票打印机未接收数据，请检查是否缺纸或离线");
                    }
                    offset += size;
                }
            }
            finally
            {
                CloseHandle(handle);
            }
        }

        private static string ReadDeviceOverride()
        {
            try
            {
                string file = Path.Combine(_dataDir, "device.txt");
                if (!File.Exists(file)) return null;
                string text = File.ReadAllText(file).Trim();
                return text.Length > 0 && !text.StartsWith("#", StringComparison.Ordinal) ? text : null;
            }
            catch { return null; }
        }

        /* ---------------- ESC/POS 排版 ---------------- */

        private static byte[] TestSlip()
        {
            MemoryStream ms = new MemoryStream();
            Cmd(ms, 0x1B, 0x40);
            Cmd(ms, 0x1B, 0x61, 0x01);
            Cmd(ms, 0x1B, 0x21, 0x10);
            Cmd(ms, 0x1B, 0x45, 0x01);
            Line(ms, "幻彩教育");
            Cmd(ms, 0x1B, 0x45, 0x00);
            Cmd(ms, 0x1B, 0x21, 0x00);
            Line(ms, "小 票 机 测 试");
            Line(ms, Divider());
            Cmd(ms, 0x1B, 0x61, 0x00);
            Line(ms, "打印机：58mm 热敏小票机");
            Line(ms, "连接方式：USB 直发 ESC/POS");
            Line(ms, "时间：" + Now());
            Line(ms, Divider());
            Cmd(ms, 0x1B, 0x61, 0x01);
            Line(ms, "打印正常，中文显示正常");
            Feed(ms, 5);
            return ms.ToArray();
        }

        private static byte[] Receipt(Dictionary<string, object> o)
        {
            MemoryStream ms = new MemoryStream();
            bool isReturn = ToBool(o, "isReturn");
            string shop = Blank(Str(o, "campusName"), "幻彩教育");

            Cmd(ms, 0x1B, 0x40);
            Cmd(ms, 0x1B, 0x61, 0x01);
            Cmd(ms, 0x1B, 0x21, 0x10);
            Cmd(ms, 0x1B, 0x45, 0x01);
            Line(ms, shop);
            Cmd(ms, 0x1B, 0x45, 0x00);
            Cmd(ms, 0x1B, 0x21, 0x00);
            Line(ms, "收 费 小 票");
            Line(ms, "单号 " + Blank(Str(o, "orderNo"), "-"));
            Cmd(ms, 0x1B, 0x61, 0x00);
            Line(ms, Divider());

            Line(ms, Pair("学员", Blank(Str(o, "stuName"), "-")));
            Line(ms, Pair("电话", Blank(Str(o, "stuTel"), "-")));
            Line(ms, Pair("类型", Blank(Str(o, "typeName"), "-")));
            Line(ms, Pair("状态", Blank(Str(o, "statusName"), "-")));
            Line(ms, Divider());

            Wrap(ms, Blank(Str(o, "content"), "-"));
            Line(ms, Divider());

            Line(ms, Pair("应收金额", Money(ToDec(o, "shouldPay"))));
            Cmd(ms, 0x1B, 0x45, 0x01);
            Line(ms, Pair(isReturn ? "实退金额" : "实收金额", Money(ToDec(o, "realPay"))));
            Cmd(ms, 0x1B, 0x45, 0x00);
            Line(ms, Divider());

            Line(ms, Pair("账户变动", SignedMoney(ToDec(o, "remain"))));
            Line(ms, Pair("欠费", PlainMoney(ToDec(o, "arrearage"))));
            Line(ms, Pair("积分变动", SignedNumber(ToDec(o, "credit"))));
            Line(ms, Divider());

            Line(ms, Pair("经办人", Blank(Str(o, "creator"), "-")));
            Line(ms, Pair("支付方式", Blank(Str(o, "payMethod"), "-")));
            Line(ms, Pair("经办日期", DealDate(o)));

            string remark = Str(o, "commentOuter");
            if (!string.IsNullOrWhiteSpace(remark))
            {
                Line(ms, Divider());
                Wrap(ms, "备注：" + remark.Trim());
            }
            Line(ms, Divider());

            Cmd(ms, 0x1B, 0x61, 0x01);
            Line(ms, "谢谢惠顾，欢迎再次光临");
            Line(ms, "打印时间 " + Now());
            Line(ms, "经办人签字：____________");
            Feed(ms, 5);
            return ms.ToArray();
        }

        private static byte[] Ticket(Dictionary<string, object> o)
        {
            MemoryStream ms = new MemoryStream();
            Cmd(ms, 0x1B, 0x40);
            Cmd(ms, 0x1B, 0x61, 0x01);
            Cmd(ms, 0x1B, 0x21, 0x10);
            Cmd(ms, 0x1B, 0x45, 0x01);
            Line(ms, Blank(Str(o, "campus"), "幻彩教育"));
            Cmd(ms, 0x1B, 0x45, 0x00);
            Cmd(ms, 0x1B, 0x21, 0x00);
            Line(ms, "听 课 证");
            Line(ms, "学员 " + Blank(Str(o, "studentName"), "-"));
            Cmd(ms, 0x1B, 0x61, 0x00);
            Line(ms, Divider());

            Line(ms, Pair("电话", Blank(Str(o, "phone"), "-")));
            Line(ms, Divider());
            Wrap(ms, "课程 " + Blank(Str(o, "courseName"), "-"));
            Wrap(ms, "班级 " + Blank(Str(o, "className"), "未分班"));
            Wrap(ms, "教师 " + Blank(Str(o, "teacher"), "-"));
            Line(ms, Pair("剩余课时", LessonText(ToDec(o, "remainLessons"))));
            Line(ms, Pair("有效期至", Blank(Str(o, "expireDate"), "未设置")));
            Line(ms, Divider());

            Cmd(ms, 0x1B, 0x61, 0x01);
            Line(ms, "上课时请出示此证");
            Line(ms, "打印时间 " + Now());
            Feed(ms, 5);
            return ms.ToArray();
        }

        private static void Cmd(Stream stream, params byte[] data)
        {
            stream.Write(data, 0, data.Length);
        }

        private static void Line(Stream stream, string text)
        {
            byte[] bytes = Gbk.GetBytes(text);
            stream.Write(bytes, 0, bytes.Length);
            stream.WriteByte((byte)'\n');
        }

        private static void Feed(Stream stream, byte lines)
        {
            Cmd(stream, 0x1B, 0x64, lines);
        }

        private static void Wrap(Stream stream, string text)
        {
            string normalized = (text ?? string.Empty).Replace("\r", string.Empty).Replace("\t", " ");
            string[] paragraphs = normalized.Split('\n');
            foreach (string paragraph in paragraphs)
            {
                if (paragraph.Length == 0)
                {
                    Line(stream, string.Empty);
                    continue;
                }

                StringBuilder current = new StringBuilder();
                int width = 0;
                for (int i = 0; i < paragraph.Length; i++)
                {
                    char c = paragraph[i];
                    int charWidth = c < 128 ? 1 : 2;
                    if (width + charWidth > LineWidth)
                    {
                        Line(stream, current.ToString());
                        current.Length = 0;
                        width = 0;
                    }
                    current.Append(c);
                    width += charWidth;
                }
                if (current.Length > 0) Line(stream, current.ToString());
            }
        }

        private static string Pair(string label, string value)
        {
            string right = Truncate(value, LineWidth / 2);
            int padding = LineWidth - Width(label) - Width(right);
            if (padding < 1) padding = 1;
            return label + new string(' ', padding) + right;
        }

        private static string Divider()
        {
            return new string('-', LineWidth);
        }

        private static int Width(string text)
        {
            int width = 0;
            foreach (char c in text ?? string.Empty) width += c < 128 ? 1 : 2;
            return width;
        }

        private static string Truncate(string text, int maxWidth)
        {
            StringBuilder builder = new StringBuilder();
            int width = 0;
            foreach (char c in text ?? string.Empty)
            {
                int charWidth = c < 128 ? 1 : 2;
                if (width + charWidth > maxWidth) break;
                builder.Append(c);
                width += charWidth;
            }
            return builder.ToString();
        }

        private static string LessonText(decimal lessons)
        {
            return Number(lessons) + " 课时";
        }

        private static string Money(decimal value)
        {
            return "￥" + Math.Abs(value).ToString("0.00", CultureInfo.InvariantCulture);
        }

        private static string PlainMoney(decimal value)
        {
            return Math.Abs(value).ToString("0.00", CultureInfo.InvariantCulture);
        }

        private static string SignedMoney(decimal value)
        {
            string sign = value > 0 ? "+" : string.Empty;
            return sign + value.ToString("0.00", CultureInfo.InvariantCulture);
        }

        private static string SignedNumber(decimal value)
        {
            string sign = value > 0 ? "+" : string.Empty;
            return sign + Number(value);
        }

        private static string Number(decimal value)
        {
            return value.ToString("0.####", CultureInfo.InvariantCulture);
        }

        private static string Now()
        {
            return DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture);
        }

        /* ---------------- 取值工具 ---------------- */

        private static string Str(Dictionary<string, object> data, string key)
        {
            object value;
            if (!data.TryGetValue(key, out value) || value == null) return null;
            return Convert.ToString(value, CultureInfo.InvariantCulture);
        }

        private static decimal ToDec(Dictionary<string, object> data, string key)
        {
            object value;
            if (!data.TryGetValue(key, out value) || value == null) return 0m;
            if (value is decimal) return (decimal)value;
            if (value is int) return (int)value;
            if (value is long) return (long)value;
            if (value is double) return (decimal)(double)value;
            if (value is bool) return (bool)value ? 1m : 0m;

            decimal parsed;
            if (decimal.TryParse(Convert.ToString(value, CultureInfo.InvariantCulture),
                    NumberStyles.Any, CultureInfo.InvariantCulture, out parsed))
            {
                return parsed;
            }
            return 0m;
        }

        private static bool ToBool(Dictionary<string, object> data, string key)
        {
            object value;
            if (!data.TryGetValue(key, out value) || value == null) return false;
            if (value is bool) return (bool)value;
            string text = Convert.ToString(value, CultureInfo.InvariantCulture);
            return string.Equals(text, "true", StringComparison.OrdinalIgnoreCase) || text == "1";
        }

        private static string Blank(string value, string fallback)
        {
            return string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();
        }

        private static string DealDate(Dictionary<string, object> data)
        {
            string value = Str(data, "dealDate");
            if (string.IsNullOrWhiteSpace(value)) return "-";
            value = value.Trim();
            return value.Length >= 10 && value[4] == '-' ? value.Substring(0, 10) : value;
        }

        /* ---------------- 日志 ---------------- */

        private static void Log(string message)
        {
            try
            {
                FileInfo info = new FileInfo(_logPath);
                if (info.Exists && info.Length > 1024 * 1024)
                {
                    string old = Path.Combine(_dataDir, "printer.old.log");
                    if (File.Exists(old)) File.Delete(old);
                    File.Move(_logPath, old);
                }
                File.AppendAllText(_logPath,
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture)
                    + " " + message + Environment.NewLine, Encoding.UTF8);
            }
            catch { }
        }
    }
}
