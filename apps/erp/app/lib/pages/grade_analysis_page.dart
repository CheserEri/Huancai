import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/grade_models.dart';
import '../core/models.dart';
import '../ui/grade_ui.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/shared.dart';

/// 成绩中心 - 成绩分析：单场考试横向对比、班级考试趋势、学员成绩波动。
class GradeAnalysisTab extends StatefulWidget {
  const GradeAnalysisTab({super.key, this.requestedExamId});

  final int? requestedExamId;

  @override
  State<GradeAnalysisTab> createState() => _GradeAnalysisTabState();
}

class _GradeAnalysisTabState extends State<GradeAnalysisTab> {
  static const _tabs = ['单场考试分析', '班级考试趋势', '学员成绩波动'];

  int _tab = 0;

  List<GradeExam> _exams = const [];
  List<GradeClassOption> _classOptions = const [];
  bool _optionsLoading = true;
  String? _optionsError;

  GradeExam? _exam;
  GradeAnalysis? _analysis;
  bool _singleLoading = false;
  String? _singleError;

  int? _classId;
  int? _classLoadedFor;
  List<GradeClassExam> _classExams = const [];
  bool _classLoading = false;
  String? _classError;

  int? _studentClassId;
  int? _studentsLoadedFor;
  List<ClassStudent> _students = const [];
  ClassStudent? _student;
  int? _studentLoadedFor;
  List<GradeStudentScore> _studentScores = const [];
  bool _studentLoading = false;
  String? _studentError;

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void didUpdateWidget(covariant GradeAnalysisTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.requestedExamId != null && widget.requestedExamId != oldWidget.requestedExamId) {
      _selectExamById(widget.requestedExamId!);
    }
  }

  Future<void> _init() async {
    setState(() {
      _optionsLoading = true;
      _optionsError = null;
    });
    try {
      final results = await Future.wait([
        Api.get<List<dynamic>>('/grades/exams'),
        Api.get<List<dynamic>>('/grades/exams/class-options'),
      ]);
      if (!mounted) return;
      final exams = mapList(results[0]).map(GradeExam.from).toList();
      final options = mapList(results[1]).map(GradeClassOption.from).toList();
      setState(() {
        _exams = exams;
        _classOptions = options;
        _classId = options.isEmpty ? null : options.first.id;
        _studentClassId = options.isEmpty ? null : options.first.id;
        _optionsLoading = false;
      });
      if (exams.isEmpty) return;
      final target = firstOrNullWhere(exams, (exam) => exam.id == widget.requestedExamId) ?? exams.first;
      await _selectExam(target);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _optionsLoading = false;
        _optionsError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _optionsLoading = false;
        _optionsError = '成绩分析数据加载失败';
      });
    }
  }

  void _switchTab(int index) {
    setState(() => _tab = index);
    if (index == 1) _ensureClassTrend();
    if (index == 2) _ensureStudentTab();
  }

  Future<void> _selectExamById(int examId) async {
    final exam = firstOrNullWhere(_exams, (item) => item.id == examId);
    if (exam != null) await _selectExam(exam);
  }

  Future<void> _pickExam() async {
    if (_exams.isEmpty) return;
    final picked = await showOptionPicker<GradeExam>(
      context: context,
      title: '选择考试',
      items: _exams,
      labelOf: (exam) => exam.name,
      subtitleOf: (exam) => '${exam.type} · ${dateOnly(exam.examDate)} · ${exam.status}',
      selected: _exam,
    );
    if (picked != null) await _selectExam(picked);
  }

  Future<void> _selectExam(GradeExam exam) async {
    setState(() {
      _exam = exam;
      _analysis = null;
      _singleLoading = true;
      _singleError = null;
    });
    try {
      final data = await Api.get<Map<String, dynamic>>('/grades/analysis', {'examId': exam.id});
      if (!mounted) return;
      setState(() {
        _analysis = GradeAnalysis.from(data);
        _singleLoading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _singleLoading = false;
        _singleError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _singleLoading = false;
        _singleError = '成绩分析加载失败';
      });
    }
  }

  void _ensureClassTrend() {
    final classId = _classId;
    if (classId == null || _classLoading) return;
    if (_classLoadedFor == classId) return;
    _loadClassTrend(classId);
  }

  Future<void> _loadClassTrend(int classId) async {
    setState(() {
      _classId = classId;
      _classLoading = true;
      _classError = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/class/$classId');
      if (!mounted) return;
      setState(() {
        _classExams = mapList(data).map(GradeClassExam.from).toList();
        _classLoadedFor = classId;
        _classLoading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _classLoading = false;
        _classError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _classLoading = false;
        _classError = '班级考试数据加载失败';
      });
    }
  }

  Future<void> _pickTrendClass() async {
    if (_classOptions.isEmpty) return;
    final picked = await showOptionPicker<GradeClassOption>(
      context: context,
      title: '选择班级',
      items: _classOptions,
      labelOf: (item) => item.name,
      subtitleOf: (item) => item.courseName ?? '',
      selected: firstOrNullWhere(_classOptions, (item) => item.id == _classId),
    );
    if (picked != null) {
      _classLoadedFor = null;
      await _loadClassTrend(picked.id);
    }
  }

  void _ensureStudentTab() {
    final classId = _studentClassId;
    if (classId == null || _studentLoading) return;
    if (_studentsLoadedFor != classId) {
      _loadStudents(classId);
    } else if (_student != null && _studentLoadedFor != _student!.studentId) {
      _loadStudentScores(_student!);
    }
  }

  Future<void> _loadStudents(int classId, {int? preferredStudentId}) async {
    setState(() {
      _studentClassId = classId;
      _studentLoading = true;
      _studentError = null;
      _students = const [];
      _student = null;
      _studentScores = const [];
      _studentLoadedFor = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/classes/$classId/students');
      if (!mounted) return;
      final students = mapList(data).map(ClassStudent.from).where((item) => item.status == '在读').toList();
      final target = firstOrNullWhere(students, (item) => item.studentId == preferredStudentId) ??
          firstOrNullWhere(students, (item) => item.studentId == _student?.studentId) ??
          (students.isEmpty ? null : students.first);
      setState(() {
        _students = students;
        _studentsLoadedFor = classId;
        _student = target;
        _studentLoading = false;
      });
      if (target != null) await _loadStudentScores(target);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _studentLoading = false;
        _studentError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _studentLoading = false;
        _studentError = '学员名单加载失败';
      });
    }
  }

  Future<void> _loadStudentScores(ClassStudent student) async {
    setState(() {
      _student = student;
      _studentLoading = true;
      _studentError = null;
      _studentLoadedFor = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/student/${student.studentId}');
      if (!mounted) return;
      setState(() {
        _studentScores = mapList(data).map(GradeStudentScore.from).toList();
        _studentLoadedFor = student.studentId;
        _studentLoading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _studentLoading = false;
        _studentError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _studentLoading = false;
        _studentError = '学员成绩加载失败';
      });
    }
  }

  Future<void> _pickStudentClass() async {
    if (_classOptions.isEmpty) return;
    final picked = await showOptionPicker<GradeClassOption>(
      context: context,
      title: '选择班级',
      items: _classOptions,
      labelOf: (item) => item.name,
      subtitleOf: (item) => item.courseName ?? '',
      selected: firstOrNullWhere(_classOptions, (item) => item.id == _studentClassId),
    );
    if (picked != null) await _loadStudents(picked.id);
  }

  Future<void> _pickStudent() async {
    if (_students.isEmpty) return;
    final picked = await showOptionPicker<ClassStudent>(
      context: context,
      title: '选择学员',
      items: _students,
      labelOf: (item) => item.studentName,
      subtitleOf: (item) => item.remainLessons > 0 ? '剩余课时 ${scoreText(item.remainLessons)}' : '',
      selected: _student,
    );
    if (picked != null) await _loadStudentScores(picked);
  }

  @override
  Widget build(BuildContext context) {
    if (_optionsLoading) return const LoadingView();
    if (_optionsError != null) return ErrorView(_optionsError!, onRetry: _init);
    return Column(
      children: [
        PillTabs(labels: _tabs, index: _tab, onChanged: _switchTab),
        const SizedBox(height: 10),
        Expanded(
          child: switch (_tab) {
            0 => _buildSingle(),
            1 => _buildClassTrend(),
            _ => _buildStudentTrend(),
          },
        ),
      ],
    );
  }

  Widget _buildSingle() {
    return RefreshIndicator(
      onRefresh: () async {
        final exam = _exam;
        if (exam != null) await _selectExam(exam);
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 28),
        children: [
          if (_exams.isEmpty)
            const EmptyState(title: '暂无考试', subtitle: '请先在考试管理中创建考试')
          else ...[
            PickerField(
              label: '考试',
              value: _exam?.name,
              hint: '请选择考试',
              onTap: _pickExam,
            ),
            const SizedBox(height: 10),
            if (_singleLoading) const LoadingView(height: 260),
            if (!_singleLoading && _singleError != null) ErrorView(_singleError!, onRetry: () => _exam == null ? null : _selectExam(_exam!)),
            if (!_singleLoading && _singleError == null && _analysis == null)
              const EmptyState(title: '暂无成绩分析数据', subtitle: '该考试暂无可分析的成绩')
            else if (!_singleLoading && _analysis != null) ..._singleContent(_analysis!),
          ],
        ],
      ),
    );
  }

  List<Widget> _singleContent(GradeAnalysis data) {
    final status = _exam?.status ?? '草稿';
    final ranking = data.rankings;
    return [
      SurfaceCard(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    data.examName,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                  ),
                ),
                const SizedBox(width: 8),
                StatusTag(status, compact: true),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              [data.examType, dateOnly(data.examDate), '满分 ${scoreText(data.fullScore)}', data.scoringMode == '等级' ? '等级制' : '分数制']
                  .where((item) => item.isNotEmpty)
                  .join(' · '),
              style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _countChip('应考', data.studentCount, AppColors.primary),
                _countChip('已录', data.enteredCount, AppColors.success),
                _countChip('缺考', data.absentCount, AppColors.warning),
                _countChip('免考', data.exemptCount, AppColors.purple),
                _countChip('未录', data.missingCount, AppColors.muted),
              ],
            ),
          ],
        ),
      ),
      const SizedBox(height: 10),
      SurfaceCard(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('成绩指标', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: MetricTile(label: '平均分', value: scoreText(data.avgScore), color: AppColors.primary)),
                Expanded(child: MetricTile(label: '最高分', value: scoreText(data.maxScore))),
                Expanded(child: MetricTile(label: '最低分', value: scoreText(data.minScore))),
              ],
            ),
            const SizedBox(height: 14),
            _rateRow('及格率', data.passRate, AppColors.success),
            const SizedBox(height: 10),
            _rateRow('优秀率', data.excellentRate, AppColors.warning),
          ],
        ),
      ),
      const SizedBox(height: 10),
      SurfaceCard(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('分数段分布', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
            const SizedBox(height: 10),
            ..._distributionRows(data),
          ],
        ),
      ),
      if (data.levels.any((item) => (item['count'] as num? ?? 0) > 0)) ...[
        const SizedBox(height: 10),
        SurfaceCard(
          padding: const EdgeInsets.all(15),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('等级分布', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
              const SizedBox(height: 10),
              ..._levelRows(data),
            ],
          ),
        ),
      ],
      const SizedBox(height: 10),
      SurfaceCard(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('班级横向对比', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
            const SizedBox(height: 8),
            if (data.classes.isEmpty)
              const InlineHint('该考试暂无可对比的班级')
            else
              for (var i = 0; i < data.classes.length; i++) ...[
                if (i > 0) const Divider(height: 18),
                _classCompareRow(data.classes[i]),
              ],
          ],
        ),
      ),
      const SizedBox(height: 10),
      SurfaceCard(
        padding: const EdgeInsets.all(15),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text('成绩排名', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
                const Spacer(),
                Text('前 ${ranking.length} 名', style: const TextStyle(fontSize: 11, color: AppColors.muted)),
              ],
            ),
            const SizedBox(height: 8),
            if (ranking.isEmpty)
              const InlineHint('暂无有效成绩排名')
            else
              for (var i = 0; i < ranking.length; i++) ...[
                if (i > 0) const Divider(height: 16),
                _rankingRow(ranking[i]),
              ],
          ],
        ),
      ),
    ];
  }

  List<Widget> _distributionRows(GradeAnalysis data) {
    final max = data.distributions.fold<int>(
      0,
      (value, item) => (item['count'] as num? ?? 0).toInt() > value ? (item['count'] as num).toInt() : value,
    );
    return [
      for (final item in data.distributions)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(
            children: [
              SizedBox(
                width: 58,
                child: Text('${item['label'] ?? '-'}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
              ),
              Expanded(
                child: MiniBar(
                  value: max == 0 ? 0 : (item['count'] as num? ?? 0).toDouble() / max,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                width: 26,
                child: Text(
                  '${item['count'] ?? 0}',
                  textAlign: TextAlign.right,
                  style: const TextStyle(fontSize: 11.5, color: AppColors.sub),
                ),
              ),
            ],
          ),
        ),
    ];
  }

  List<Widget> _levelRows(GradeAnalysis data) {
    final max = data.levels.fold<int>(
      0,
      (value, item) => (item['count'] as num? ?? 0).toInt() > value ? (item['count'] as num).toInt() : value,
    );
    return [
      for (final item in data.levels)
        if ((item['count'] as num? ?? 0) > 0)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              children: [
                SizedBox(
                  width: 62,
                  child: StatusTag(
                    '${item['level'] ?? '-'}',
                    color: levelColor('${item['level']}'),
                    background: levelBackground('${item['level']}'),
                    compact: true,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: MiniBar(
                    value: max == 0 ? 0 : (item['count'] as num? ?? 0).toDouble() / max,
                    color: levelColor('${item['level']}'),
                  ),
                ),
                const SizedBox(width: 8),
                Text('${item['count'] ?? 0} 人', style: const TextStyle(fontSize: 11.5, color: AppColors.sub)),
              ],
            ),
          ),
    ];
  }

  Widget _classCompareRow(Map<String, dynamic> item) {
    final count = intOf(item['count']);
    final avg = numOrNull(item['avgScore']);
    final pass = numOrNull(item['passRate']);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                '${item['className'] ?? '-'}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.text),
              ),
            ),
            Text('$count 人', style: const TextStyle(fontSize: 11, color: AppColors.muted)),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          '平均 ${scoreText(avg)} · 最高 ${scoreText(numOrNull(item['maxScore']))} · 最低 ${scoreText(numOrNull(item['minScore']))} · 及格 ${percentText(pass)} · 优秀 ${percentText(numOrNull(item['excellentRate']))}',
          style: const TextStyle(fontSize: 11, color: AppColors.sub, height: 1.6),
        ),
      ],
    );
  }

  Widget _rankingRow(Map<String, dynamic> item) {
    final rank = intOf(item['rank']);
    final top = rank <= 3;
    return Row(
      children: [
        Container(
          width: 24,
          height: 24,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: top ? AppColors.primarySoft : const Color(0xFFF3F4F6),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            '$rank',
            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: top ? AppColors.primary : AppColors.sub),
          ),
        ),
        const SizedBox(width: 9),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${item['studentName'] ?? '-'}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: AppColors.text),
              ),
              Text('${item['className'] ?? '-'}', style: const TextStyle(fontSize: 10.5, color: AppColors.muted)),
            ],
          ),
        ),
        if ((item['level'] ?? '').toString().isNotEmpty)
          StatusTag(
            '${item['level']}',
            color: levelColor('${item['level']}'),
            background: levelBackground('${item['level']}'),
            compact: true,
          ),
        const SizedBox(width: 8),
        Text(
          scoreText(numOrNull(item['totalScore'])),
          style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: AppColors.primary),
        ),
      ],
    );
  }

  Widget _buildClassTrend() {
    final chronological = _classExams.reversed.toList();
    final labeled = chronological.where((item) => item.avgScore != null).toList();
    final rates = labeled.map((item) => item.scoreRate ?? 0).toList();
    final passRates = labeled.map((item) => item.passRate?.toDouble() ?? 0).toList();
    final avgRate = rates.isEmpty ? null : rates.reduce((a, b) => a + b) / rates.length;
    final avgPass = passRates.isEmpty ? null : passRates.reduce((a, b) => a + b) / passRates.length;
    final deltaById = <int, double?>{};
    for (var i = 0; i < chronological.length; i++) {
      final current = chronological[i].scoreRate;
      final previous = i == 0 ? null : chronological[i - 1].scoreRate;
      deltaById[chronological[i].examId] = current == null || previous == null ? null : current - previous;
    }
    return RefreshIndicator(
      onRefresh: () async {
        final classId = _classId;
        if (classId != null) await _loadClassTrend(classId);
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 28),
        children: [
          if (_classOptions.isEmpty)
            const EmptyState(title: '暂无可选班级', subtitle: '请先在班级管理中创建班级')
          else ...[
            PickerField(
              label: '班级',
              value: firstOrNullWhere(_classOptions, (item) => item.id == _classId)?.name,
              hint: '请选择班级',
              onTap: _pickTrendClass,
            ),
            const SizedBox(height: 10),
            if (_classLoading) const LoadingView(height: 260),
            if (!_classLoading && _classError != null) ErrorView(_classError!, onRetry: () => _classId == null ? null : _loadClassTrend(_classId!)),
            if (!_classLoading && _classError == null && _classExams.isEmpty)
              const EmptyState(title: '暂无考试记录', subtitle: '该班级暂无已关联的考试')
            else if (!_classLoading && _classExams.isNotEmpty) ...[
              SurfaceCard(
                padding: const EdgeInsets.all(15),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text('班级整体表现', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
                        const Spacer(),
                        Text('共 ${_classExams.length} 场 · 已出分 ${labeled.length} 场', style: const TextStyle(fontSize: 11, color: AppColors.muted)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(child: MetricTile(label: '平均得分率', value: percentText(avgRate), color: AppColors.primary)),
                        Expanded(child: MetricTile(label: '平均及格率', value: percentText(avgPass), color: AppColors.success)),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              SurfaceCard(
                padding: const EdgeInsets.all(15),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('得分趋势', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
                    const SizedBox(height: 6),
                    if (labeled.isEmpty)
                      const InlineHint('暂无可用于趋势分析的已出分考试')
                    else
                      GradeTrendChart(
                        labels: labeled.map((item) => _shortDate(item.examDate)).toList(),
                        series: [
                          GradeTrendSeries(
                            name: '平均得分率',
                            color: AppColors.primary,
                            values: labeled.map((item) => item.scoreRate).toList(),
                          ),
                          GradeTrendSeries(
                            name: '及格率',
                            color: AppColors.success,
                            values: labeled.map((item) => item.passRate?.toDouble()).toList(),
                          ),
                        ],
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              for (final exam in _classExams) ...[
                _classExamCard(exam, deltaById[exam.examId]),
                const SizedBox(height: 8),
              ],
            ],
          ],
        ],
      ),
    );
  }

  Widget _classExamCard(GradeClassExam exam, double? delta) {
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  exam.examName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
              ),
              const SizedBox(width: 8),
              StatusTag(exam.status, compact: true),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '${exam.examType} · ${dateOnly(exam.examDate)} · 已录 ${exam.enteredCount}/${exam.studentCount}',
            style: const TextStyle(fontSize: 11, color: AppColors.muted),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: MetricTile(label: '平均分', value: scoreText(exam.avgScore), color: AppColors.primary)),
              Expanded(child: MetricTile(label: '得分率', value: percentText(exam.scoreRate))),
              Expanded(child: MetricTile(label: '及格率', value: percentText(exam.passRate), color: AppColors.success)),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const Text('较上场', style: TextStyle(fontSize: 10.5, color: AppColors.muted)),
                  const SizedBox(height: 2),
                  Text(
                    delta == null ? '-' : '${delta >= 0 ? '+' : ''}${scoreText(delta)}pp',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: delta == null ? AppColors.muted : (delta >= 0 ? AppColors.success : AppColors.danger),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStudentTrend() {
    final chronological = _studentScores.reversed.toList();
    final latest = _studentScores.isEmpty ? null : _studentScores.first;
    final rates = latest == null ? <double?>[] : chronological.map((item) => item.scoreRate).toList();
    final classRates = latest == null ? <double?>[] : chronological.map((item) => item.classRate).toList();
    final validRates = rates.whereType<double>().toList();
    final avgRate = validRates.isEmpty ? null : validRates.reduce((a, b) => a + b) / validRates.length;
    final gap = latest == null
        ? null
        : (latest.scoreRate != null && latest.classRate != null ? latest.scoreRate! - latest.classRate! : null);
    return RefreshIndicator(
      onRefresh: () async {
        final student = _student;
        if (student != null) await _loadStudentScores(student);
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 28),
        children: [
          if (_classOptions.isEmpty)
            const EmptyState(title: '暂无可选班级', subtitle: '请先在班级管理中创建班级')
          else ...[
            PickerField(
              label: '班级',
              value: firstOrNullWhere(_classOptions, (item) => item.id == _studentClassId)?.name,
              hint: '请选择班级',
              onTap: _pickStudentClass,
            ),
            const SizedBox(height: 8),
            PickerField(
              label: '学员',
              value: _student?.studentName,
              hint: _students.isEmpty ? '该班级暂无在读学员' : '请选择学员',
              onTap: _students.isEmpty ? () {} : _pickStudent,
              enabled: _students.isNotEmpty,
            ),
            const SizedBox(height: 10),
            if (_studentLoading) const LoadingView(height: 260),
            if (!_studentLoading && _studentError != null) ErrorView(_studentError!, onRetry: _ensureStudentTab),
            if (!_studentLoading && _studentError == null && _studentScores.isEmpty && _students.isEmpty)
              const EmptyState(title: '暂无学员', subtitle: '该班级暂无在读学员')
            else if (!_studentLoading && _studentError == null && _studentScores.isEmpty && _student != null)
              const EmptyState(title: '暂无成绩记录', subtitle: '该学员暂无已发布的考试成绩'),
            if (!_studentLoading && _studentScores.isNotEmpty) ...[
              SurfaceCard(
                padding: const EdgeInsets.all(15),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text('成绩波动概览', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
                        const Spacer(),
                        Text('共 ${_studentScores.length} 场已发布考试', style: const TextStyle(fontSize: 11, color: AppColors.muted)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(child: MetricTile(label: '平均得分率', value: percentText(avgRate), color: AppColors.primary)),
                        Expanded(
                          child: MetricTile(
                            label: '最新得分率',
                            value: percentText(latest?.scoreRate),
                            color: AppColors.success,
                          ),
                        ),
                        Expanded(
                          child: MetricTile(
                            label: '较班级平均',
                            value: gap == null ? '-' : '${gap >= 0 ? '+' : ''}${scoreText(gap)}pp',
                            color: gap == null ? AppColors.text : (gap >= 0 ? AppColors.success : AppColors.danger),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              SurfaceCard(
                padding: const EdgeInsets.all(15),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('得分率走势', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
                    const SizedBox(height: 6),
                    GradeTrendChart(
                      labels: chronological.map((item) => _shortDate(item.examDate)).toList(),
                      series: [
                        GradeTrendSeries(name: '学员得分率', color: AppColors.primary, values: rates),
                        GradeTrendSeries(name: '班级平均得分率', color: AppColors.sub, values: classRates),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              for (final item in _studentScores) ...[
                _studentScoreCard(item),
                const SizedBox(height: 8),
              ],
            ],
          ],
        ],
      ),
    );
  }

  Widget _studentScoreCard(GradeStudentScore item) {
    final absent = item.status == '缺考' || item.status == '免考';
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  item.examName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
              ),
              const SizedBox(width: 8),
              StatusTag(item.status, compact: true),
              if ((item.level ?? '').isNotEmpty) ...[
                const SizedBox(width: 6),
                StatusTag(item.level!, color: levelColor(item.level!), background: levelBackground(item.level!), compact: true),
              ],
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '${item.examType} · ${dateOnly(item.examDate)} · ${item.className ?? ''}',
            style: const TextStyle(fontSize: 11, color: AppColors.muted),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      absent ? '-' : '${scoreText(item.totalScore)} / ${scoreText(item.fullScore)}',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: absent ? AppColors.muted : AppColors.primary,
                      ),
                    ),
                    Text(
                      absent ? item.status : '得分率 ${percentText(item.scoreRate)} · 班级平均 ${scoreText(item.classAvgScore)}',
                      style: const TextStyle(fontSize: 11, color: AppColors.muted),
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  if (item.rankClass != null) Text('班排 ${item.rankClass}', style: const TextStyle(fontSize: 11.5, color: AppColors.sub)),
                  if (item.rankCampus != null) Text('校排 ${item.rankCampus}', style: const TextStyle(fontSize: 11.5, color: AppColors.sub)),
                ],
              ),
            ],
          ),
          if ((item.remark ?? '').isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              item.remark!,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11, color: AppColors.muted, height: 1.5),
            ),
          ],
        ],
      ),
    );
  }

  Widget _countChip(String label, int count, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(9)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label, style: TextStyle(fontSize: 11, color: color)),
          const SizedBox(width: 5),
          Text('$count', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: color)),
        ],
      ),
    );
  }

  Widget _rateRow(String label, num? rate, Color color) {
    final value = (rate ?? 0).toDouble();
    return Row(
      children: [
        SizedBox(
          width: 54,
          child: Text(label, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
        ),
        Expanded(child: MiniBar(value: value / 100, color: color, height: 5)),
        const SizedBox(width: 8),
        SizedBox(
          width: 44,
          child: Text(
            percentText(rate),
            textAlign: TextAlign.right,
            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: color),
          ),
        ),
      ],
    );
  }

  String _shortDate(String? date) {
    if (date == null || date.length < 10) return date ?? '-';
    return date.substring(5, 10);
  }
}
