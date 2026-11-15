import 'package:flutter/material.dart';

import '../../core/api.dart';
import '../../core/format.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';
import 'shared.dart';

/// 资料费：按报读课程 + 资料费模板缴费，自动写入缴纳周期。
class DataFeeForm extends StatefulWidget {
  const DataFeeForm({super.key, required this.studentId, required this.onDone});

  final int studentId;
  final VoidCallback onDone;

  @override
  State<DataFeeForm> createState() => _DataFeeFormState();
}

class _DataFeeFormState extends State<DataFeeForm> {
  final _remark = TextEditingController();

  List<Map<String, dynamic>> _templates = const [];
  List<Map<String, dynamic>> _enrollments = const [];
  List<String> _payMethods = const ['现金'];

  int? _enrollmentId;
  int? _templateId;
  String _payMethod = '现金';
  bool _loading = true;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _remark.dispose();
    super.dispose();
  }

  Map<String, dynamic>? _pick(List<Map<String, dynamic>> list, int? id) {
    if (id == null) return null;
    for (final item in list) {
      if (intOf(item['id']) == id) return item;
    }
    return null;
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<Map<String, dynamic>>('/data-fees/options', {'studentId': widget.studentId});
      final methods = (data['payMethods'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      if (!mounted) return;
      setState(() {
        _templates = mapList(data['templates']).where((item) => str(item['status']) != '停用').toList();
        _enrollments = mapList(data['enrollments']);
        _payMethods = methods.isEmpty ? const ['现金'] : methods;
        _payMethod = _payMethods.first;
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
        _error = '资料费选项加载失败';
      });
    }
  }

  Future<void> _pickEnrollment() async {
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择课程',
      items: _enrollments,
      labelOf: (item) => str(item['courseName'], '未命名课程'),
      subtitleOf: (item) {
        final last = str(item['lastPaidAt']);
        return last.isEmpty ? '尚未缴纳资料费' : '上次缴纳 ${dateOnly(last)}';
      },
      selected: _pick(_enrollments, _enrollmentId),
    );
    if (picked != null) setState(() => _enrollmentId = intOf(picked['id']));
  }

  Future<void> _pickTemplate() async {
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择资料费模板',
      items: _templates,
      labelOf: (item) => str(item['name']),
      subtitleOf: (item) => '${money(doubleOf(item['price']))} 元 · ${intOf(item['cycleMonths'])} 个月',
      selected: _pick(_templates, _templateId),
    );
    if (picked != null) setState(() => _templateId = intOf(picked['id']));
  }

  Future<void> _pickPayMethod() async {
    final picked = await showOptionPicker<String>(
      context: context,
      title: '选择支付方式',
      items: _payMethods,
      labelOf: (item) => item,
      selected: _payMethod,
    );
    if (picked != null) setState(() => _payMethod = picked);
  }

  Future<void> _submit() async {
    if (_submitting) return;
    if (_enrollmentId == null) {
      showToast(context, '请选择缴费课程');
      return;
    }
    if (_templateId == null) {
      showToast(context, '请选择资料费模板');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/data-fees/payments', {
        'studentId': widget.studentId,
        'enrollmentId': _enrollmentId,
        'templateId': _templateId,
        'payMethod': _payMethod,
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      setState(() => _submitting = false);
      final period = '${str(result['periodStart'], '')}${str(result['periodEnd']).isEmpty ? '' : ' 至 ${str(result['periodEnd'])}'}';
      await showPaymentSuccessDialog(
        context,
        title: '资料费缴纳成功',
        orderNo: str(result['orderNo'], '-'),
        summary: period.isEmpty ? null : '缴纳周期 $period',
      );
      if (!mounted) return;
      widget.onDone();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      showToast(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const LoadingView();
    if (_error != null) return ErrorView(_error!, onRetry: _load);
    if (_enrollments.isEmpty) {
      return const EmptyState(title: '该学员暂无可缴资料费的报读', subtitle: '需先完成课时费报名后再缴纳资料费', icon: Icons.receipt_long_outlined);
    }

    final template = _pick(_templates, _templateId);
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
            children: [
              SurfaceCard(
                padding: const EdgeInsets.all(14),
                child: Column(
                  children: [
                    PickerField(
                      label: '缴费课程',
                      value: str(_pick(_enrollments, _enrollmentId)?['courseName']),
                      hint: '请选择课程',
                      onTap: _pickEnrollment,
                    ),
                    const SizedBox(height: 10),
                    PickerField(
                      label: '资料费模板',
                      value: str(template?['name']),
                      hint: _templates.isEmpty ? '暂无启用模板' : '请选择模板',
                      enabled: _templates.isNotEmpty,
                      onTap: _pickTemplate,
                    ),
                    if (template != null) ...[
                      const SizedBox(height: 8),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '缴纳周期 ${intOf(template['cycleMonths'])} 个月 · 自动续算缴纳月份',
                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SurfaceCard(
                padding: const EdgeInsets.all(14),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: MetricTile(
                            label: '应缴金额',
                            value: money(doubleOf(template?['price'])),
                            unit: '元',
                            color: AppColors.danger,
                          ),
                        ),
                        Expanded(
                          child: MetricTile(
                            label: '上次缴纳',
                            value: str(_pick(_enrollments, _enrollmentId)?['lastPaidAt']).isEmpty
                                ? '—'
                                : dateOnly(str(_pick(_enrollments, _enrollmentId)?['lastPaidAt'])),
                            align: CrossAxisAlignment.end,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    PickerField(label: '支付方式', value: _payMethod, hint: '请选择', onTap: _pickPayMethod),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _remark,
                      maxLines: 2,
                      decoration: const InputDecoration(hintText: '备注（可选）'),
                      style: const TextStyle(fontSize: 13.5),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        SubmitBar(label: '确认缴纳', submitting: _submitting, onSubmit: _submit),
      ],
    );
  }
}
