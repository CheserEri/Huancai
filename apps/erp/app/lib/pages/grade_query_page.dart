import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/grade_models.dart';
import '../core/models.dart';
import '../ui/grade_ui.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/shared.dart';

/// 成绩中心 - 成绩查询：按考试、班级、类型、状态、关键词筛选成绩并导出。
class GradeQueryTab extends StatefulWidget {
  const GradeQueryTab({super.key});

  @override
  State<GradeQueryTab> createState() => _GradeQueryTabState();
}

class _GradeQueryTabState extends State<GradeQueryTab> {
  static const _types = ['全部类型', '周考', '月考', '学校考试'];
  static const _statuses = ['全部状态', '正常', '缺考', '免考'];

  final _searchController = TextEditingController();
  Timer? _debounce;

  List<GradeExam> _exams = const [];
  List<GradeClassOption> _classOptions = const [];
  List<GradeQueryRow> _rows = const [];
  bool _loading = true;
  String? _error;
  String _keyword = '';
  int? _examId;
  int? _classId;
  String _type = '全部类型';
  String _status = '全部状态';

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _init() async {
    await Future.wait([_loadOptions(), _loadRows()]);
  }

  Future<void> _loadOptions() async {
    try {
      final results = await Future.wait([
        Api.get<List<dynamic>>('/grades/exams'),
        Api.get<List<dynamic>>('/grades/exams/class-options'),
      ]);
      if (!mounted) return;
      setState(() {
        _exams = mapList(results[0]).map(GradeExam.from).toList();
        _classOptions = mapList(results[1]).map(GradeClassOption.from).toList();
      });
    } catch (_) {
      // 筛选项加载失败不影响成绩列表查询。
    }
  }

