import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api.dart';
import '../core/models.dart';

/// 全局登录态：token 持久化在本地，启动时拉取 /auth/me 刷新菜单权限。
class AppState extends ChangeNotifier {
  AppState() {
    Api.onUnauthorized = _clearLogin;
  }

  static const _tokenKey = 'hc_token';
  static const _userKey = 'hc_user';

  UserInfo? user;
  bool restoring = true;

  bool get loggedIn => user != null;

  bool can(List<String> paths) => user?.can(paths) ?? false;

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_tokenKey);
    if (token == null || token.isEmpty) {
      restoring = false;
      notifyListeners();
      return;
    }
    Api.token = token;
    final cached = prefs.getString(_userKey);
    if (cached != null) {
      try {
        user = UserInfo.from(Map<String, dynamic>.from(jsonDecode(cached) as Map));
      } catch (_) {
        user = null;
      }
    }
    restoring = false;
    notifyListeners();
    try {
      final data = await Api.get<Map<String, dynamic>>('/auth/me');
      user = UserInfo.from(data);
      await prefs.setString(_userKey, jsonEncode(data));
    } on ApiException catch (e) {
      if (e.statusCode == 401) {
        await _clearLogin();
      }
    }
    notifyListeners();
  }

  Future<void> login(String account, String password) async {
    final data = await Api.post<Map<String, dynamic>>('/auth/login', {
      'account': account,
      'password': password,
    });
    Api.token = '${data['token']}';
    user = UserInfo.from(data);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, Api.token!);
    await prefs.setString(_userKey, jsonEncode(data));
    notifyListeners();
  }

  Future<void> logout() async {
    try {
      await Api.post('/auth/logout');
    } catch (_) {
      // 后端不可达时也允许本地退出
    }
    await _clearLogin();
    notifyListeners();
  }

  Future<void> _clearLogin() async {
    Api.token = null;
    user = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
    await prefs.remove(_userKey);
    notifyListeners();
  }
}

final appState = AppState();
