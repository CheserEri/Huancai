import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../core/api.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/grade_models.dart';
import '../core/models.dart';
import '../ui/grade_ui.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'grade_settings_page.dart';
import 'payment/shared.dart';

/// 成绩中心 - 成绩录入：按考试与班级录入总分 / 分项 / 等级，支持批量填充与发布。
class GradeEntryTab extends StatefulWidget {
  const GradeEntryTab({super.key, this.requestedExamId});

  /// 从考试管理跳转时指定要录入的考试。
  final int? requestedExamId;

  @override
  State<GradeEntryTab> createState() => _GradeEntryTabState();
}

class _EntryRowDraft {
  _EntryRowDraft(this.student)
      : status = student.status,
        level = student.level,
        totalController = TextEditingController(text: student.totalScore == null ? '' : scoreText(student.totalScore)),
        remarkController = TextEditingController(text: student.remark ?? ''),
        partControllers = {
          for (final entry in student.partScores.entries)
            entry.key: TextEditingController(text: entry.value == null ? '' : scoreText(entry.value)),
        };

  final GradeEntryStudent student;
  String status;
  String? level;
  final TextEditingController totalController;
  final TextEditingController remarkController;
  final Map<String, TextEditingController> partControllers;

  void dispose() {
    totalController.dispose();
    remarkController.dispose();
    for (final controller in partControllers.values) {
      controller.dispose();
    }
  }
}

class _ClassRef {
  _ClassRef(this.id, this.name);

  final int id;
  final String name;
}

class _GradeEntryTabState extends State<GradeEntryTab> {
  static const _maxImages = 9;
  static const _maxFileSize = 10 * 1024 * 1024;

  final _picker = ImagePicker();
  final _batchFillController = TextEditingController();

