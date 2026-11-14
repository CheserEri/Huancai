import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../state/app_state.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'class_detail_page.dart';
import 'grades_page.dart';
import 'home_service_page.dart';
import 'orders_page.dart';
import 'payment_page.dart';
import 'stock_page.dart';

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key, required this.onOpenSchedule, required this.onOpenProfile});

  final VoidCallback onOpenSchedule;
  final VoidCallback onOpenProfile;

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  DashboardSummary? _summary;
  List<ScheduleItem> _today = const [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) setState(() => _error = null);
    try {
      final today = todayText();
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/dashboard/summary'),
        Api.get<Map<String, dynamic>>('/schedules', {
          'start': today,
          'end': today,
          'page': 1,
          'size': 100,
        }),
      ]);
      final page = PageData.from<ScheduleItem>(results[1], ScheduleItem.from);
      final list = [...page.records]..sort((a, b) => a.startTime.compareTo(b.startTime));
      if (!mounted) return;
      setState(() {
        _summary = DashboardSummary.from(results[0]);
        _today = list;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = '数据加载失败，请稍后重试';
      });
    }
  }

  Future<void> _openOrders() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const OrdersPage()));
  }

  Future<void> _openGrades() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const GradesPage()));
  }

  Future<void> _openHomeService(int tab) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => HomeServicePage(initialTab: tab)));
  }

  Future<void> _openPayment() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PaymentPage()));
  }

  Future<void> _openStock() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const StockPage()));
  }

  @override
  Widget build(BuildContext context) {
    final user = appState.user;
    final entries = <Widget>[
      if (appState.can(const ['/order']))
        QuickEntry(
          icon: Icons.receipt_long_outlined,
          label: '订单',
          color: AppColors.primary,
          background: AppColors.primarySoft,
          onTap: _openOrders,
        ),
      if (appState.can(const ['/order', '/student']))
        QuickEntry(
          icon: Icons.account_balance_wallet_outlined,
          label: '缴费',
          color: AppColors.warning,
          background: AppColors.warningSoft,
          onTap: _openPayment,
        ),
      if (appState.can(const ['/gradeExam', '/gradeQuery', '/gradeAnalysis']))
        QuickEntry(
          icon: Icons.insights_outlined,
          label: '成绩',
          color: AppColors.purple,
          background: AppColors.purpleSoft,
          onTap: _openGrades,
        ),
      if (appState.can(const ['/messagePush', '/homework', '/classSuspend', '/classComment']))
        QuickEntry(
          icon: Icons.forum_outlined,
          label: '家校服务',
          color: AppColors.teal,
          background: AppColors.tealSoft,
          onTap: () => _openHomeService(0),
        ),
      if (appState.can(const ['/messagePush']))
        QuickEntry(
          icon: Icons.campaign_outlined,
          label: '消息推送',
          color: AppColors.warning,
          background: AppColors.warningSoft,
          onTap: () => _openHomeService(0),
        ),
      if (appState.can(const ['/homework']))
        QuickEntry(
          icon: Icons.menu_book_outlined,
          label: '作业',
          color: AppColors.success,
          background: AppColors.successSoft,
          onTap: () => _openHomeService(1),
        ),
      if (appState.can(const ['/classSuspend']))
        QuickEntry(
          icon: Icons.pause_circle_outline_rounded,
          label: '班级休课',
          color: AppColors.danger,
          background: AppColors.dangerSoft,
          onTap: () => _openHomeService(2),
        ),
      if (appState.can(const ['/classAlbum']))
        QuickEntry(
          icon: Icons.photo_library_outlined,
          label: '班级相册',
          color: AppColors.teal,
          background: AppColors.tealSoft,
          onTap: () => _openHomeService(3),
        ),
      if (appState.can(const ['/storage']))
        QuickEntry(
          icon: Icons.inventory_2_outlined,
          label: '教材库存',
          color: AppColors.purple,
          background: AppColors.purpleSoft,
          onTap: _openStock,
        ),
    ];

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.zero,
          children: [
            SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 6),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${greetingText()}，${user?.name ?? ''}',
                            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: AppColors.text, height: 1.3),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${DateTime.now().month} 月 ${DateTime.now().day} 日 ${weekdayCn(DateTime.now())} · ${user?.role ?? ''}',
                            style: const TextStyle(fontSize: 12.5, color: AppColors.muted),
                          ),
                        ],
                      ),
                    ),
                    GestureDetector(
                      onTap: widget.onOpenProfile,
                      child: AvatarBadge(user?.name ?? '幻', size: 42),
                    ),
                  ],
                ),
              ),
            ),
            if (_loading)
              const LoadingView(height: 360)
            else if (_error != null)
              ErrorView(_error!, onRetry: _load)
            else ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                child: _HeroCard(summary: _summary!, onOpenSchedule: widget.onOpenSchedule),
              ),
              if (entries.isNotEmpty) ...[
                const SectionTitle('快捷入口'),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final count = entries.length >= 4 ? 4 : entries.length;
                      final width = constraints.maxWidth / count;
                      return Wrap(
                        children: [
                          for (final entry in entries) SizedBox(width: width, child: entry),
                        ],
                      );
                    },
                  ),
                ),
              ],
              SectionTitle(
                '今日课程',
                trailing: TextButton(
                  onPressed: widget.onOpenSchedule,
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 0)),
                  child: const Text('全部课表', style: TextStyle(fontSize: 12.5)),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: _today.isEmpty
                    ? const SurfaceCard(
                        child: InlineHint('今天没有排课记录，可前往「课表」查看其他日期。'),
                      )
                    : Column(
                        children: [
                          for (final item in _today.take(4))
                            Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: _ScheduleTile(item: item),
                            ),
                          if (_today.length > 4)
                            Align(
                              alignment: Alignment.centerLeft,
                              child: TextButton(
                                onPressed: widget.onOpenSchedule,
                                style: TextButton.styleFrom(padding: EdgeInsets.zero),
                                child: Text('还有 ${_today.length - 4} 节课程，查看全部', style: const TextStyle(fontSize: 12.5)),
                              ),
                            ),
                        ],
                      ),
              ),
              if ((_summary?.topCourses ?? const []).isNotEmpty) ...[
                const SectionTitle('热门课程'),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 0),
                  child: SurfaceCard(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    child: Column(
                      children: [
                        for (var i = 0; i < _summary!.topCourses.length; i++) ...[
                          _CourseRow(
                            index: i,
                            data: _summary!.topCourses[i],
                            max: doubleOf(_summary!.topCourses.first['students'], 1),
                          ),
                          if (i != _summary!.topCourses.length - 1) const Divider(height: 1, color: AppColors.border),
                        ],
                      ],
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 28),
            ],
          ],
        ),
      ),
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({required this.summary, required this.onOpenSchedule});

  final DashboardSummary summary;
  final VoidCallback onOpenSchedule;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
      decoration: BoxDecoration(
        color: const Color(0xFF1B1F26),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('今日课程', style: TextStyle(fontSize: 12.5, color: Color(0xB3FFFFFF))),
                  const SizedBox(height: 6),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text(
                        '${summary.todayClasses}',
                        style: const TextStyle(fontSize: 38, fontWeight: FontWeight.w600, color: Colors.white, height: 1),
                      ),
                      const SizedBox(width: 4),
                      const Padding(
                        padding: EdgeInsets.only(bottom: 4),
                        child: Text('节', style: TextStyle(fontSize: 12, color: Color(0xB3FFFFFF))),
                      ),
                    ],
                  ),
                ],
              ),
              const Spacer(),
              TextButton(
                onPressed: onOpenSchedule,
                style: TextButton.styleFrom(
                  foregroundColor: Colors.white,
                  backgroundColor: const Color(0x1AFFFFFF),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  minimumSize: const Size(0, 0),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                ),
                child: const Text('查看课表', style: TextStyle(fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Container(height: 1, color: const Color(0x14FFFFFF)),
          const SizedBox(height: 14),
          Row(
            children: [
              _heroMetric('在读学员', '${summary.activeStudents}'),
              _heroDivider(),
              _heroMetric('剩余课时', countText(summary.remainLessons)),
              _heroDivider(),
              _heroMetric('待收欠费', yuan(summary.debt)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _heroMetric(String label, String value) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(fontSize: 11.5, color: Color(0x80FFFFFF))),
          const SizedBox(height: 5),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white, height: 1.2),
          ),
        ],
      ),
    );
  }

  Widget _heroDivider() => Container(
        width: 1,
        height: 26,
        margin: const EdgeInsets.symmetric(horizontal: 10),
        color: const Color(0x14FFFFFF),
      );
}

