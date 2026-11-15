import 'package:flutter/material.dart';

import '../../core/api.dart';
import '../../core/format.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';
import 'shared.dart';

/// 欠费补缴：按报读补费，金额上限为该报读的欠费金额。
class ArrearsForm extends StatefulWidget {
  const ArrearsForm({super.key, required this.studentId, required this.onDone});

  final int studentId;
  final VoidCallback onDone;

  @override
  State<ArrearsForm> createState() => _ArrearsFormState();
}

class _ArrearsFormState extends State<ArrearsForm> {
  final _amount = TextEditingController();
  final _remark = TextEditingController();

  List<Map<String, dynamic>> _enrollments = const [];
  List<String> _payMethods = const ['现金'];
  int? _enrollmentId;
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
    _amount.dispose();
    _remark.dispose();
    super.dispose();
  }

  Map<String, dynamic>? get _enrollment {
    if (_enrollmentId == null) return null;
    for (final item in _enrollments) {
      if (intOf(item['id']) == _enrollmentId) return item;
    }
    return null;
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<Map<String, dynamic>>('/local-orders/options', {'studentId': widget.studentId});
      final methods = (data['payMethods'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      final enrollments = mapList(data['enrollments']).where((item) => doubleOf(item['arrears']) > 0).toList();
      if (!mounted) return;
      setState(() {
        _enrollments = enrollments;
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
        _error = '欠费数据加载失败';
      });
    }
  }

  Future<void> _pickEnrollment() async {
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择补缴报读',
      items: _enrollments,
      labelOf: (item) => str(item['courseName'], '未命名课程'),
      subtitleOf: (item) =>
          '剩余课时 ${countText(doubleOf(item['remainLessons']))} · 欠费 ${money(doubleOf(item['arrears']))} 元',
      selected: _enrollment,
    );
    if (picked == null) return;
    setState(() {
      _enrollmentId = intOf(picked['id']);
      _amount.text = amountInput(doubleOf(picked['arrears']));
    });
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
    final enrollment = _enrollment;
    if (enrollment == null) {
      showToast(context, '请选择补缴报读');
      return;
    }
    final amount = doubleOf(_amount.text);
    final arrears = doubleOf(enrollment['arrears']);
    if (amount <= 0) {
      showToast(context, '请填写正确的补缴金额');
      return;
    }
    if (amount > arrears) {
      showToast(context, '补缴金额不能超过欠费 ${money(arrears)} 元');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/local-orders/subsidy', {
        'studentId': widget.studentId,
        'enrollmentId': _enrollmentId,
        'amount': round2(amount),
        'payMethod': _payMethod,
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      setState(() => _submitting = false);
      final paid = amount;
      _amount.clear();
      _remark.clear();
      await showPaymentSuccessDialog(
        context,
        title: '补缴成功',
        orderNo: str(result['orderNo'], '-'),
        summary: '补缴金额 ¥${money(paid)}',
      );
      if (!mounted) return;
      widget.onDone();
      _load();
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
      return const EmptyState(title: '该学员暂无欠费', subtitle: '存在欠费课时或未缴金额时会显示在补缴列表中', icon: Icons.check_circle_outline_rounded);
    }

    final enrollment = _enrollment;
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
                      label: '补缴报读',
                      value: str(enrollment?['courseName']),
                      hint: '请选择报读课程',
                      onTap: _pickEnrollment,
                    ),
                    if (enrollment != null) ...[
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: MetricTile(
                              label: '剩余课时',
                              value: countText(doubleOf(enrollment['remainLessons'])),
                              unit: '课时',
                            ),
                          ),
                          Expanded(
                            child: MetricTile(
                              label: '欠费金额',
                              value: money(doubleOf(enrollment['arrears'])),
                              unit: '元',
                              align: CrossAxisAlignment.end,
                              color: AppColors.danger,
                            ),
                          ),
                        ],
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
                    TextField(
                      controller: _amount,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: InputDecoration(
                        labelText: '补缴金额',
                        prefixText: '¥ ',
                        helperText: enrollment == null ? null : '最多可补缴 ${money(doubleOf(enrollment['arrears']))} 元',
                      ),
                      style: const TextStyle(fontSize: 15),
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
        SubmitBar(label: '确认补缴', submitting: _submitting, onSubmit: _submit),
      ],
    );
  }
}
