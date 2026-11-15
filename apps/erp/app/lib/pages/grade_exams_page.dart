import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/grade_models.dart';
import '../core/models.dart';
import '../ui/grade_ui.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/shared.dart';

/// 成绩中心 - 考试管理：新建、编辑、复制、发布、解锁、归档、删除考试。
class GradeExamsTab extends StatefulWidget {
  const GradeExamsTab({super.key, required this.onEntry, required this.onAnalyze});

  final ValueChanged<GradeExam> onEntry;
  final ValueChanged<GradeExam> onAnalyze;

  @override
  State<GradeExamsTab> createState() => _GradeExamsTabState();
}

class _GradeExamsTabState extends State<GradeExamsTab> {
  static const _statusFilters = ['全部', '草稿', '已发布', '已归档'];
  static const _typeFilters = ['全部类型', '周考', '月考', '学校考试'];

  final _searchController = TextEditingController();
  Timer? _debounce;

  bool _loading = true;
  String? _error;
  List<GradeExam> _exams = const [];
  List<GradeClassOption> _classOptions = const [];
  String _status = '全部';
  String _type = '全部类型';
  int? _classId;
  String _keyword = '';
  bool _acting = false;

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
    await Future.wait([_loadClassOptions(), _loadExams()]);
  }

  Future<void> _loadClassOptions() async {
    try {
      final data = await Api.get<List<dynamic>>('/grades/exams/class-options');
      if (!mounted) return;
      setState(() => _classOptions = mapList(data).map(GradeClassOption.from).toList());
    } catch (_) {
      // 班级选项仅用于筛选与新建考试，加载失败时不影响考试列表展示。
    }
  }

  Future<void> _loadExams() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/grades/exams', {
        if (_keyword.isNotEmpty) 'keyword': _keyword,
        if (_type != '全部类型') 'type': _type,
        if (_status != '全部') 'status': _status,
        if (_classId != null) 'classId': _classId,
      });
      if (!mounted) return;
      setState(() {
        _exams = mapList(data).map(GradeExam.from).toList();
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
        _error = '考试数据加载失败';
      });
    }
  }

  Future<void> _showFilterSheet() async {
    var type = _type;
    var classId = _classId;
    final result = await showModalBottomSheet<bool>(
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
                const Text('筛选考试', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
                const SizedBox(height: 16),
                const Text('考试类型', style: TextStyle(fontSize: 12.5, color: AppColors.muted)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final item in _typeFilters)
                      ChoiceChip(
                        label: Text(item),
                        selected: type == item,
                        onSelected: (_) => setSheetState(() => type = item),
                      ),
                  ],
                ),
                const SizedBox(height: 16),
                const Text('适用班级', style: TextStyle(fontSize: 12.5, color: AppColors.muted)),
                const SizedBox(height: 8),
                PickerField(
                  label: '班级',
                  value: _classId == null ? null : _classNameOf(_classId!),
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
                if (classId != null)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: () => setSheetState(() => classId = null),
                      child: const Text('清除班级筛选'),
                    ),
                  ),
                const SizedBox(height: 20),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () {
                          setSheetState(() {
                            type = '全部类型';
                            classId = null;
                          });
                        },
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
    if (result != true) return;
    setState(() {
      _type = type;
      _classId = classId;
    });
    _loadExams();
  }

  Future<void> _openForm([GradeExam? exam]) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => GradeExamFormPage(exam: exam, classOptions: _classOptions),
      ),
    );
    if (changed == true) {
      await _loadExams();
      if (mounted) showToast(context, exam == null ? '考试已创建' : '考试已保存');
    }
  }

  Future<void> _action(GradeExam exam, String action) async {
    if (_acting) return;
    final Map<String, String> titles = {
      'publish': '发布考试',
      'unlock': '解锁考试',
      'archive': '归档考试',
      'copy': '复制考试',
      'delete': '删除考试',
    };
    final Map<String, String> contents = {
      'publish': '发布后将锁定成绩并计算班级、校区排名。确认发布「${exam.name}」？',
      'unlock': '解锁后考试回到草稿状态，可继续修改成绩，并会在备注中记录解锁人。确认解锁？',
      'archive': '归档后考试与成绩将变为只读状态，不能再录入。确认归档「${exam.name}」？',
      'copy': '将复制「${exam.name}」的班级与分项配置（不含成绩），生成一场新的草稿考试。',
      'delete': '删除后不可恢复，仅草稿状态的考试可以删除。确认删除「${exam.name}」？',
    };
    final confirmed = await confirmDialog(context, titles[action] ?? '操作确认', contents[action] ?? '确认继续？');
    if (!confirmed || !mounted) return;
    setState(() => _acting = true);
    try {
      if (action == 'delete') {
        await Api.delete<void>('/grades/exams/${exam.id}');
      } else {
        await Api.post<Map<String, dynamic>>('/grades/exams/${exam.id}/$action');
      }
      if (!mounted) return;
      final messages = {
        'publish': '考试已发布',
        'unlock': '考试已解锁',
        'archive': '考试已归档',
        'copy': '考试已复制',
        'delete': '考试已删除',
      };
      showToast(context, messages[action] ?? '操作成功');
      await _loadExams();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    } catch (_) {
      if (mounted) showToast(context, '操作失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _acting = false);
    }
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
                  hint: '搜索考试名称',
                  onSubmitted: (value) {
                    _keyword = value.trim();
                    _loadExams();
                  },
                  onChanged: (value) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () {
                      _keyword = value.trim();
                      _loadExams();
                    });
                  },
                ),
              ),
              const SizedBox(width: 8),
              IconButton.outlined(
                onPressed: _showFilterSheet,
                icon: Badge(
                  isLabelVisible: _type != '全部类型' || _classId != null,
                  child: const Icon(Icons.tune_rounded, size: 20),
                ),
                tooltip: '筛选',
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                onPressed: () => _openForm(),
                icon: const Icon(Icons.add_rounded, size: 20),
                tooltip: '新建考试',
              ),
            ],
          ),
        ),
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
        Expanded(child: _buildList()),
      ],
    );
  }

  Widget _buildList() {
    if (_loading) return const LoadingView();
    if (_error != null) return ErrorView(_error!, onRetry: _loadExams);
    return RefreshIndicator(
      onRefresh: _loadExams,
      child: _exams.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [EmptyState(title: '暂无考试', subtitle: '当前筛选条件下暂无考试')],
            )
          : ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 2, 20, 28),
              itemCount: _exams.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (context, index) => _examCard(_exams[index]),
            ),
    );
  }

  Widget _examCard(GradeExam exam) {
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(15, 14, 10, 8),
      onTap: () => widget.onAnalyze(exam),
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
            [exam.type, exam.courseName ?? '', dateOnly(exam.examDate), exam.creator ?? ''].where((e) => e.isNotEmpty).join(' · '),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 12, color: AppColors.muted),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              GradeProgressTag(entered: exam.enteredCount, total: exam.studentCount),
              const SizedBox(width: 10),
              Expanded(
                child: MiniBar(
                  value: exam.studentCount == 0 ? 0 : exam.enteredCount / exam.studentCount,
                  height: 4,
                ),
              ),
              const SizedBox(width: 10),
              Text('满分 ${scoreText(exam.fullScore)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
            ],
          ),
          if (exam.classNames.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              _classNamesText(exam.classNames),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
            ),
          ],
          if ((exam.remark ?? '').isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              exam.remark!,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11, color: AppColors.muted, height: 1.5),
            ),
          ],
          const SizedBox(height: 6),
          Row(
            children: [
              TextButton.icon(
                onPressed: () => widget.onEntry(exam),
                icon: const Icon(Icons.edit_note_rounded, size: 18),
                label: Text(exam.editable ? '录入成绩' : '查看成绩'),
                style: TextButton.styleFrom(minimumSize: const Size(0, 34), padding: const EdgeInsets.symmetric(horizontal: 8)),
              ),
              const Spacer(),
              PopupMenuButton<String>(
                enabled: !_acting,
                tooltip: '更多操作',
                icon: const Icon(Icons.more_horiz_rounded, size: 20, color: AppColors.sub),
                onSelected: (value) {
                  if (value == 'edit') {
                    _openForm(exam);
                  } else {
                    _action(exam, value);
                  }
                },
                itemBuilder: (context) => [
                  if (exam.editable) const PopupMenuItem(value: 'edit', child: Text('编辑考试')),
                  if (exam.status == '草稿') const PopupMenuItem(value: 'publish', child: Text('发布考试')),
                  if (exam.status == '已发布') const PopupMenuItem(value: 'unlock', child: Text('解锁修改')),
                  if (exam.status == '已发布') const PopupMenuItem(value: 'archive', child: Text('归档考试')),
                  const PopupMenuItem(value: 'copy', child: Text('复制考试')),
                  if (exam.editable) const PopupMenuItem(value: 'delete', child: Text('删除考试')),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _classNamesText(List<String> names) {
    if (names.length <= 3) return names.join('、');
    return '${names.take(3).join('、')} 等 ${names.length} 个班级';
  }

  String _classNameOf(int classId) {
    for (final option in _classOptions) {
      if (option.id == classId) return option.name;
    }
    return '班级#$classId';
  }
}

/// 新建 / 编辑考试。
class GradeExamFormPage extends StatefulWidget {
  const GradeExamFormPage({super.key, this.exam, required this.classOptions});

  final GradeExam? exam;
  final List<GradeClassOption> classOptions;

  @override
  State<GradeExamFormPage> createState() => _GradeExamFormPageState();
}

class _ExamPartDraft {
  _ExamPartDraft({String name = '', num? weight, num? fullScore})
      : nameController = TextEditingController(text: name),
        weightController = TextEditingController(text: weight == null ? '' : scoreText(weight)),
        fullScoreController = TextEditingController(text: fullScore == null ? '' : scoreText(fullScore));

  final TextEditingController nameController;
  final TextEditingController weightController;
  final TextEditingController fullScoreController;

  void dispose() {
    nameController.dispose();
    weightController.dispose();
    fullScoreController.dispose();
  }
}

class _GradeExamFormPageState extends State<GradeExamFormPage> {
  static const _types = ['周考', '月考', '学校考试'];

  final _nameController = TextEditingController();
  final _fullScoreController = TextEditingController(text: '100');
  final _remarkController = TextEditingController();

  String _type = '周考';
  String _scoringMode = '分数';
  String _examDate = todayText();
  int? _courseId;
  Set<int> _classIds = {};
  List<_ExamPartDraft> _parts = [];
  List<GradePartTemplateInfo> _templates = const [];
  bool _submitting = false;

  bool get _schoolExam => _type == '学校考试';

  @override
  void initState() {
    super.initState();
    final exam = widget.exam;
    if (exam != null) {
      _nameController.text = exam.name;
      _fullScoreController.text = scoreText(exam.fullScore);
      _remarkController.text = exam.remark ?? '';
      _type = _types.contains(exam.type) ? exam.type : '周考';
      _scoringMode = exam.scoringMode == '等级' ? '等级' : '分数';
      _examDate = dateOnly(exam.examDate);
      _courseId = exam.courseId;
      _classIds = exam.classIds.toSet();
      _parts = exam.parts
          .map((part) => _ExamPartDraft(name: part.name, weight: part.weight, fullScore: part.fullScore))
          .toList();
    }
    _loadTemplates();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _fullScoreController.dispose();
    _remarkController.dispose();
    for (final part in _parts) {
      part.dispose();
    }
    super.dispose();
  }

  Future<void> _loadTemplates() async {
    try {
      final data = await Api.get<List<dynamic>>('/grades/part-templates');
      if (!mounted) return;
      setState(() => _templates = mapList(data).map(GradePartTemplateInfo.from).toList());
    } catch (_) {
      // 模板为可选辅助，加载失败时仍可手动配置分项。
    }
  }

  List<MapEntry<int, String>> get _courses {
    final map = <int, String>{};
    for (final option in widget.classOptions) {
      if (option.courseId != null && (option.courseName ?? '').isNotEmpty) {
        map[option.courseId!] = option.courseName!;
      }
    }
    final list = map.entries.toList()..sort((a, b) => a.value.compareTo(b.value));
    return list;
  }

  List<GradeClassOption> get _courseClasses => widget.classOptions.where((item) => item.courseId == _courseId).toList();

  List<MapEntry<String, List<GradeClassOption>>> get _groupedClasses {
    final groups = <String, List<GradeClassOption>>{};
    for (final option in widget.classOptions) {
      final key = (option.courseName ?? '').isEmpty ? '未关联课程' : option.courseName!;
      groups.putIfAbsent(key, () => []).add(option);
    }
    final list = groups.entries.toList()..sort((a, b) => a.key.compareTo(b.key));
    return list;
  }

  void _changeType(String type) {
    setState(() {
      _type = type;
      if (_schoolExam) {
        _courseId = null;
        _classIds = {};
      } else {
        _classIds = _courseClasses.map((item) => item.id).toSet();
      }
    });
  }

  void _pickCourse() {
    final courses = _courses;
    if (courses.isEmpty) {
      showToast(context, '暂无可用课程，请先在课程模板中维护课程与班级');
      return;
    }
    showOptionPicker<int>(
      context: context,
      title: '选择课程',
      items: courses.map((entry) => entry.key).toList(),
      labelOf: (id) => courses.firstWhere((entry) => entry.key == id).value,
      selected: _courseId,
    ).then((picked) {
      if (picked == null) return;
      setState(() {
        _courseId = picked;
        _classIds = _courseClasses.map((item) => item.id).toSet();
      });
    });
  }

  Future<void> _pickDate() async {
    final initial = parseDate(_examDate) ?? DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
    );
    if (picked != null) setState(() => _examDate = dateText(picked));
  }

  void _addPart() {
    setState(() => _parts.add(_ExamPartDraft()));
  }

  Future<void> _importTemplate() async {
    if (_templates.isEmpty) {
      showToast(context, '暂无分项模板，可在成绩设置中维护');
      return;
    }
    final picked = await showOptionPicker<GradePartTemplateInfo>(
      context: context,
      title: '导入分项模板',
      items: _templates,
      labelOf: (item) => item.name,
      subtitleOf: (item) => item.courseCategory ?? '',
    );
    if (picked == null) return;
    for (final part in _parts) {
      part.dispose();
    }
    setState(() {
      _parts = picked.parts
          .map((part) => _ExamPartDraft(
                name: part.name,
                weight: templatePartWeight(picked.parts, part),
                fullScore: part.fullScore,
              ))
          .toList();
    });
  }

  void _removePart(int index) {
    final part = _parts[index];
    setState(() => _parts.removeAt(index));
    part.dispose();
  }

  double get _weightTotal => _parts.fold<double>(0, (sum, part) => sum + (double.tryParse(part.weightController.text.trim()) ?? 0));

  Future<void> _submit() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      showToast(context, '请填写考试名称');
      return;
    }
    final fullScore = double.tryParse(_fullScoreController.text.trim());
    if (fullScore == null || fullScore <= 0) {
      showToast(context, '满分必须大于 0');
      return;
    }
    if (!_schoolExam && _courseId == null) {
      showToast(context, '请选择课程，将自动带出该课程下全部班级');
      return;
    }
    if (_classIds.isEmpty) {
      showToast(context, '请选择适用班级');
      return;
    }
    if (_parts.isNotEmpty) {
      final missingName = _parts.any((part) => part.nameController.text.trim().isEmpty);
      if (missingName) {
        showToast(context, '请填写分项名称');
        return;
      }
      if ((_weightTotal - 100).abs() > 0.01) {
        showToast(context, '分项权重合计应为 100%，当前为 ${scoreText(_weightTotal)}%');
        return;
      }
    }
    final parts = <Map<String, dynamic>>[];
    for (var i = 0; i < _parts.length; i++) {
      parts.add({
        'name': _parts[i].nameController.text.trim(),
        'weight': double.tryParse(_parts[i].weightController.text.trim()) ?? 0,
        'fullScore': double.tryParse(_parts[i].fullScoreController.text.trim()) ?? 0,
        'sort': i,
      });
    }
    final selectedCourse = firstOrNullWhere(_courses, (entry) => entry.key == _courseId);
    final body = {
      'name': name,
      'type': _type,
      'courseId': _schoolExam ? null : _courseId,
      'courseName': _schoolExam ? null : selectedCourse?.value,
      'examDate': _examDate,
      'fullScore': fullScore,
      'scoringMode': _scoringMode,
      'remark': _remarkController.text.trim(),
      'classIds': _classIds.toList(),
      'parts': parts,
    };
    setState(() => _submitting = true);
    try {
      final exam = widget.exam;
      if (exam == null) {
        await Api.post<Map<String, dynamic>>('/grades/exams', body);
      } else {
        await Api.put<Map<String, dynamic>>('/grades/exams/${exam.id}', body);
      }
      if (!mounted) return;
      Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    } catch (_) {
      if (mounted) showToast(context, '保存失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.exam == null ? '新建考试' : '编辑考试')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 120),
        children: [
          const SectionTitle('基本信息', padding: EdgeInsets.fromLTRB(0, 4, 0, 8)),
          SurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const _FieldLabel('考试名称', required: true),
                TextField(
                  controller: _nameController,
                  maxLength: 64,
                  decoration: const InputDecoration(hintText: '例如：三年级语文第一次月考', counterText: ''),
                ),
                const SizedBox(height: 14),
                const _FieldLabel('考试类型'),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final type in _types)
                      ChoiceChip(
                        label: Text(type == '周考' || type == '月考' ? '$type（机构）' : type),
                        selected: _type == type,
                        onSelected: (_) => _changeType(type),
                      ),
                  ],
                ),
                if (!_schoolExam) ...[
                  const SizedBox(height: 14),
                  const _FieldLabel('所属课程', required: true),
                  PickerField(
                    label: '课程',
                    value: firstOrNullWhere(_courses, (entry) => entry.key == _courseId)?.value,
                    hint: '请选择课程',
                    onTap: _pickCourse,
                  ),
                  const SizedBox(height: 8),
                  const GradeHintLine('机构周考 / 月考选中课程后，该课程下全部班级自动适用。'),
                  const SizedBox(height: 10),
                  if (_courseId == null)
                    const InlineHint('选择课程后自动带出适用班级')
                  else
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final item in _courseClasses) StatusTag(item.name),
                      ],
                    ),
                ],
                const SizedBox(height: 14),
                const _FieldLabel('考试日期'),
                PickerField(label: '日期', value: _examDate, hint: '请选择日期', onTap: _pickDate),
              ],
            ),
          ),
          if (_schoolExam) ...[
            const SectionTitle('适用班级', padding: EdgeInsets.fromLTRB(0, 20, 0, 8)),
            SurfaceCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const GradeHintLine('学校考试不绑定课程，可跨课程批量选择班级。'),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      TextButton(
                        onPressed: () => setState(() => _classIds = widget.classOptions.map((item) => item.id).toSet()),
                        child: const Text('全选'),
                      ),
                      TextButton(
                        onPressed: () => setState(() => _classIds = {}),
                        child: const Text('清空'),
                      ),
                      const Spacer(),
                      Text('已选 ${_classIds.length} 个班级', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                    ],
                  ),
                  if (widget.classOptions.isEmpty)
                    const InlineHint('暂无可选班级')
                  else
                    for (final group in _groupedClasses) ...[
                      const SizedBox(height: 8),
                      Text(group.key, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.sub)),
                      const SizedBox(height: 6),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final item in group.value)
                            FilterChip(
                              label: Text(item.name),
                              selected: _classIds.contains(item.id),
                              onSelected: (selected) {
                                setState(() {
                                  if (selected) {
                                    _classIds = {..._classIds, item.id};
                                  } else {
                                    _classIds = {..._classIds}..remove(item.id);
                                  }
                                });
                              },
                            ),
                        ],
                      ),
                    ],
                ],
              ),
            ),
          ],
          const SectionTitle('计分方式', padding: EdgeInsets.fromLTRB(0, 20, 0, 8)),
          SurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(child: _FieldLabel('满分')),
                    SizedBox(
                      width: 120,
                      child: TextField(
                        controller: _fullScoreController,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        textAlign: TextAlign.right,
                        decoration: const InputDecoration(hintText: '100'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                const _FieldLabel('成绩制式'),
                Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(
                      label: const Text('分数'),
                      selected: _scoringMode == '分数',
                      onSelected: (_) => setState(() => _scoringMode = '分数'),
                    ),
                    ChoiceChip(
                      label: const Text('等级（等级 + 评语）'),
                      selected: _scoringMode == '等级',
                      onSelected: (_) => setState(() => _scoringMode = '等级'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SectionTitle('分项配置', padding: EdgeInsets.fromLTRB(0, 20, 0, 8)),
          SurfaceCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                GradeHintLine('分项可选，录入分项后自动汇总总分，权重合计需为 100%。'),
                const SizedBox(height: 8),
                Row(
                  children: [
                    TextButton.icon(
                      onPressed: _addPart,
                      icon: const Icon(Icons.add_rounded, size: 18),
                      label: const Text('添加分项'),
                    ),
                    TextButton(
                      onPressed: _importTemplate,
                      child: const Text('从模板导入'),
                    ),
                    const Spacer(),
                    if (_parts.isNotEmpty)
                      Text(
                        '权重 ${scoreText(_weightTotal)}%',
                        style: TextStyle(
                          fontSize: 11.5,
                          color: (_weightTotal - 100).abs() > 0.01 ? AppColors.danger : AppColors.success,
                        ),
                      ),
                  ],
                ),
                if (_parts.isEmpty)
                  const InlineHint('未配置分项时直接录入总分')
                else
                  for (var i = 0; i < _parts.length; i++)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 5,
                            child: TextField(
                              controller: _parts[i].nameController,
                              decoration: const InputDecoration(hintText: '分项名称，如 笔画'),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Expanded(
                            flex: 3,
                            child: TextField(
                              controller: _parts[i].weightController,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(hintText: '权重%'),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Expanded(
                            flex: 3,
                            child: TextField(
                              controller: _parts[i].fullScoreController,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(hintText: '满分'),
                            ),
                          ),
                          IconButton(
                            onPressed: () => _removePart(i),
                            icon: const Icon(Icons.close_rounded, size: 18, color: AppColors.muted),
                            visualDensity: VisualDensity.compact,
                          ),
                        ],
                      ),
                    ),
              ],
            ),
          ),
          const SectionTitle('备注', padding: EdgeInsets.fromLTRB(0, 20, 0, 8)),
          SurfaceCard(
            child: TextField(
              controller: _remarkController,
              maxLines: 3,
              maxLength: 200,
              decoration: const InputDecoration(hintText: '选填，如考试范围、注意事项', counterText: ''),
            ),
          ),
        ],
      ),
      bottomNavigationBar: SubmitBar(
        label: widget.exam == null ? '创建考试' : '保存修改',
        submitting: _submitting,
        onSubmit: _submit,
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text, {this.required = false});

  final String text;
  final bool required;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(text, style: const TextStyle(fontSize: 12.5, color: AppColors.sub, fontWeight: FontWeight.w500)),
          if (required)
            const Text(' *', style: TextStyle(fontSize: 12.5, color: AppColors.danger)),
        ],
      ),
    );
  }
}
