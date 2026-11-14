import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'grade_analysis_page.dart';

class GradesPage extends StatefulWidget {
  const GradesPage({super.key});

  @override
  State<GradesPage> createState() => _GradesPageState();
}

class _GradesPageState extends State<GradesPage> {
  static const _statusFilters = ['全部', '已发布', '草稿', '已归档'];

  final _searchController = TextEditingController();
  Timer? _debounce;

  int _tab = 0;
  String _status = '全部';
  List<GradeExam> _exams = const [];
  bool _loadingExams = true;
  String? _examError;

  GradeExam? _selectedExam;
  List<GradeQueryRow> _queryRows = const [];
  bool _loadingQuery = false;
  String? _queryError;
  String _keyword = '';

  @override
  void initState() {
    super.initState();
    _loadExams();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadExams() async {
    setState(() {
      _loadingExams = true;
      _examError = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/exams', {
        if (_status != '全部') 'status': _status,
      });
      if (!mounted) return;
      setState(() {
        _exams = mapList(data).map(GradeExam.from).toList();
        _loadingExams = false;
        if (_selectedExam == null && _exams.isNotEmpty) {
          _selectedExam = _exams.first;
        }
      });
      if (_tab == 1 && _selectedExam != null && _queryRows.isEmpty) {
        _loadQuery();
      }
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loadingExams = false;
        _examError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingExams = false;
        _examError = '考试数据加载失败';
      });
    }
  }

  Future<void> _loadQuery() async {
    final exam = _selectedExam;
    if (exam == null) return;
    setState(() {
      _loadingQuery = true;
      _queryError = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/query', {
        'examId': exam.id,
        if (_keyword.isNotEmpty) 'keyword': _keyword,
      });
      if (!mounted) return;
      setState(() {
        _queryRows = mapList(data).map(GradeQueryRow.from).toList();
        _loadingQuery = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loadingQuery = false;
        _queryError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingQuery = false;
        _queryError = '成绩查询失败';
      });
    }
  }

  Future<void> _pickExam() async {
    if (_exams.isEmpty) return;
    final picked = await showModalBottomSheet<GradeExam>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('选择考试', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
              ),
            ),
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: _exams.length,
                separatorBuilder: (_, _) => const Divider(height: 1, color: AppColors.border),
                itemBuilder: (context, index) {
                  final exam = _exams[index];
                  return ListTile(
                    title: Text(exam.name, style: const TextStyle(fontSize: 14, color: AppColors.text)),
                    subtitle: Text(
                      '${dateOnly(exam.examDate)} · ${exam.status}',
                      style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                    ),
                    trailing: exam.id == _selectedExam?.id
                        ? const Icon(Icons.check_rounded, size: 18, color: AppColors.primary)
                        : null,
                    onTap: () => Navigator.pop(context, exam),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (picked == null) return;
    setState(() => _selectedExam = picked);
    _loadQuery();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('成绩中心')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: PillTabs(
              labels: const ['考试列表', '成绩查询'],
              index: _tab,
              padding: EdgeInsets.zero,
              onChanged: (value) {
                setState(() => _tab = value);
                if (value == 1 && _queryRows.isEmpty) _loadQuery();
              },
            ),
          ),
          if (_tab == 0) ...[
            PillTabs(
              labels: _statusFilters,
              index: _statusFilters.indexOf(_status),
              padding: const EdgeInsets.symmetric(horizontal: 20),
              onChanged: (index) {
                setState(() => _status = _statusFilters[index]);
                _loadExams();
              },
            ),
            const SizedBox(height: 10),
          ] else ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  Expanded(
                    child: GestureDetector(
                      onTap: _pickExam,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppColors.border),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.assignment_outlined, size: 17, color: AppColors.muted),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                _selectedExam?.name ?? '请选择考试',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 13.5, color: AppColors.text),
                              ),
                            ),
                            const Icon(Icons.expand_more_rounded, size: 18, color: AppColors.muted),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: SearchBarField(
                controller: _searchController,
                hint: '搜索学员姓名',
                onSubmitted: (value) {
                  _keyword = value.trim();
                  _loadQuery();
                },
                onChanged: (value) {
                  _debounce?.cancel();
                  _debounce = Timer(const Duration(milliseconds: 400), () {
                    _keyword = value.trim();
                    _loadQuery();
                  });
                },
              ),
            ),
            const SizedBox(height: 10),
          ],
          Expanded(child: _tab == 0 ? _buildExams() : _buildQuery()),
        ],
      ),
    );
  }

  Widget _buildExams() {
    if (_loadingExams) return const LoadingView();
    if (_examError != null) return ErrorView(_examError!, onRetry: _loadExams);
    return RefreshIndicator(
      onRefresh: _loadExams,
      child: _exams.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [EmptyState(title: '暂无考试', subtitle: '当前筛选条件下没有考试记录，可在管理端创建考试')],
            )
          : ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
              itemCount: _exams.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final exam = _exams[index];
                return SurfaceCard(
                  padding: const EdgeInsets.all(15),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => GradeAnalysisPage(exam: exam)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              exam.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                            ),
                          ),
                          const SizedBox(width: 8),
                          StatusTag(exam.status, compact: true),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        [
                          exam.type,
                          exam.courseName ?? '',
                          dateOnly(exam.examDate),
                        ].where((e) => e.isNotEmpty).join(' · '),
                        style: const TextStyle(fontSize: 12, color: AppColors.muted),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Text(
                            '录入 ${exam.enteredCount}/${exam.studentCount} 人',
                            style: const TextStyle(fontSize: 12, color: AppColors.sub),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: MiniBar(
                              value: exam.studentCount == 0 ? 0 : exam.enteredCount / exam.studentCount,
                              height: 4,
                            ),
                          ),
                          const SizedBox(width: 10),
                          Text('满分 ${countText(exam.fullScore)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                        ],
                      ),
                      if (exam.classNames.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Text(
                          exam.classNames.join('、'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                        ),
                      ],
                    ],
                  ),
                );
              },
            ),
    );
  }

  Widget _buildQuery() {
    if (_selectedExam == null && !_loadingExams) {
      return const EmptyState(title: '暂无可查询的考试', subtitle: '请先在管理端创建考试并录入成绩');
    }
    if (_loadingQuery) return const LoadingView();
    if (_queryError != null) return ErrorView(_queryError!, onRetry: _loadQuery);
    return RefreshIndicator(
      onRefresh: _loadQuery,
      child: _queryRows.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [EmptyState(title: '暂无成绩记录', subtitle: '该考试尚未录入成绩或成绩尚未发布')],
            )
          : ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
              itemCount: _queryRows.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final row = _queryRows[index];
                return SurfaceCard(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 26,
                        child: Text(
                          '${index + 1}',
                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Text(
                                  row.studentName,
                                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                                ),
                                const SizedBox(width: 8),
                                StatusTag(row.status, compact: true),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              [
                                row.className,
                                if (row.rankClass != null) '班排 ${row.rankClass}',
                                if (row.rankCampus != null) '校排 ${row.rankCampus}',
                              ].join(' · '),
                              style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                            ),
                          ],
                        ),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            row.totalScore == null ? row.level ?? '-' : countText(row.totalScore),
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.primary, height: 1.1),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            row.totalScore == null ? '' : '满分 ${countText(row.fullScore)}',
                            style: const TextStyle(fontSize: 10.5, color: AppColors.muted),
                          ),
                        ],
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}
