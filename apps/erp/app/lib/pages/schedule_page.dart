import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'class_detail_page.dart';

class SchedulePage extends StatefulWidget {
  const SchedulePage({super.key});

  @override
  State<SchedulePage> createState() => _SchedulePageState();
}

class _SchedulePageState extends State<SchedulePage> {
  static const _statuses = ['全部', '待上课', '已上课', '已取消', '休课'];

  DateTime _selected = DateTime.now();
  DateTime _weekStart = _mondayOf(DateTime.now());
  String _status = '全部';
  List<ScheduleItem> _items = const [];
  Map<String, dynamic> _summary = const {};
  bool _loading = true;
  String? _error;

  static DateTime _mondayOf(DateTime date) => DateTime(date.year, date.month, date.day - (date.weekday - 1));

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final day = dateText(_selected);
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/schedules', {
          'start': day,
          'end': day,
          'size': 200,
          if (_status != '全部') 'status': _status,
        }),
        Api.get<Map<String, dynamic>>('/schedules/summary', {
          'start': day,
          'end': day,
          if (_status != '全部') 'status': _status,
        }).catchError((_) => <String, dynamic>{}),
      ]);
      if (!mounted) return;
      final list = PageData.from<ScheduleItem>(results[0], ScheduleItem.from).records
        ..sort((a, b) => a.startTime.compareTo(b.startTime));
      setState(() {
        _items = list;
        _summary = results[1];
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
        _error = '课表加载失败';
      });
    }
  }

  void _selectDay(DateTime day) {
    setState(() {
      _selected = day;
      _weekStart = _mondayOf(day);
    });
    _load();
  }

  void _shiftWeek(int weeks) {
    final start = _weekStart.add(Duration(days: weeks * 7));
    setState(() {
      _weekStart = start;
      _selected = start;
    });
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final days = List.generate(7, (index) => _weekStart.add(Duration(days: index)));
    final today = DateTime.now();

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: Row(
                children: [
                  const Expanded(
                    child: Text('课表', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600, color: AppColors.text)),
                  ),
                  Text(
                    '${_selected.year} 年 ${_selected.month} 月',
                    style: const TextStyle(fontSize: 12.5, color: AppColors.muted),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            SurfaceCard(
              margin: const EdgeInsets.symmetric(horizontal: 20),
              padding: const EdgeInsets.fromLTRB(8, 10, 8, 12),
              child: Column(
                children: [
                  Row(
                    children: [
                      IconButton(
                        onPressed: () => _shiftWeek(-1),
                        icon: const Icon(Icons.chevron_left_rounded, size: 20, color: AppColors.sub),
                        visualDensity: VisualDensity.compact,
                      ),
                      Expanded(
                        child: Text(
                          '${compactDate(days.first)} - ${compactDate(days.last)}',
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 12.5, color: AppColors.sub, fontWeight: FontWeight.w500),
                        ),
                      ),
                      IconButton(
                        onPressed: () => _shiftWeek(1),
                        icon: const Icon(Icons.chevron_right_rounded, size: 20, color: AppColors.sub),
                        visualDensity: VisualDensity.compact,
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      for (final day in days)
                        Expanded(
                          child: GestureDetector(
                            onTap: () => _selectDay(day),
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 160),
                              margin: const EdgeInsets.symmetric(horizontal: 2),
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              decoration: BoxDecoration(
                                color: sameDay(day, _selected) ? AppColors.primary : Colors.transparent,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Column(
                                children: [
                                  Text(
                                    weekdayCn(day).replaceFirst('周', ''),
                                    style: TextStyle(
                                      fontSize: 11,
                                      color: sameDay(day, _selected) ? Colors.white70 : AppColors.muted,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${day.day}',
                                    style: TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.w600,
                                      color: sameDay(day, _selected)
                                          ? Colors.white
                                          : sameDay(day, today)
                                              ? AppColors.primary
                                              : AppColors.text,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Container(
                                    width: 4,
                                    height: 4,
                                    decoration: BoxDecoration(
                                      color: sameDay(day, today) && !sameDay(day, _selected)
                                          ? AppColors.primary
                                          : Colors.transparent,
                                      shape: BoxShape.circle,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            PillTabs(
              labels: _statuses,
              index: _statuses.indexOf(_status),
              padding: const EdgeInsets.symmetric(horizontal: 20),
              onChanged: (index) {
                setState(() => _status = _statuses[index]);
                _load();
              },
            ),
            if (_summary.isNotEmpty) ...[
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    _summaryChip('共 ${intOf(_summary['total'])} 节'),
                    const SizedBox(width: 8),
                    _summaryChip('已上 ${intOf(_summary['done'])} 节', color: AppColors.success),
                    const SizedBox(width: 8),
                    _summaryChip('待上 ${intOf(_summary['pending'])} 节', color: AppColors.warning),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 8),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? ErrorView(_error!, onRetry: _load)
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: _items.isEmpty
                              ? ListView(
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  children: [
                                    EmptyState(
                                      icon: Icons.event_available_outlined,
                                      title: '${_selected.month} 月 ${_selected.day} 日暂无课程',
                                      subtitle: '切换日期或状态筛选查看其他课次',
                                    ),
                                  ],
                                )
                              : ListView.separated(
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
                                  itemCount: _items.length,
                                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                                  itemBuilder: (context, index) => _ScheduleCard(item: _items[index]),
                                ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _summaryChip(String text, {Color color = AppColors.sub}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8), border: Border.all(color: AppColors.border)),
      child: Text(text, style: TextStyle(fontSize: 11.5, color: color)),
    );
  }
}

class _ScheduleCard extends StatelessWidget {
  const _ScheduleCard({required this.item});

  final ScheduleItem item;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      padding: const EdgeInsets.all(15),
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
            width: 52,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.startTime,
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
                const SizedBox(height: 2),
                Text(item.endTime, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
              ],
            ),
          ),
          Container(
            width: 3,
            height: 44,
            margin: const EdgeInsets.only(right: 12, top: 2),
            decoration: BoxDecoration(
              color: item.status == '已上课'
                  ? AppColors.success
                  : item.status == '休课'
                      ? AppColors.purple
                      : item.status == '已取消'
                          ? AppColors.muted
                          : AppColors.primary,
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
                        style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                      ),
                    ),
                    const SizedBox(width: 8),
                    StatusTag(item.status, compact: true),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  [
                    if ((item.courseName ?? '').isNotEmpty) item.courseName!,
                    if ((item.teacherName ?? '').isNotEmpty) item.teacherName!,
                    if ((item.room ?? '').isNotEmpty) item.room!,
                  ].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
                if ((item.topic ?? '').isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    item.topic!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                  ),
                ],
                const SizedBox(height: 6),
                Text(
                  '应到 ${item.shouldCount} · 实到 ${item.actualCount}',
                  style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