  List<GradeExam> _exams = const [];
  List<GradeRuleInfo> _rules = const [];
  List<String> _paperImages = const [];
  GradeExam? _exam;
  int? _classId;
  GradeEntryData? _entry;
  List<_EntryRowDraft> _rows = [];
  bool _loading = true;
  bool _loadingEntry = false;
  bool _saving = false;
  bool _uploading = false;
  String? _error;
  String? _entryError;

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void didUpdateWidget(covariant GradeEntryTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.requestedExamId != null && widget.requestedExamId != oldWidget.requestedExamId) {
      _selectExamById(widget.requestedExamId!);
    }
  }

  @override
  void dispose() {
    _batchFillController.dispose();
    _disposeRows();
    super.dispose();
  }

  void _disposeRows() {
    for (final row in _rows) {
      row.dispose();
    }
    _rows = [];
  }

  Future<void> _init() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        Api.get<List<dynamic>>('/grades/exams'),
        Api.get<List<dynamic>>('/grades/rules').catchError((_) => <dynamic>[]),
      ]);
      if (!mounted) return;
      final exams = mapList(results[0]).map(GradeExam.from).toList();
      final rules = mapList(results[1]).map(GradeRuleInfo.from).toList();
      setState(() {
        _exams = exams;
        _rules = rules;
        _loading = false;
      });
      if (exams.isEmpty) return;
      final target = firstOrNullWhere(exams, (exam) => exam.id == widget.requestedExamId) ?? exams.first;
      await _selectExam(target);
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
        _error = '成绩录入数据加载失败';
      });
    }
  }

  Future<void> _refreshExamList() async {
    try {
      final data = await Api.get<List<dynamic>>('/grades/exams');
      if (!mounted) return;
      setState(() => _exams = mapList(data).map(GradeExam.from).toList());
    } catch (_) {
      // 考试列表刷新失败不影响当前录入内容。
    }
  }

  Future<void> _selectExamById(int examId) async {
    var exam = firstOrNullWhere(_exams, (item) => item.id == examId);
    if (exam == null) {
      try {
        final data = await Api.get<Map<String, dynamic>>('/grades/exams/$examId');
        exam = GradeExam.from(data);
      } catch (_) {
        return;
      }
    }
    await _selectExam(exam);
  }

  Future<void> _selectExam(GradeExam exam) async {
    final classId = exam.classIds.isEmpty ? null : exam.classIds.first;
    setState(() {
      _exam = exam;
      _classId = classId;
      _paperImages = exam.paperImages;
      _entry = null;
      _entryError = null;
      _disposeRows();
    });
    if (classId != null) await _loadEntry();
  }

  Future<void> _selectClass(int classId) async {
    setState(() => _classId = classId);
    await _loadEntry();
  }

  List<_ClassRef> get _classes {
    final exam = _exam;
    if (exam == null) return const [];
    return [
      for (var i = 0; i < exam.classIds.length; i++)
        _ClassRef(exam.classIds[i], i < exam.classNames.length ? exam.classNames[i] : '班级#${exam.classIds[i]}'),
    ];
  }

  Future<void> _loadEntry() async {
    final exam = _exam;
    final classId = _classId;
    if (exam == null || classId == null) return;
    setState(() {
      _loadingEntry = true;
      _entryError = null;
    });
    try {
      final results = await Future.wait<dynamic>([
        Api.get<Map<String, dynamic>>('/grades/entry', {'examId': exam.id, 'classId': classId}),
        Api.get<Map<String, dynamic>>('/grades/exams/${exam.id}').catchError((_) => <String, dynamic>{}),
      ]);
      if (!mounted) return;
      final entry = GradeEntryData.from(results[0]);
      final freshExam = results[1].isEmpty ? null : GradeExam.from(results[1]);
      _disposeRows();
      setState(() {
        _entry = entry;
        if (freshExam != null) {
          _exam = freshExam;
          _paperImages = freshExam.paperImages;
          _exams = _exams.map((item) => item.id == freshExam.id ? freshExam : item).toList();
        }
        _rows = entry.students.map(_EntryRowDraft.new).toList();
        _loadingEntry = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loadingEntry = false;
        _entryError = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingEntry = false;
        _entryError = '成绩数据加载失败';
      });
    }
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

  Future<void> _pickClass() async {
    final classes = _classes;
    if (classes.isEmpty) return;
    final picked = await showOptionPicker<_ClassRef>(
      context: context,
      title: '选择班级',
      items: classes,
      labelOf: (item) => item.name,
      selected: firstOrNullWhere(classes, (item) => item.id == _classId),
    );
    if (picked != null) await _selectClass(picked.id);
  }

  List<String> get _levelOptions {
    final names = _rules.map((rule) => rule.name).where((name) => name.isNotEmpty).toList();
    return names.isEmpty ? gradeLevels : names;
  }

  Future<void> _editFullScore() async {
    final exam = _exam;
    if (exam == null || !exam.editable) {
      showToast(context, '成绩已发布，请先在考试管理中解锁后再修改满分');
      return;
    }
    final controller = TextEditingController(text: scoreText(exam.fullScore));
    final value = await showDialog<double>(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: const Text('修改满分', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
        content: TextField(
          controller: controller,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(hintText: '请输入满分'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              final parsed = double.tryParse(controller.text.trim());
              if (parsed == null || parsed <= 0) {
                showToast(context, '满分必须大于 0');
                return;
              }
              Navigator.pop(context, parsed);
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (value == null || !mounted) return;
    try {
      final data = await Api.put<Map<String, dynamic>>(
        '/grades/exams/${exam.id}/full-score?fullScore=${Uri.encodeQueryComponent(scoreText(value))}',
      );
      if (!mounted) return;
      final updated = GradeExam.from(data);
      setState(() {
        _exam = updated;
        _exams = _exams.map((item) => item.id == updated.id ? updated : item).toList();
      });
      await _loadEntry();
      if (mounted) showToast(context, '满分已更新');
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    }
  }

  Future<void> _openSettings() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const GradeSettingsPage()));
    try {
      final data = await Api.get<List<dynamic>>('/grades/rules');
      if (mounted) setState(() => _rules = mapList(data).map(GradeRuleInfo.from).toList());
    } catch (_) {
      // 等级规则刷新失败不影响录入。
    }
  }

  Future<void> _viewImage(String url) async {
    await showDialog<void>(
      context: context,
      builder: (context) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        backgroundColor: Colors.black,
        child: Stack(
          children: [
            InteractiveViewer(
              child: Image.network(AppConfig.fileUrl(url), fit: BoxFit.contain),
            ),
            Positioned(
              top: 4,
              right: 4,
              child: IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close_rounded, color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickPaperImages() async {
    final exam = _exam;
    if (exam == null || !exam.editable) {
      showToast(context, '请先解锁考试后再修改试卷图片');
      return;
    }
    if (_paperImages.length >= _maxImages) {
      showToast(context, '每场考试最多上传 9 张试卷图片');
      return;
    }
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined, color: AppColors.primary),
              title: const Text('拍照上传'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined, color: AppColors.primary),
              title: const Text('从相册选择'),
              subtitle: const Text('可一次选择多张，最多 9 张'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    final files = <XFile>[];
    if (source == ImageSource.camera) {
      final shot = await _picker.pickImage(source: ImageSource.camera, maxWidth: 1600, imageQuality: 80);
      if (shot != null) files.add(shot);
    } else {
      final picked = await _picker.pickMultiImage(maxWidth: 1600, imageQuality: 80);
      files.addAll(picked);
    }
    if (files.isEmpty) return;
    final remain = _maxImages - _paperImages.length;
    final selected = files.take(remain).toList();
    var skipped = files.length - selected.length;
    setState(() => _uploading = true);
    var failed = 0;
    for (final file in selected) {
      try {
        if (await file.length() > _maxFileSize) {
          skipped += 1;
          continue;
        }
        final data = await Api.upload<Map<String, dynamic>>(
          '/grades/exams/${exam.id}/paper-images',
          filePath: file.path,
        );
        final updated = GradeExam.from(data);
        if (!mounted) return;
        setState(() {
          _paperImages = updated.paperImages;
          _exam = updated;
        });
      } catch (_) {
        failed += 1;
      }
    }
    if (!mounted) return;
    setState(() => _uploading = false);
    final messages = <String>['成功上传 ${selected.length - failed} 张'];
    if (failed > 0) messages.add('$failed 张上传失败');
    if (skipped > 0) messages.add('$skipped 张已跳过（超出数量或超过 10MB）');
    showToast(context, messages.join('，'));
  }

  Future<void> _deletePaperImage(String url) async {
    final exam = _exam;
    if (exam == null) return;
    final confirmed = await confirmDialog(context, '删除试卷图片', '确认删除这张试卷图片？删除后不可恢复。');
    if (!confirmed || !mounted) return;
    try {
      final data = await Api.delete<Map<String, dynamic>>(
        '/grades/exams/${exam.id}/paper-images',
        {'url': url},
      );
      if (!mounted) return;
      final updated = GradeExam.from(data);
      setState(() {
        _paperImages = updated.paperImages;
        _exam = updated;
      });
      showToast(context, '试卷图片已删除');
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    }
  }

  void _batchFill() {
    final entry = _entry;
    if (entry == null) return;
    if (entry.levelMode) {
      showToast(context, '等级制考试请直接选择等级或使用「按姓名批量录入」');
      return;
    }
    if (entry.parts.isNotEmpty) {
      showToast(context, '已配置分项，请使用「按姓名批量录入」选择分项');
      return;
    }
    final value = double.tryParse(_batchFillController.text.trim());
    if (value == null) {
      showToast(context, '请先填写要批量填充的分数');
      return;
    }
    if (value < 0 || value > entry.fullScore) {
      showToast(context, '分数需在 0 - ${scoreText(entry.fullScore)} 之间');
      return;
    }
    setState(() {
      for (final row in _rows) {
        if (row.status == '缺考' || row.status == '免考') continue;
        row.totalController.text = scoreText(value);
        row.status = '正常';
      }
      _batchFillController.clear();
    });
    showToast(context, '已批量填充 ${_rows.length} 名学员');
  }

  Future<void> _openBatchByName() async {
    final entry = _entry;
    if (entry == null) return;
    final targets = <String>['总分', ...entry.parts.map((part) => part.name)];
    var target = entry.parts.isEmpty ? '总分' : entry.parts.first.name;
    final controller = TextEditingController();
    final applied = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          title: const Text('按姓名批量录入', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
          content: SizedBox(
            width: double.maxFinite,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '每行一条「学员姓名 分数」，可直接从 Excel 粘贴两列。',
                  style: TextStyle(fontSize: 12, color: AppColors.muted, height: 1.5),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final item in targets)
                      ChoiceChip(
                        label: Text(item),
                        selected: target == item,
                        onSelected: (_) => setDialogState(() => target = item),
                      ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: controller,
                  minLines: 4,
                  maxLines: 7,
                  decoration: const InputDecoration(hintText: '张三 95\n李四\t88'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('批量填入')),
          ],
        ),
      ),
    );
    final text = controller.text;
    controller.dispose();
    if (applied != true || !mounted) return;
    final lines = text.split(RegExp(r'[\r\n]+')).where((line) => line.trim().isNotEmpty);
    var matched = 0;
    var invalid = 0;
    final unmatched = <String>[];
    setState(() {
      for (final line in lines) {
        final cells = line.split(RegExp(r'[\s,，\t]+')).where((cell) => cell.isNotEmpty).toList();
        if (cells.length < 2) {
          invalid += 1;
          continue;
        }
        final name = cells.first.trim();
        final value = double.tryParse(cells[1].trim());
        if (value == null) {
          invalid += 1;
          continue;
        }
        final row = firstOrNullWhere(_rows, (item) => item.student.studentName == name);
        if (row == null) {
          unmatched.add(name);
          continue;
        }
        row.status = '正常';
        if (target == '总分') {
          row.totalController.text = scoreText(value);
        } else {
          final part = firstOrNullWhere(entry.parts, (item) => item.name == target);
          if (part == null) continue;
          final partController = row.partControllers.putIfAbsent('${part.id}', () => TextEditingController());
          partController.text = scoreText(value);
        }
        matched += 1;
      }
    });
    final messages = <String>['已填入 $matched 条'];
    if (invalid > 0) messages.add('$invalid 行格式不正确');
    if (unmatched.isNotEmpty) messages.add('${unmatched.length} 人未匹配：${unmatched.take(3).join('、')}');
    showToast(context, messages.join('，'));
  }

  num? _partTotal(_EntryRowDraft row, List<GradePart> parts) {
    double? total;
    for (final part in parts) {
      final text = row.partControllers['${part.id}']?.text.trim() ?? '';
      if (text.isEmpty) continue;
      final value = double.tryParse(text);
      if (value == null) continue;
      total = (total ?? 0) + value;
    }
    return total;
  }

  List<Map<String, dynamic>>? _buildRows() {
    final entry = _entry;
    if (entry == null) return null;
    final rows = <Map<String, dynamic>>[];
    for (final row in _rows) {
      final partScores = <String, dynamic>{};
      if (entry.parts.isNotEmpty) {
        for (final part in entry.parts) {
          final text = row.partControllers['${part.id}']?.text.trim() ?? '';
          partScores['${part.id}'] = text.isEmpty ? null : double.tryParse(text);
        }
      }
      double? total;
      final totalText = row.totalController.text.trim();
      if (totalText.isNotEmpty) total = double.tryParse(totalText);
      if (entry.parts.isNotEmpty) total = _partTotal(row, entry.parts)?.toDouble() ?? total;
      if (row.status == '正常' && total != null && total > entry.fullScore) {
        showToast(context, '${row.student.studentName} 的成绩不能超过满分 ${scoreText(entry.fullScore)}');
        return null;
      }
      rows.add({
        'studentId': row.student.studentId,
        'enrollmentId': row.student.enrollmentId,
        'totalScore': row.status == '正常' ? total : null,
        'level': row.level,
        'partScores': entry.parts.isEmpty ? null : partScores,
        'status': row.status,
        'remark': row.remarkController.text.trim(),
      });
    }
    return rows;
  }

  Future<void> _save({required bool publish}) async {
    final exam = _exam;
    final entry = _entry;
    final classId = _classId;
    if (exam == null || entry == null || classId == null) return;
    if (!entry.editable) {
      showToast(context, entry.status == '已归档' ? '该考试已归档，成绩只读' : '该考试已发布，请先在考试管理中解锁');
      return;
    }
    if (publish) {
      final confirmed = await confirmDialog(
        context,
        '发布成绩',
        '发布后本场考试成绩将锁定并计算排名，如需修改需在考试管理中解锁。确认发布？',
      );
      if (!confirmed || !mounted) return;
    }
    final rows = _buildRows();
    if (rows == null) return;
    setState(() => _saving = true);
    try {
      await Api.post<Map<String, dynamic>>(
        publish ? '/grades/entry/publish' : '/grades/entry/save',
        {'examId': exam.id, 'classId': classId, 'rows': rows},
      );
      if (!mounted) return;
      showToast(context, publish ? '成绩已发布' : '草稿已保存');
      await _loadEntry();
      await _refreshExamList();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    } catch (_) {
      if (mounted) showToast(context, '保存失败，请稍后重试');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const LoadingView();
    if (_error != null) return ErrorView(_error!, onRetry: _init);
    if (_exams.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无考试', subtitle: '请先在考试管理中创建考试')],
      );
    }
    final entry = _entry;
    final editable = entry?.editable ?? false;
    return RefreshIndicator(
      onRefresh: _loadEntry,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 28),
        children: [
          _selectorCard(),
          if (entry != null) ...[
            const SizedBox(height: 10),
            _infoCard(entry, editable),
            const SizedBox(height: 10),
            _paperCard(editable),
          ],
          if (_loadingEntry) const LoadingView(height: 240),
          if (!_loadingEntry && _entryError != null) ErrorView(_entryError!, onRetry: _loadEntry),
          if (!_loadingEntry && _entryError == null && entry == null)
            const EmptyState(title: '暂无可录入的班级', subtitle: '该考试尚未关联班级'),
          if (!_loadingEntry && _entryError == null && entry != null) ...[
            if (entry.students.isEmpty)
              const EmptyState(title: '暂无学员', subtitle: '该班级当前没有在读学员')
            else ...[
              const SizedBox(height: 10),
              if (!editable) _readonlyBanner(entry),
              if (editable) _toolbar(entry),
              const SizedBox(height: 10),
              for (var i = 0; i < _rows.length; i++) ...[
                _studentCard(i, _rows[i], entry, editable),
                if (i != _rows.length - 1) const SizedBox(height: 8),
              ],
              if (editable) ...[
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _saving ? null : () => _save(publish: false),
                        child: const Text('保存草稿'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: _saving ? null : () => _save(publish: true),
                        child: _saving
                            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                            : const Text('发布成绩'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ],
        ],
      ),
    );
  }

  Widget _selectorCard() {
    return SurfaceCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        children: [
          PickerField(label: '考试', value: _exam?.name, hint: '请选择考试', onTap: _pickExam),
          const SizedBox(height: 8),
          PickerField(
            label: '班级',
            value: firstOrNullWhere(_classes, (item) => item.id == _classId)?.name,
            hint: _classes.isEmpty ? '该考试未关联班级' : '请选择班级',
            onTap: _classes.isEmpty ? () {} : _pickClass,
            enabled: _classes.isNotEmpty,
          ),
        ],
      ),
    );
  }

  Widget _infoCard(GradeEntryData entry, bool editable) {
    return SurfaceCard(
      padding: const EdgeInsets.all(15),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  entry.examName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
              ),
              const SizedBox(width: 8),
              StatusTag(entry.status, compact: true),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              StatusTag(entry.examType, color: AppColors.primary, background: AppColors.primarySoft, compact: true),
              StatusTag(entry.scoringMode == '等级' ? '等级制' : '分数制', color: AppColors.purple, background: AppColors.purpleSoft, compact: true),
              Text('满分 ${scoreText(entry.fullScore)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
              Text('考试日期 ${dateOnly(entry.examDate)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              OutlinedButton.icon(
                onPressed: editable ? _editFullScore : null,
                icon: const Icon(Icons.tune_rounded, size: 16),
                label: const Text('修改满分'),
                style: OutlinedButton.styleFrom(minimumSize: const Size(0, 36), textStyle: const TextStyle(fontSize: 12.5)),
              ),
              const SizedBox(width: 10),
              OutlinedButton.icon(
                onPressed: _openSettings,
                icon: const Icon(Icons.settings_outlined, size: 16),
                label: const Text('成绩设置'),
                style: OutlinedButton.styleFrom(minimumSize: const Size(0, 36), textStyle: const TextStyle(fontSize: 12.5)),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _paperCard(bool editable) {
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(15, 12, 15, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('试卷图片', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text)),
              const SizedBox(width: 6),
              Text('${_paperImages.length}/$_maxImages', style: const TextStyle(fontSize: 11, color: AppColors.muted)),
              const Spacer(),
              if (editable)
                TextButton.icon(
                  onPressed: _uploading ? null : _pickPaperImages,
                  icon: const Icon(Icons.add_a_photo_outlined, size: 16),
                  label: Text(_uploading ? '上传中…' : '上传'),
                  style: TextButton.styleFrom(minimumSize: const Size(0, 32)),
                ),
            ],
          ),
          const SizedBox(height: 8),
          if (_paperImages.isEmpty)
            const InlineHint('暂无试卷图片，可拍照上传试卷，便于后续查阅')
          else
            SizedBox(
              height: 84,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _paperImages.length,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final url = _paperImages[index];
                  return GestureDetector(
                    onTap: () => _viewImage(url),
                    child: Stack(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(10),
                          child: Image.network(
                            AppConfig.fileUrl(url),
                            width: 84,
                            height: 84,
                            fit: BoxFit.cover,
                            errorBuilder: (_, _, _) => Container(
                              width: 84,
                              height: 84,
                              color: const Color(0xFFF3F4F6),
                              alignment: Alignment.center,
                              child: const Icon(Icons.broken_image_outlined, size: 20, color: AppColors.muted),
                            ),
                          ),
                        ),
                        if (editable)
                          Positioned(
                            top: 2,
                            right: 2,
                            child: GestureDetector(
                              onTap: () => _deletePaperImage(url),
                              child: Container(
                                padding: const EdgeInsets.all(3),
                                decoration: const BoxDecoration(color: Colors.black54, shape: BoxShape.circle),
                                child: const Icon(Icons.close_rounded, size: 13, color: Colors.white),
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget _readonlyBanner(GradeEntryData entry) {
    final text = entry.status == '已归档'
        ? '该考试已归档，成绩只读'
        : '该考试成绩已发布并锁定，如需修改请到「考试管理」解锁';
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(color: AppColors.warningSoft, borderRadius: BorderRadius.circular(10)),
      child: Row(
        children: [
          const Icon(Icons.lock_outline_rounded, size: 16, color: AppColors.warning),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: const TextStyle(fontSize: 12, color: AppColors.warning, height: 1.5))),
        ],
      ),
    );
  }

  Widget _toolbar(GradeEntryData entry) {
    return SurfaceCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _batchFillController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(hintText: '批量填充分数'),
                ),
              ),
              const SizedBox(width: 8),
              FilledButton(
                onPressed: _batchFill,
                style: FilledButton.styleFrom(minimumSize: const Size(64, 42), textStyle: const TextStyle(fontSize: 13)),
                child: const Text('填充'),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              TextButton.icon(
                onPressed: _openBatchByName,
                icon: const Icon(Icons.playlist_add_rounded, size: 17),
                label: const Text('按姓名批量录入'),
                style: TextButton.styleFrom(minimumSize: const Size(0, 32)),
              ),
              const Spacer(),
              Text('${_rows.length} 名学员', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _studentCard(int index, _EntryRowDraft row, GradeEntryData entry, bool editable) {
    final absent = row.status == '缺考' || row.status == '免考';
    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              AvatarBadge(row.student.studentName, size: 32),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      row.student.studentName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                    if ((row.student.studentNo ?? '').isNotEmpty)
                      Text('学号 ${row.student.studentNo}', style: const TextStyle(fontSize: 10.5, color: AppColors.muted)),
                  ],
                ),
              ),
              if (row.student.rankClass != null && !editable)
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: StatusTag('班排 ${row.student.rankClass}', compact: true),
                ),
              if (editable)
                PopupMenuButton<String>(
                  tooltip: '成绩状态',
                  onSelected: (value) => setState(() => row.status = value),
                  itemBuilder: (context) => const [
                    PopupMenuItem(value: '正常', child: Text('正常')),
                    PopupMenuItem(value: '缺考', child: Text('缺考')),
                    PopupMenuItem(value: '免考', child: Text('免考')),
                  ],
                  child: StatusTag(row.status, compact: true),
                )
              else
                StatusTag(row.status, compact: true),
            ],
          ),
          const SizedBox(height: 10),
          if (entry.levelMode) _levelRow(row, entry, editable, absent),
          if (!entry.levelMode && !absent) _scoreRow(row, entry, editable),
          if (absent && editable)
            const InlineHint('缺考 / 免考学员不参与分数统计'),
          if (!editable && absent && row.level != null)
            Text('等级：${row.level}', style: const TextStyle(fontSize: 12, color: AppColors.sub)),
          const SizedBox(height: 8),
          if (editable)
            TextField(
              controller: row.remarkController,
              maxLength: 100,
              decoration: const InputDecoration(hintText: '评语 / 备注（选填）', counterText: ''),
              style: const TextStyle(fontSize: 12.5),
            )
          else if ((row.remarkController.text).trim().isNotEmpty)
            Text('备注：${row.remarkController.text.trim()}', style: const TextStyle(fontSize: 12, color: AppColors.sub)),
        ],
      ),
    );
  }

  Widget _scoreRow(_EntryRowDraft row, GradeEntryData entry, bool editable) {
    if (!editable) {
      final partsText = entry.parts
          .map((part) => '${part.name} ${row.partControllers['${part.id}']?.text.trim().isEmpty ?? true ? '-' : row.partControllers['${part.id}']!.text.trim()}')
          .join(' · ');
      return Row(
        children: [
          Expanded(
            child: Text(
              entry.parts.isEmpty ? '总分 ${row.totalController.text.trim().isEmpty ? '-' : row.totalController.text.trim()}' : partsText,
              style: const TextStyle(fontSize: 12.5, color: AppColors.sub),
            ),
          ),
          Text(
            '${_partTotal(row, entry.parts) == null ? (row.totalController.text.trim().isEmpty ? '-' : row.totalController.text.trim()) : scoreText(_partTotal(row, entry.parts))} / ${scoreText(entry.fullScore)}',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.primary),
          ),
        ],
      );
    }
    if (entry.parts.isEmpty) {
      return Row(
        children: [
          Expanded(
            child: TextField(
              controller: row.totalController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(hintText: '0 - ${scoreText(entry.fullScore)}', prefixText: '总分  '),
              style: const TextStyle(fontSize: 13.5),
            ),
          ),
          const SizedBox(width: 8),
          Text('/ ${scoreText(entry.fullScore)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            for (var i = 0; i < entry.parts.length; i++) ...[
              if (i > 0) const SizedBox(width: 6),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${entry.parts[i].name}${entry.parts[i].fullScore == null ? '' : '/${scoreText(entry.parts[i].fullScore)}'}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 10.5, color: AppColors.muted),
                    ),
                    const SizedBox(height: 3),
                    TextField(
                      controller: row.partControllers.putIfAbsent('${entry.parts[i].id}', TextEditingController.new),
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(hintText: '0'),
                      style: const TextStyle(fontSize: 13),
                      onChanged: (_) {
                        final partTotal = _partTotal(row, entry.parts);
                        if (partTotal != null) row.totalController.text = scoreText(partTotal);
                        setState(() {});
                      },
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 6),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: row.totalController,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(hintText: '填写分项后自动汇总，也可直接填写总分', prefixText: '总分  '),
                style: const TextStyle(fontSize: 13),
                onChanged: (_) => setState(() {}),
              ),
            ),
            const SizedBox(width: 8),
            Text('/ ${scoreText(entry.fullScore)}', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
          ],
        ),
      ],
    );
  }

  Widget _levelRow(_EntryRowDraft row, GradeEntryData entry, bool editable, bool absent) {
    if (!editable) {
      return Row(
        children: [
          Text('等级：${row.level ?? '-'}', style: const TextStyle(fontSize: 12.5, color: AppColors.sub)),
          const Spacer(),
          if (row.totalController.text.trim().isNotEmpty)
            Text('${row.totalController.text.trim()} / ${scoreText(entry.fullScore)}', style: const TextStyle(fontSize: 12.5, color: AppColors.primary)),
        ],
      );
    }
    final level = row.level;
    return Row(
      children: [
        Expanded(
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: absent
                ? null
                : () async {
                    final picked = await showOptionPicker<String>(
                      context: context,
                      title: '选择等级',
                      items: _levelOptions,
                      labelOf: (item) => item,
                      selected: level,
                    );
                    if (picked != null) setState(() => row.level = picked);
                  },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
              decoration: BoxDecoration(
                color: absent ? const Color(0xFFF7F8FA) : Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(
                children: [
                  Text('等级', style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      level == null || level.isEmpty ? '选择等级' : level,
                      textAlign: TextAlign.right,
                      style: TextStyle(
                        fontSize: 13,
                        color: level == null || level.isEmpty ? AppColors.muted : levelColor(level),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(Icons.expand_more_rounded, size: 18, color: AppColors.muted),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        SizedBox(
          width: 96,
          child: TextField(
            controller: row.totalController,
            enabled: !absent,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(hintText: '分数(可选)'),
            style: const TextStyle(fontSize: 12.5),
          ),
        ),
      ],
    );
  }
}
