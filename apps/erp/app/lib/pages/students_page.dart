import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'student_detail_page.dart';

class StudentsPage extends StatefulWidget {
  const StudentsPage({super.key});

  @override
  State<StudentsPage> createState() => _StudentsPageState();
}

class _StudentsPageState extends State<StudentsPage> {
  static const _statuses = ['全部', '在读', '停课', '结课'];

  final _searchController = TextEditingController();
  final _scrollController = ScrollController();
  Timer? _debounce;

  final List<Student> _students = [];
  StudentSummary? _summary;
  String _keyword = '';
  String _status = '在读';
  int _page = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  bool get _hasMore => _students.length < _total;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _load(reset: true);
    _loadSummary();
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

  Map<String, dynamic> _query(int page) => {
        'page': page,
        'size': 20,
        if (_keyword.isNotEmpty) 'keyword': _keyword,
        if (_status != '全部') 'status': _status,
      };

  Future<void> _load({bool reset = false}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 1;
      });
    }
    try {
      final data = await Api.get<Map<String, dynamic>>('/students', _query(reset ? 1 : _page));
      final page = PageData.from<Student>(data, Student.from);
      if (!mounted) return;
      setState(() {
        if (reset) _students.clear();
        _students.addAll(page.records);
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
        _error = '学员数据加载失败';
      });
    }
  }

  Future<void> _loadMore() async {
    setState(() => _loadingMore = true);
    await _load();
  }

  Future<void> _loadSummary() async {
    try {
      final data = await Api.get<Map<String, dynamic>>('/students/summary', {
        if (_keyword.isNotEmpty) 'keyword': _keyword,
        if (_status != '全部') 'status': _status,
      });
      if (!mounted) return;
      setState(() => _summary = StudentSummary.from(data));
    } catch (_) {
      // 汇总失败不阻断列表展示
    }
  }

  void _refreshAll() {
    _load(reset: true);
    _loadSummary();
  }

  void _onKeywordChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      _keyword = value.trim();
      _refreshAll();
    });
  }

  void _onStatusChanged(String status) {
    if (_status == status) return;
    setState(() => _status = status);
    _refreshAll();
  }

  Future<void> _openStudent(Student student) async {
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => StudentDetailPage(studentId: student.id, studentName: student.name)),
    );
    if (mounted) _refreshAll();
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
                    child: Text('学员', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w600, color: AppColors.text)),
                  ),
                  Text(
                    '共 $_total 人',
                    style: const TextStyle(fontSize: 12.5, color: AppColors.muted),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: SearchBarField(
                controller: _searchController,
                hint: '搜索学员姓名 / 手机号',
                onSubmitted: (value) {
                  _keyword = value.trim();
                  _refreshAll();
                },
                onChanged: _onKeywordChanged,
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 34,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 20),
                itemCount: _statuses.length,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final status = _statuses[index];
                  final selected = status == _status;
                  return GestureDetector(
                    onTap: () => _onStatusChanged(status),
                    child: Container(
                      alignment: Alignment.center,
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: selected ? AppColors.primary : Colors.white,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: selected ? AppColors.primary : AppColors.border),
                      ),
                      child: Text(
                        status,
                        style: TextStyle(
                          fontSize: 13,
                          color: selected ? Colors.white : AppColors.sub,
                          fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            if (_summary != null) ...[
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: SurfaceCard(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  child: Row(
                    children: [
                      Expanded(child: MetricTile(label: '筛选人数', value: '${_summary!.count}', unit: '人')),
                      Expanded(child: MetricTile(label: '账户余额', value: money(_summary!.balance), unit: '元', align: CrossAxisAlignment.center)),
                      Expanded(child: MetricTile(label: '欠费', value: money(_summary!.debt), unit: '元', color: AppColors.danger, align: CrossAxisAlignment.end)),
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
                      ? ErrorView(_error!, onRetry: _refreshAll)
                      : RefreshIndicator(
                          onRefresh: () async => _refreshAll(),
                          child: _students.isEmpty
                              ? ListView(
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  children: [
                                    EmptyState(
                                      title: '暂无学员',
                                      subtitle: _status == '在读' ? '当前筛选条件下暂无在读学员' : '换个筛选条件试试',
                                    ),
                                  ],
                                )
                              : ListView.separated(
                                  controller: _scrollController,
                                  physics: const AlwaysScrollableScrollPhysics(),
                                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
                                  itemCount: _students.length + 1,
                                  separatorBuilder: (_, _) => const SizedBox(height: 10),
                                  itemBuilder: (context, index) {
                                    if (index == _students.length) {
                                      return Padding(
                                        padding: const EdgeInsets.symmetric(vertical: 16),
                                        child: Center(
                                          child: Text(
                                            _hasMore ? '加载中…' : '已加载全部 $_total 位学员',
                                            style: const TextStyle(fontSize: 12, color: AppColors.muted),
                                          ),
                                        ),
                                      );
                                    }
                                    final student = _students[index];
                                    return _StudentTile(student: student, onTap: () => _openStudent(student));
                                  },
                                ),
                        ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StudentTile extends StatelessWidget {
  const _StudentTile({required this.student, required this.onTap});

  final Student student;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      child: Row(
        children: [
          AvatarBadge(student.name, size: 42),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        student.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                      ),
                    ),
                    if (student.isStar == 1) ...[
                      const SizedBox(width: 4),
                      const Icon(Icons.star_rounded, size: 14, color: AppColors.warning),
                    ],
                    const SizedBox(width: 8),
                    StatusTag(student.status, compact: true),
                  ],
                ),
                const SizedBox(height: 5),
                Text(
                  [
                    if (student.gender.isNotEmpty) student.gender,
                    if ((student.ageText ?? '').isNotEmpty) student.ageText!,
                    student.campusName,
                    '在读 ${student.courseCount} 门',
                  ].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '余额 ${money(student.balance)}',
                style: const TextStyle(fontSize: 12.5, color: AppColors.text, fontWeight: FontWeight.w500),
              ),
              const SizedBox(height: 4),
              Text(
                student.debt > 0 ? '欠费 ${money(student.debt)}' : '积分 ${student.points}',
                style: TextStyle(
                  fontSize: 11.5,
                  color: student.debt > 0 ? AppColors.danger : AppColors.muted,
                ),
              ),
            ],
          ),
          const SizedBox(width: 2),
          const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
        ],
      ),
    );
  }
}
