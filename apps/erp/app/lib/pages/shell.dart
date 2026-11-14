import 'package:flutter/material.dart';

import '../state/app_state.dart';
import 'classes_page.dart';
import 'dashboard_page.dart';
import 'profile_page.dart';
import 'schedule_page.dart';
import 'students_page.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _TabDef {
  _TabDef(this.label, this.icon, this.activeIcon, this.builder);

  final String label;
  final IconData icon;
  final IconData activeIcon;
  final WidgetBuilder builder;
}

class _MainShellState extends State<MainShell> {
  int _index = 0;

  late final List<_TabDef> _tabs = [
    _TabDef('工作台', Icons.grid_view_outlined, Icons.grid_view_rounded,
        (context) => DashboardPage(onOpenSchedule: () => _selectByLabel('课表'), onOpenProfile: () => _selectByLabel('我的'))),
    if (appState.can(const ['/student']))
      _TabDef('学员', Icons.people_outline_rounded, Icons.people_rounded, (_) => const StudentsPage()),
    if (appState.can(const ['/class']))
      _TabDef('班级', Icons.groups_outlined, Icons.groups_rounded, (_) => const ClassesPage()),
    if (appState.can(const ['/schedule']))
      _TabDef('课表', Icons.calendar_month_outlined, Icons.calendar_month_rounded, (_) => const SchedulePage()),
    _TabDef('我的', Icons.person_outline_rounded, Icons.person_rounded, (_) => const ProfilePage()),
  ];

  void _selectByLabel(String label) {
    final index = _tabs.indexWhere((tab) => tab.label == label);
    if (index >= 0) setState(() => _index = index);
  }

  @override
  Widget build(BuildContext context) {
    if (_index >= _tabs.length) _index = 0;
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          for (var i = 0; i < _tabs.length; i++)
            _KeepAlive(
              keep: i == _index,
              child: Builder(builder: _tabs[i].builder),
            ),
        ],
      ),
      bottomNavigationBar: DecoratedBox(
        decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: Color(0xFFEEF0F3))),
        ),
        child: NavigationBar(
          selectedIndex: _index,
          onDestinationSelected: (value) => setState(() => _index = value),
          destinations: [
            for (final tab in _tabs)
              NavigationDestination(
                icon: Icon(tab.icon),
                selectedIcon: Icon(tab.activeIcon),
                label: tab.label,
              ),
          ],
        ),
      ),
    );
  }
}

/// IndexedStack 会保留页面状态，这里仅用于标记当前页，避免重复销毁重建。
class _KeepAlive extends StatelessWidget {
  const _KeepAlive({required this.keep, required this.child});

  final bool keep;
  final Widget child;

  @override
  Widget build(BuildContext context) => child;
}