class _ScheduleTile extends StatelessWidget {
  const _ScheduleTile({required this.item});

  final ScheduleItem item;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      onTap: item.classId == null
          ? null
          : () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => ClassDetailPage(classId: item.classId!, className: item.className ?? '班级详情'),
                ),
              ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 48,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.startTime, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text)),
                const SizedBox(height: 2),
                Text(item.endTime, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
              ],
            ),
          ),
          Container(
            width: 3,
            height: 38,
            margin: const EdgeInsets.only(right: 12, top: 2),
            decoration: BoxDecoration(
              color: item.status.contains('已上') ? AppColors.success : AppColors.primary,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        item.className ?? item.courseName ?? '未命名课程',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                      ),
                    ),
                    const SizedBox(width: 8),
                    StatusTag(item.status, compact: true),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  [
                    if ((item.teacherName ?? '').isNotEmpty) item.teacherName!,
                    if ((item.room ?? '').isNotEmpty) item.room!,
                    if ((item.topic ?? '').isNotEmpty) item.topic!,
                  ].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CourseRow extends StatelessWidget {
  const _CourseRow({required this.index, required this.data, required this.max});

  final int index;
  final Map<String, dynamic> data;
  final double max;

  @override
  Widget build(BuildContext context) {
    final students = doubleOf(data['students']);
    final name = str(data['courseName'], '未命名课程');
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          Container(
            width: 22,
            height: 22,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: index < 3 ? AppColors.primarySoft : const Color(0xFFF3F4F6),
              borderRadius: BorderRadius.circular(7),
            ),
            child: Text(
              '${index + 1}',
              style: TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w600,
                color: index < 3 ? AppColors.primary : AppColors.muted,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13, color: AppColors.text, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 6),
                MiniBar(value: max <= 0 ? 0 : students / max, height: 4),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(
            '${countText(students)} 人',
            style: const TextStyle(fontSize: 12, color: AppColors.muted),
          ),
        ],
      ),
    );
  }
}