  Future<void> _loadRows() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/query', {
        if (_examId != null) 'examId': _examId,
        if (_classId != null) 'classId': _classId,
        if (_type != '全部类型') 'type': _type,
        if (_status != '全部状态') 'scoreStatus': _status,
        if (_keyword.isNotEmpty) 'keyword': _keyword,
      });
      if (!mounted) return;
      setState(() {
        _rows = mapList(data).map(GradeQueryRow.from).toList();
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
        _error = '成绩查询失败';
      });
    }
  }

  Future<void> _showFilterSheet() async {
    var examId = _examId;
    var classId = _classId;
    var type = _type;
    var status = _status;
    final applied = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setSheetState) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('筛选条件', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
                const SizedBox(height: 14),
                PickerField(
                  label: '考试',
                  value: firstOrNullWhere(_exams, (item) => item.id == examId)?.name,
                  hint: '全部考试',
                  onTap: () async {
                    final picked = await showOptionPicker<GradeExam>(
                      context: context,
                      title: '选择考试',
                      items: _exams,
                      labelOf: (item) => item.name,
                      subtitleOf: (item) => '${item.type} · ${dateOnly(item.examDate)}',
                    );
                    if (picked != null) setSheetState(() => examId = picked.id);
                  },
                ),
                const SizedBox(height: 8),
                PickerField(
                  label: '班级',
                  value: firstOrNullWhere(_classOptions, (item) => item.id == classId)?.name,
                  hint: '全部班级',
                  onTap: () async {
                    final picked = await showOptionPicker<GradeClassOption>(
                      context: context,
                      title: '选择班级',
                      items: _classOptions,
                      labelOf: (item) => item.name,
                      subtitleOf: (item) => item.courseName ?? '',
                    );
                    if (picked != null) setSheetState(() => classId = picked.id);
                  },
                ),
                if (examId != null || classId != null)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: () => setSheetState(() {
                        examId = null;
                        classId = null;
                      }),
                      child: const Text('清除考试 / 班级筛选'),
                    ),
                  ),
                const SizedBox(height: 10),
                const Text('考试类型', style: TextStyle(fontSize: 12.5, color: AppColors.muted)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final item in _types)
                      ChoiceChip(
                        label: Text(item),
                        selected: type == item,
                        onSelected: (_) => setSheetState(() => type = item),
                      ),
                  ],
                ),
                const SizedBox(height: 14),
                const Text('成绩状态', style: TextStyle(fontSize: 12.5, color: AppColors.muted)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final item in _statuses)
                      ChoiceChip(
                        label: Text(item),
                        selected: status == item,
                        onSelected: (_) => setSheetState(() => status = item),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => setSheetState(() {
                          examId = null;
                          classId = null;
                          type = '全部类型';
                          status = '全部状态';
                        }),
                        child: const Text('重置'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: () => Navigator.pop(context, true),
                        child: const Text('应用筛选'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    if (applied != true) return;
    setState(() {
      _examId = examId;
      _classId = classId;
      _type = type;
      _status = status;
    });
    _loadRows();
  }

  Future<void> _export() async {
    if (_rows.isEmpty) {
      showToast(context, '当前筛选条件下暂无可导出的成绩');
      return;
    }
    final buffer = StringBuffer('考试名称,考试类型,考试日期,班级,学员姓名,成绩,得分率,班级排名,校区排名,等级,状态,备注\n');
    for (final row in _rows) {
      final score = row.totalScore == null ? '' : scoreText(row.totalScore);
      final rate = row.totalScore == null ? '' : '${scoreText(scoreRateOf(row.totalScore, row.fullScore))}%';
      final cells = [
        row.examName,
        row.examType,
        dateOnly(row.examDate),
        row.className,
        row.studentName,
        score,
        rate,
        row.rankClass?.toString() ?? '',
        row.rankCampus?.toString() ?? '',
        row.level ?? '',
        row.status,
        row.remark ?? '',
      ];
      buffer.writeln(cells.map((cell) => '"${cell.replaceAll('"', '""')}"').join(','));
    }
    await Clipboard.setData(ClipboardData(text: buffer.toString()));
    if (mounted) showToast(context, '已导出 ${_rows.length} 条成绩，CSV 内容已复制到剪贴板');
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
          child: Row(
            children: [
              Expanded(
                child: SearchBarField(
                  controller: _searchController,
                  hint: '搜索学员姓名 / 学号',
                  onSubmitted: (value) {
                    _keyword = value.trim();
                    _loadRows();
                  },
                  onChanged: (value) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () {
                      _keyword = value.trim();
                      _loadRows();
                    });
                  },
                ),
              ),
              const SizedBox(width: 8),
              IconButton.outlined(
                onPressed: _showFilterSheet,
                icon: Badge(
                  isLabelVisible: _examId != null || _classId != null || _type != '全部类型' || _status != '全部状态',
                  child: const Icon(Icons.tune_rounded, size: 20),
                ),
                tooltip: '筛选',
              ),
              const SizedBox(width: 8),
              IconButton.outlined(
                onPressed: _export,
                icon: const Icon(Icons.file_download_outlined, size: 20),
                tooltip: '导出',
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
          child: Row(
            children: [
              Text('共 ${_rows.length} 条成绩', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
              const Spacer(),
              if (_status != '全部状态')
                StatusTag(_status, compact: true),
            ],
          ),
        ),
        Expanded(
          child: _loading
              ? const LoadingView()
              : _error != null
                  ? ErrorView(_error!, onRetry: _loadRows)
                  : RefreshIndicator(
                      onRefresh: _loadRows,
                      child: _rows.isEmpty
                          ? ListView(
                              physics: const AlwaysScrollableScrollPhysics(),
                              children: const [EmptyState(title: '暂无成绩', subtitle: '当前筛选条件下暂无成绩')],
                            )
                          : ListView.separated(
                              physics: const AlwaysScrollableScrollPhysics(),
                              padding: const EdgeInsets.fromLTRB(20, 2, 20, 28),
                              itemCount: _rows.length,
                              separatorBuilder: (_, _) => const SizedBox(height: 8),
                              itemBuilder: (context, index) => _rowCard(_rows[index]),
                            ),
                    ),
        ),
      ],
    );
  }

  Widget _rowCard(GradeQueryRow row) {
    final absent = row.status == '缺考' || row.status == '免考';
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AvatarBadge(row.studentName, size: 32),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            row.studentName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                          ),
                        ),
                        const SizedBox(width: 6),
                        StatusTag(row.status, compact: true),
                        if ((row.level ?? '').isNotEmpty) ...[
                          const SizedBox(width: 6),
                          StatusTag(row.level!, color: levelColor(row.level!), background: levelBackground(row.level!), compact: true),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${row.className} · ${row.examName}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    absent ? '-' : scoreText(row.totalScore),
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: absent ? AppColors.muted : AppColors.primary,
                    ),
                  ),
                  Text(
                    absent ? row.status : '/ ${scoreText(row.fullScore)} · ${percentText(scoreRateOf(row.totalScore, row.fullScore))}',
                    style: const TextStyle(fontSize: 10, color: AppColors.muted),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Text(
                  [row.examType, dateOnly(row.examDate)].where((item) => item.isNotEmpty).join(' · '),
                  style: const TextStyle(fontSize: 11, color: AppColors.muted),
                ),
              ),
              if (row.rankClass != null) Text('班排 ${row.rankClass}', style: const TextStyle(fontSize: 11, color: AppColors.sub)),
              if (row.rankCampus != null) ...[
                const SizedBox(width: 10),
                Text('校排 ${row.rankCampus}', style: const TextStyle(fontSize: 11, color: AppColors.sub)),
              ],
            ],
          ),
          if ((row.remark ?? '').isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              row.remark!,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11, color: AppColors.muted, height: 1.5),
            ),
          ],
        ],
      ),
    );
  }
}
