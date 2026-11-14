import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/config.dart';
import '../state/app_state.dart';
import '../ui/theme.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _account = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _account.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    final account = _account.text.trim();
    final password = _password.text;
    if (account.isEmpty || password.isEmpty) {
      setState(() => _error = '请输入账号与密码');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await appState.login(account, password);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '登录失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(28, 0, 28, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 72),
              Container(
                width: 64,
                height: 64,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: AppColors.danger,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Text(
                  '幻',
                  style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w600, height: 1.1),
                ),
              ),
              const SizedBox(height: 26),
              const Text(
                '幻彩学堂',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.w600, color: AppColors.text, height: 1.2),
              ),
              const SizedBox(height: 8),
              const Text(
                '教务管理 · 移动端',
                style: TextStyle(fontSize: 14, color: AppColors.muted),
              ),
              const SizedBox(height: 44),
              const Text('账号', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w500)),
              const SizedBox(height: 8),
              TextField(
                controller: _account,
                textInputAction: TextInputAction.next,
                style: const TextStyle(fontSize: 15),
                decoration: const InputDecoration(
                  hintText: '请输入员工账号',
                  prefixIcon: Icon(Icons.person_outline_rounded, size: 20, color: AppColors.muted),
                ),
              ),
              const SizedBox(height: 18),
              const Text('密码', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w500)),
              const SizedBox(height: 8),
              TextField(
                controller: _password,
                obscureText: _obscure,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _submit(),
                style: const TextStyle(fontSize: 15),
                decoration: InputDecoration(
                  hintText: '请输入登录密码',
                  prefixIcon: const Icon(Icons.lock_outline_rounded, size: 20, color: AppColors.muted),
                  suffixIcon: IconButton(
                    icon: Icon(
                      _obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                      size: 20,
                      color: AppColors.muted,
                    ),
                    onPressed: () => setState(() => _obscure = !_obscure),
                  ),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 14),
                Row(
                  children: [
                    const Icon(Icons.error_outline_rounded, size: 15, color: AppColors.danger),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(_error!, style: const TextStyle(fontSize: 12.5, color: AppColors.danger, height: 1.5)),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: 30),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _loading ? null : _submit,
                  child: _loading
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('登录'),
                ),
              ),
              const SizedBox(height: 20),
              Center(
                child: Text(
                  '服务地址 ${AppConfig.baseHost.replaceFirst('http://', '')}',
                  style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
