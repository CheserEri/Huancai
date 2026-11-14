/// 全局配置：后端地址固定写死，上线服务器后修改此处并重新打包发版。
class AppConfig {
  /// 后端服务固定地址（当前指向局域网内运行的后端）。
  /// 例：真机访问时须保证手机与电脑处于同一局域网。
  static const String baseHost = 'http://192.168.0.107:8080';

  static const String apiBaseUrl = '$baseHost/api';

  static const String appName = '幻彩学堂';

  /// 把后端返回的相对路径（如 /uploads/xxx.jpg）补全为可访问的绝对地址。
  static String fileUrl(String path) {
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    return '$baseHost$path';
  }
}
