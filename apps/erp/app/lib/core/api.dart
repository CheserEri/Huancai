import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'config.dart';

/// 业务异常：message 直接用于界面提示。
class ApiException implements Exception {
  ApiException(this.message, [this.statusCode = 0]);

  final String message;
  final int statusCode;

  @override
  String toString() => message;
}

/// 统一的后端接口访问入口，自动携带登录 token 并解包 { code, message, data }。
class Api {
  static final http.Client _client = http.Client();

  static String? token;

  /// 登录态失效时的回调（由 AppState 注入，用于清理本地登录信息）。
  static Future<void> Function()? onUnauthorized;

  static Future<T> get<T>(String path, [Map<String, dynamic>? query]) =>
      _send<T>('GET', path, query: query);

  static Future<T> post<T>(String path, [Object? body]) => _send<T>('POST', path, body: body);

  static Future<T> put<T>(String path, [Object? body]) => _send<T>('PUT', path, body: body);

  static Future<T> delete<T>(String path) => _send<T>('DELETE', path);

  /// 表单文件上传（multipart），用于班级相册等图片上传场景。
  ///
  /// 注意：不要手动设置 Content-Type，boundary 由 [http.MultipartRequest] 自动生成。
  static Future<T> upload<T>(
    String path, {
    required String filePath,
    String field = 'file',
    Map<String, String>? fields,
  }) async {
    final uri = Uri.parse('${AppConfig.apiBaseUrl}$path');
    final request = http.MultipartRequest('POST', uri);
    if (token != null && token!.isNotEmpty) {
      request.headers['Authorization'] = 'Bearer $token';
    }
    if (fields != null) request.fields.addAll(fields);
    request.files.add(await http.MultipartFile.fromPath(field, filePath));

    http.Response response;
    try {
      final streamed = await _client.send(request).timeout(const Duration(seconds: 90));
      response = await http.Response.fromStream(streamed);
    } on TimeoutException {
      throw ApiException('上传超时，请检查网络后重试');
    } catch (_) {
      throw ApiException('无法连接服务器，请检查网络与服务器地址');
    }
    return _handle<T>(response);
  }

  static Future<T> _send<T>(
    String method,
    String path, {
    Map<String, dynamic>? query,
    Object? body,
  }) async {
    final params = <String, String>{};
    query?.forEach((key, value) {
      if (value == null) return;
      final text = value is List ? value.join(',') : '$value';
      if (text.isEmpty) return;
      params[key] = text;
    });

    final uri = Uri.parse('${AppConfig.apiBaseUrl}$path')
        .replace(queryParameters: params.isEmpty ? null : params);

    final request = http.Request(method, uri);
    request.headers['Content-Type'] = 'application/json';
    if (token != null && token!.isNotEmpty) {
      request.headers['Authorization'] = 'Bearer $token';
    }
    if (body != null) request.body = jsonEncode(body);

    http.Response response;
    try {
      final streamed = await _client.send(request).timeout(const Duration(seconds: 30));
      response = await http.Response.fromStream(streamed);
    } on TimeoutException {
      throw ApiException('请求超时，请检查网络或后端服务是否正常');
    } catch (_) {
      throw ApiException('无法连接服务器，请检查网络与服务器地址');
    }

    return _handle<T>(response);
  }

  /// 统一的响应校验与解包，JSON 请求与文件上传共用。
  static Future<T> _handle<T>(http.Response response) async {
    if (response.statusCode == 401) {
      await onUnauthorized?.call();
      throw ApiException('登录已失效，请重新登录', 401);
    }
    if (response.statusCode == 403) {
      throw ApiException(_messageOf(response) ?? '当前账号无权访问该功能，请联系管理员分配菜单权限', 403);
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(_messageOf(response) ?? '请求失败（HTTP ${response.statusCode}）', response.statusCode);
    }

    dynamic bodyJson;
    try {
      bodyJson = jsonDecode(utf8.decode(response.bodyBytes));
    } catch (_) {
      throw ApiException('服务器返回数据格式异常');
    }
    if (bodyJson is! Map) throw ApiException('服务器返回数据格式异常');

    final code = bodyJson['code'];
    if (code is num && code != 0) {
      throw ApiException('${bodyJson['message'] ?? '请求失败'}');
    }
    final data = bodyJson['data'];
    if (data == null) {
      if (T == dynamic || T == Null) return null as T;
      return data as T;
    }
    if (T == dynamic) return data as T;
    return data as T;
  }

  static String? _messageOf(http.Response response) {
    try {
      final json = jsonDecode(utf8.decode(response.bodyBytes));
      if (json is Map && json['message'] != null) return '${json['message']}';
    } catch (_) {
      // 忽略解析失败
    }
    return null;
  }
}
