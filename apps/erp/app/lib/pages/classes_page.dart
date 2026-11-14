import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'class_detail_page.dart';

class ClassesPage extends StatefulWidget {
  const ClassesPage({super.key});

  @override
  State<ClassesPage> createState() => _ClassesPageState();
}

class _ClassesPageState extends State<ClassesPage> {
  static const _filters = ['全部', '开放招生', '已开班', '已结班'];

  final _searchController = TextEditingController();
  final _scrollController = ScrollController();
  Timer? _debounce;

  final List<ClassInfo> _classes = [];
  String _keyword = '';
  String _filter = '全部';
  int _page = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  Map<String, dynamic> _summary = const {};

  bool get _hasMore => _classes.length < _total;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scrollController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter < 240 && !_loadingMore && _hasMore && !_loading) {
      _loadMore();
    }
  }

  Map<String, dynamic> _query(int page) {
    final params = <String, dynamic>{
      'page': page,
      'size': 20,
      if (_keyword.isNotEmpty) 'keyword': _keyword,
    };
    if (_filter == '开放招生') params['enrolling'] = 1;
    if (_filter == '已开班') {
      params['status'] = '已开班';
      params['enrolling'] = 0;
    }
    if (_filter == '已结班') {
      params['status'] = '已结班';
      params['includeClosed'] = true;
    }
    return params;
  }

  Map<String, dynamic> _summaryQuery() {
    final query = _query(1)..remove('page')..remove('size');
    return query;
  }

  Future<void> _load({bool reset = false}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 1;
      });
      _loadSummary();
    }
    try {
      final data = await Api.get<Map<String, dynamic>>('/classes', _query(reset ? 1 : _page));
      final page = PageData.from<ClassInfo>(data, ClassInfo.from);
      if (!mounted) return;
      setState(() {
        if (reset) _classes.clear();
        _classes.addAll(page.records);
        _total = page.total;
        _page = (reset ? 1 : _page) + 1;
        _loading = false;
        _loadingMore = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = '班级数据加载失败';
      });
    }
  }

  Future<void> _loadMore() async {
    setState(() => _loadingMore = true);
    await _load();
  }

  Future<void> _loadSummary() async {
    try {
      final data = await Api.get<Map<String, dynamic>>('/classes/summary', _summaryQuery());
      if (!mounted) return;
      setState(() => _summary = data);
    } catch (_) {
      // 汇总失败不阻断列表
    }
  }

  void _refresh() {
    _load(reset: true);
  }

  @override
  Widget build(BuildContext context) {
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
                    child: Text('班级', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600, color: AppColors.text)),
                  ),
                  Text('共 $_total 个', style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: SearchBarField(
                controller: _searchController,
                hint: '搜索班级 / 课程 / 教师',
                onSubmitted: (value) {
                  _keyword = value.trim();
                  _refresh();
                },
                onChanged: (value) {
                  _debounce?.cancel();
                  _debounce = Timer(const Duration(milliseconds: 400), () {
                    _keyword = value.trim();
                    _refresh();
                  });
                },
              ),
            ),
            const SizedBox(height: 12),
            PillTabs(
              labels: _filters,
              index: _filters.indexOf(_filter),
              padding: const EdgeInsets.symmetric(horizontal: 20),
              onChanged: (index) {
                setState(() => _filter = _filters[index]);
                _refresh();
              },
            ),
            if (_summary.isNotEmpty) ...[
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: SurfaceCard(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  child: Row(
                    children: [
                      Expanded(child: MetricTile(label: '班级总数', value: '${intOf(_summary['total'])}', unit: '个')),
                      Expanded(child: MetricTile(label: '在读班级', value: '${intOf(_summary['active'])}', unit: '个', align: CrossAxisAlignment.center)),
                      Expanded(child: MetricTile(label: '班级学员', value: '${intOf(_summary['studentCount'])}', unit: '人', align: CrossAxisAlignment.end)),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 8),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? ErrorView(_error!, onRetry: _refresh)
                      : RefreshIndicator(
                          onRefresh: () async => _refresh(),
                          child: _classes.isEmpty
                              ? ListView(
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  children: const [EmptyState(title: '暂无班级', subtitle: '当前筛选条件下没有匹配的班级')],
                                )
                              : ListView.separated(
                                  controller: _scrollController,
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
                                  itemCount: _classes.length + 1,
                                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                                  itemBuilder: (context, index) {
                                    if (index == _classes.length) {
                                      return Padding(
                                        padding: const EdgeInsets.symmetric(vertical: 16),
                                        child: Center(
                                          child: Text(
                                            _hasMore ? '加载中…' : '已加载全部 $_total 个班级',
                                            style: const TextStyle(fontSize: 12, color: AppColors.muted),
                                          ),
                                        ),
                                      );
                                    }
                                    final item = _classes[index];
                                    return SurfaceCard(
                                      padding: const EdgeInsets.all(15),
                                      onTap: () => Navigator.of(context).push(
                                        MaterialPageRoute(
                                          builder: (_) => ClassDetailPage(classId: item.id, className: item.name),
                                        ),
                                      ),
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Row(
                                            children: [
                                              Expanded(
                                                child: Text(
                                                  item.name,
                                                  maxLines: 1,
                                                  overflow: TextOverflow.ellipsis,
                                                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                                                ),
                                              ),
                                              const SizedBox(width: 8),
                                              StatusTag(item.status, compact: true),
                                            ],
                                          ),
                                          const SizedBox(height: 6),
                                          Text(
                                            [
                                              item.courseName ?? '未绑定课程',
                                              item.campusName,
                                              if ((item.teacherName ?? '').isNotEmpty) item.teacherName!,
                                            ].join(' · '),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(fontSize: 12, color: AppColors.muted),
                                          ),
                                          const SizedBox(height: 10),
                                          Row(
                                            children: [
                                              _chip(Icons.groups_outlined, '${item.studentCount}/${item.capacity == 0 ? '不限' : item.capacity} 人'),
                                              const SizedBox(width: 14),
                                              if ((item.scheduleText ?? '').isNotEmpty)
                                                Flexible(child: _chip(Icons.schedule_rounded, item.scheduleText!)),
                                              const Spacer(),
                                              if (item.enrolling)
                                                const StatusTag('开放招生', compact: true),
                                            ],
                                          ),
                                        ],
                                      ),
                                    );
                                  },
                                ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _chip(IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: AppColors.muted),
        const SizedBox(width: 4),
        Flexible(
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
          ),
        ),
      ],
    );
  }
}
