import 'package:flutter/material.dart';

import '../core/config.dart';
import '../state/app_state.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'grades_page.dart';
import 'home_service_page.dart';
import 'orders_page.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});

  Future<void> _logout(BuildContext context) async {
    final ok = await confirmDialog(context, '退出登录', '确认退出当前账号吗？');
    if (!ok) return;
    await appState.logout();
  }

  @override
  Widget build(BuildContext context) {
    final user = appState.user;
    final menus = <Widget>[
      if (appState.can(const ['/order']))
        _MenuTile(
          icon: Icons.receipt_long_outlined,
          title: '订单管理',
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const OrdersPage())),
        ),
      if (appState.can(const ['/gradeExam', '/gradeQuery', '/gradeAnalysis']))
        _MenuTile(
          icon: Icons.insights_outlined,
          title: '成绩中心',
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const GradesPage())),
        ),
      if (appState.can(const ['/messagePush', '/homework', '/classSuspend', '/classComment']))
        _MenuTile(
          icon: Icons.forum_outlined,
          title: '家校服务',
          onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HomeServicePage())),
        ),
    ];

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
          children: [
            const Text('我的', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600, color: AppColors.text)),
            const SizedBox(height: 16),
            SurfaceCard(
              padding: const EdgeInsets.all(18),
              child: Row(
                children: [
                  AvatarBadge(user?.name ?? '幻', size: 56),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.name ?? '-',
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.text),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          [
                            user?.role ?? '员工',
                            user == null ? '' : '工号 ${user.staffId}',
                            user?.isAdmin == true ? '全部权限' : '已分配 ${user?.permissions.length ?? 0} 项权限',
                          ].where((e) => e.isNotEmpty).join(' · '),
                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (menus.isNotEmpty) ...[
              const SectionTitle('功能入口', padding: EdgeInsets.fromLTRB(0, 22, 0, 8)),
              SurfaceCard(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                child: Column(
                  children: [
                    for (var i = 0; i < menus.length; i++) ...[
                      menus[i],
                      if (i != menus.length - 1) const Divider(height: 1, color: AppColors.border),
                    ],
                  ],
                ),
              ),
            ],
            const SectionTitle('关于', padding: EdgeInsets.fromLTRB(0, 22, 0, 8)),
            SurfaceCard(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Column(
                children: [
                  InfoRow('服务地址', AppConfig.baseHost.replaceFirst('http://', '')),
                  InfoRow('应用版本', 'v1.0.0'),
                  InfoRow('数据来源', '幻彩学堂教务系统 · 本地数据库'),
                ],
              ),
            ),
            const SizedBox(height: 24),
            OutlinedButton(
              onPressed: () => _logout(context),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.danger,
                side: const BorderSide(color: Color(0xFFF3D4D2)),
              ),
              child: const Text('退出登录'),
            ),
          ],
        ),
      ),
    );
  }
}

class _MenuTile extends StatelessWidget {
  const _MenuTile({required this.icon, required this.title, required this.onTap});

  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Row(
          children: [
            Icon(icon, size: 19, color: AppColors.sub),
            const SizedBox(width: 12),
            Expanded(child: Text(title, style: const TextStyle(fontSize: 14, color: AppColors.text))),
            const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
          ],
        ),
      ),
    );
  }
}
