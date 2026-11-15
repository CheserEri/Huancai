import 'package:flutter/material.dart';

import '../../core/api.dart';
import '../../core/format.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';
import 'shared.dart';

/// 余额充值：优先冲抵该学员欠费，剩余部分计入账户余额。
class RechargeForm extends StatefulWidget {
  const RechargeForm({super.key, required this.studentId, required this.onDone});

  final int studentId;
  final VoidCallback onDone;

  @override
  State<RechargeForm> createState() => _RechargeFormState();
}

class _RechargeFormState extends State<RechargeForm> {
  final _amount = TextEditingController();
  final _remark = TextEditingController();

  List<String> _payMethods = const ['现金'];
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

  Future<void> _load() async {
    try {
      final data = await Api.get<Map<String, dynamic>>('/local-orders/options', {'studentId': widget.studentId});
      final methods = (data['payMethods'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      if (!mounted) return;
      setState(() {
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
        _error = '支付方式加载失败';
      });
    }
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
    final amount = doubleOf(_amount.text);
    if (amount <= 0) {
      showToast(context, '请填写正确的充值金额');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/local-orders/recharge', {
        'studentId': widget.studentId,
        'amount': round2(amount),
        'payMethod': _payMethod,
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      setState(() => _submitting = false);
      final balance = result['balance'];
      _amount.clear();
      _remark.clear();
      await showPaymentSuccessDialog(
        context,
        title: '充值成功',
        orderNo: str(result['orderNo'], '-'),
        summary: balance == null ? null : '当前余额 ¥${money(doubleOf(balance))}',
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
                    TextField(
                      controller: _amount,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '充值金额', prefixText: '¥ '),
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
              const SizedBox(height: 12),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  '充值金额优先冲抵学员欠费，剩余部分计入账户剩余未用学费；欠费需独立补缴时请使用「补缴」。',
                  style: TextStyle(fontSize: 12, color: AppColors.muted, height: 1.6),
                ),
              ),
            ],
          ),
        ),
        SubmitBar(label: '确认充值', submitting: _submitting, onSubmit: _submit),
      ],
    );
  }
}
