import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/grade_models.dart';
import '../core/models.dart';
import '../ui/grade_ui.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

/// 成绩中心 - 成绩设置：等级规则与分项模板维护。
class GradeSettingsPage extends StatefulWidget {
  const GradeSettingsPage({super.key});

  @override
  State<GradeSettingsPage> createState() => _GradeSettingsPageState();
}

class _GradeSettingsPageState extends State<GradeSettingsPage> {
  bool _loading = true;
  String? _error;
  List<GradeRuleInfo> _rules = const [];
  List<GradePartTemplateInfo> _templates = const [];

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
      final results = await Future.wait([
        Api.get<List<dynamic>>('/grades/rules'),
        Api.get<List<dynamic>>('/grades/part-templates'),
      ]);
      if (!mounted) return;
      setState(() {
        _rules = mapList(results[0]).map(GradeRuleInfo.from).toList();
        _templates = mapList(results[1]).map(GradePartTemplateInfo.from).toList();
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
        _error = '成绩设置加载失败';
      });
    }
  }

  Future<void> _editRule([GradeRuleInfo? rule]) async {
    final nameController = TextEditingController(text: rule?.name ?? '');
    final minController = TextEditingController(text: rule?.minScore == null ? '' : scoreText(rule!.minScore));
    final maxController = TextEditingController(text: rule?.maxScore == null ? '' : scoreText(rule!.maxScore));
    final sortController = TextEditingController(text: rule == null ? '${_rules.length + 1}' : '${rule.sort}');
    final saved = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: Text(rule == null ? '新增等级规则' : '编辑等级规则', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: nameController, decoration: const InputDecoration(labelText: '等级名称', hintText: '如：优秀')),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: minController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '最低分'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: maxController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '最高分'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              TextField(
                controller: sortController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: '排序', hintText: '数字越小越靠前'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          FilledButton(
            onPressed: () {
              if (nameController.text.trim().isEmpty) {
                showToast(context, '请填写等级名称');
                return;
              }
              final min = double.tryParse(minController.text.trim());
              final max = double.tryParse(maxController.text.trim());
              if (min == null || max == null) {
                showToast(context, '请填写完整的分数区间');
                return;
              }
              if (min > max) {
                showToast(context, '最低分不能大于最高分');
                return;
              }
              Navigator.pop(context, true);
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    final body = {
      'name': nameController.text.trim(),
      'minScore': double.tryParse(minController.text.trim()),
      'maxScore': double.tryParse(maxController.text.trim()),
      'sort': int.tryParse(sortController.text.trim()) ?? _rules.length + 1,
    };
    nameController.dispose();
    minController.dispose();
    maxController.dispose();
    sortController.dispose();
    if (saved != true || !mounted) return;
    await _submitSetting(
      request: () => rule == null
          ? Api.post<Map<String, dynamic>>('/grades/rules', body)
          : Api.put<Map<String, dynamic>>('/grades/rules/${rule.id}', body),
      success: rule == null ? '等级规则已新增' : '等级规则已保存',
    );
  }

  Future<void> _deleteRule(GradeRuleInfo rule) async {
    final confirmed = await confirmDialog(context, '删除等级规则', '确认删除等级「${rule.name}」？删除后该等级将不再可选。');
    if (!confirmed || !mounted) return;
    await _submitSetting(
      request: () => Api.delete<void>('/grades/rules/${rule.id}'),
      success: '等级规则已删除',
    );
  }

  Future<void> _editTemplate([GradePartTemplateInfo? template]) async {
    final nameController = TextEditingController(text: template?.name ?? '');
    final categoryController = TextEditingController(text: template?.courseCategory ?? '');
    final parts = <_TemplatePartDraft>[
      for (final part in template?.parts ?? const <GradePart>[])
        _TemplatePartDraft(name: part.name, weight: part.weight, fullScore: part.fullScore),
    ];
    if (parts.isEmpty) parts.add(_TemplatePartDraft());
    final saved = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          title: Text(template == null ? '新增分项模板' : '编辑分项模板', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(controller: nameController, decoration: const InputDecoration(labelText: '模板名称', hintText: '如：书法三部分')),
                  const SizedBox(height: 10),
                  TextField(
                    controller: categoryController,
                    decoration: const InputDecoration(labelText: '适用课程分类', hintText: '选填，如：书法类'),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      const Text('分项配置', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.sub)),
                      const Spacer(),
                      TextButton.icon(
                        onPressed: () => setDialogState(() => parts.add(_TemplatePartDraft())),
                        icon: const Icon(Icons.add_rounded, size: 16),
                        label: const Text('添加'),
                        style: TextButton.styleFrom(minimumSize: const Size(0, 30)),
                      ),
                    ],
                  ),
                  for (var i = 0; i < parts.length; i++)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 5,
                            child: TextField(
                              controller: parts[i].nameController,
                              decoration: const InputDecoration(hintText: '分项名称'),
                            ),
                          ),
                          const SizedBox(width: 5),
                          Expanded(
                            flex: 3,
                            child: TextField(
                              controller: parts[i].weightController,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(hintText: '权重%'),
                            ),
                          ),
                          const SizedBox(width: 5),
                          Expanded(
                            flex: 3,
                            child: TextField(
                              controller: parts[i].fullScoreController,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(hintText: '满分'),
                            ),
                          ),
                          IconButton(
                            onPressed: parts.length <= 1
                                ? null
                                : () {
                                    final removed = parts.removeAt(i);
                                    removed.dispose();
                                    setDialogState(() {});
                                  },
                            icon: const Icon(Icons.close_rounded, size: 17, color: AppColors.muted),
                            visualDensity: VisualDensity.compact,
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            FilledButton(
              onPressed: () {
                if (nameController.text.trim().isEmpty) {
                  showToast(context, '请填写模板名称');
                  return;
                }
                if (parts.any((part) => part.nameController.text.trim().isEmpty)) {
                  showToast(context, '请填写分项名称');
                  return;
                }
                final weight = parts.fold<double>(0, (sum, part) => sum + (double.tryParse(part.weightController.text.trim()) ?? 0));
                if (parts.isNotEmpty && (weight - 100).abs() > 0.01) {
                  showToast(context, '分项权重合计应为 100%，当前为 ${scoreText(weight)}%');
                  return;
                }
                Navigator.pop(context, true);
              },
              child: const Text('保存'),
            ),
          ],
        ),
      ),
    );
    final body = {
      'name': nameController.text.trim(),
      'courseCategory': categoryController.text.trim(),
      'parts': [
        for (var i = 0; i < parts.length; i++)
          {
            'name': parts[i].nameController.text.trim(),
            'weight': double.tryParse(parts[i].weightController.text.trim()) ?? 0,
            'fullScore': double.tryParse(parts[i].fullScoreController.text.trim()) ?? 0,
            'sort': i,
          },
      ],
    };
    nameController.dispose();
    categoryController.dispose();
    for (final part in parts) {
      part.dispose();
    }
    if (saved != true || !mounted) return;
    await _submitSetting(
      request: () => template == null
          ? Api.post<Map<String, dynamic>>('/grades/part-templates', body)
          : Api.put<Map<String, dynamic>>('/grades/part-templates/${template.id}', body),
      success: template == null ? '分项模板已新增' : '分项模板已保存',
    );
  }

  Future<void> _deleteTemplate(GradePartTemplateInfo template) async {
    final confirmed = await confirmDialog(context, '删除分项模板', '确认删除模板「${template.name}」？已创建考试的分项配置不受影响。');
    if (!confirmed || !mounted) return;
    await _submitSetting(
      request: () => Api.delete<void>('/grades/part-templates/${template.id}'),
      success: '分项模板已删除',
    );
  }

  Future<void> _submitSetting({required Future<dynamic> Function() request, required String success}) async {
    try {
      await request();
      if (!mounted) return;
      showToast(context, success);
      await _load();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    } catch (_) {
      if (mounted) showToast(context, '操作失败，请稍后重试');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('成绩设置')),
      body: _loading
          ? const LoadingView()
          : _error != null
              ? ErrorView(_error!, onRetry: _load)
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 28),
                    children: [
                      Row(
                        children: [
                          const Expanded(child: SectionTitle('等级规则', padding: EdgeInsets.zero)),
                          TextButton.icon(
                            onPressed: () => _editRule(),
                            icon: const Icon(Icons.add_rounded, size: 17),
                            label: const Text('新增'),
                          ),
                        ],
                      ),
                      const GradeHintLine('用于等级制考试，系统按分数区间自动匹配等级；未配置时按 优秀≥90、良好≥80、合格≥60、待提高<60 计算。'),
                      const SizedBox(height: 8),
                      if (_rules.isEmpty)
                        const SurfaceCard(child: EmptyState(title: '暂无等级规则', subtitle: '成绩等级将使用系统默认区间'))
                      else
                        for (final rule in _rules) ...[
                          SurfaceCard(
                            padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
                            child: Row(
                              children: [
                                StatusTag(rule.name, color: levelColor(rule.name), background: levelBackground(rule.name)),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    '${scoreText(rule.minScore)} - ${scoreText(rule.maxScore)} 分 · 排序 ${rule.sort}',
                                    style: const TextStyle(fontSize: 12, color: AppColors.sub),
                                  ),
                                ),
                                IconButton(
                                  onPressed: () => _editRule(rule),
                                  icon: const Icon(Icons.edit_outlined, size: 18, color: AppColors.muted),
                                  visualDensity: VisualDensity.compact,
                                ),
                                IconButton(
                                  onPressed: () => _deleteRule(rule),
                                  icon: const Icon(Icons.delete_outline_rounded, size: 18, color: AppColors.danger),
                                  visualDensity: VisualDensity.compact,
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 8),
                        ],
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          const Expanded(child: SectionTitle('分项模板', padding: EdgeInsets.zero)),
                          TextButton.icon(
                            onPressed: () => _editTemplate(),
                            icon: const Icon(Icons.add_rounded, size: 17),
                            label: const Text('新增'),
                          ),
                        ],
                      ),
                      const GradeHintLine('新建考试时可一键导入模板中的分项名称、权重与满分，权重合计需为 100%。'),
                      const SizedBox(height: 8),
                      if (_templates.isEmpty)
                        const SurfaceCard(child: EmptyState(title: '暂无分项模板', subtitle: '可在新建考试时手动添加分项'))
                      else
                        for (final template in _templates) ...[
                          SurfaceCard(
                            padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Flexible(
                                            child: Text(
                                              template.name,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text),
                                            ),
                                          ),
                                          if ((template.courseCategory ?? '').isNotEmpty) ...[
                                            const SizedBox(width: 6),
                                            StatusTag(template.courseCategory!, compact: true),
                                          ],
                                        ],
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        template.parts
                                            .map((part) => '${part.name} ${scoreText(templatePartWeight(template.parts, part))}%${part.fullScore == null ? '' : '/${scoreText(part.fullScore)}分'}')
                                            .join(' · '),
                                        style: const TextStyle(fontSize: 11.5, color: AppColors.muted, height: 1.5),
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  onPressed: () => _editTemplate(template),
                                  icon: const Icon(Icons.edit_outlined, size: 18, color: AppColors.muted),
                                  visualDensity: VisualDensity.compact,
                                ),
                                IconButton(
                                  onPressed: () => _deleteTemplate(template),
                                  icon: const Icon(Icons.delete_outline_rounded, size: 18, color: AppColors.danger),
                                  visualDensity: VisualDensity.compact,
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 8),
                        ],
                    ],
                  ),
                ),
    );
  }
}

class _TemplatePartDraft {
  _TemplatePartDraft({String name = '', num? weight, num? fullScore})
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
