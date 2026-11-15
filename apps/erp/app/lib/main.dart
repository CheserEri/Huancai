import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'pages/login_page.dart';
import 'pages/shell.dart';
import 'state/app_state.dart';
import 'ui/theme.dart';

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // 登录态在后台恢复，避免网络不可达时启动白屏等待
  appState.restore();
  runApp(const HuancaiApp());
}

class HuancaiApp extends StatelessWidget {
  const HuancaiApp({super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (context, _) {
        final loggedIn = appState.loggedIn;
        if (!loggedIn) {
          // 登录态失效（含 401）时回到登录页，避免停留在我已无权限的页面
          WidgetsBinding.instance.addPostFrameCallback((_) {
            navigatorKey.currentState?.popUntil((route) => route.isFirst);
          });
        }
        return MaterialApp(
          title: '幻彩学堂',
          debugShowCheckedModeBanner: false,
          navigatorKey: navigatorKey,
          theme: buildAppTheme(),
          locale: const Locale('zh', 'CN'),
          supportedLocales: const [Locale('zh', 'CN'), Locale('en')],
          localizationsDelegates: const [
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: appState.restoring
              ? const _SplashPage()
              : loggedIn
                  ? const MainShell()
                  : const LoginPage(),
        );
      },
    );
  }
}

class _SplashPage extends StatelessWidget {
  const _SplashPage();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Colors.white,
      body: Center(
        child: SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
    );
  }
}
