/// 全局配置：后端地址固定写死，上线服务器后修改此处并重新打包发版。
class AppConfig {
  /// 后端服务固定地址（当前指向腾讯云线上服务器，经 Nginx 80 端口反代到后端）。
  /// 需要重新部署到其他服务器时，修改此处并重新打包。
  static const String baseHost = 'http://43.143.100.118';

  static const String apiBaseUrl = '$baseHost/api';

  static const String appName = '幻彩学堂';

  /// 把后端返回的相对路径（如 /uploads/xxx.jpg）补全为可访问的绝对地址。
  static String fileUrl(String path) {
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    return '$baseHost$path';
  }
}
