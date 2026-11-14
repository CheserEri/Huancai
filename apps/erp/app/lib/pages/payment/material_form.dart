import 'package:flutter/material.dart';

import '../../core/api.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';
import 'shared.dart';

/// 教材费：按教材出库并生成购买记录与订单，支持账户剩余未用学费抵扣。
class MaterialForm extends StatefulWidget {
  const MaterialForm({super.key, required this.studentId, required this.balance, required this.onDone});

  final int studentId;
  final double balance;
  final VoidCallback onDone;

  @override
  State<MaterialForm> createState() => _MaterialFormState();
}

class _MaterialFormState extends State<MaterialForm> {
  final _quantity = TextEditingController(text: '1');
  final _unitPrice = TextEditingController();
  final _balanceAmount = TextEditingController(text: '0');
  final _remark = TextEditingController();

  List<Map<String, dynamic>> _materials = const [];
  List<String> _rooms = const [];
  List<Map<String, dynamic>> _settings = const [];
  List<Map<String, dynamic>> _stocks = const [];

  Map<String, dynamic>? _material;
  String _room = '';
  bool _useBalance = false;
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
    _quantity.dispose();
    _unitPrice.dispose();
    _balanceAmount.dispose();
    _remark.dispose();
    super.dispose();
  }

  double get _quantityValue => doubleOf(_quantity.text);
  double get _total => round2(_quantityValue * doubleOf(_unitPrice.text));
  double get _balanceValue => _useBalance ? doubleOf(_balanceAmount.text) : 0;

  double get _availableStock {
    final name = str(_material?['name']);
    if (name.isEmpty) return 0;
    final row = _stocks.where((item) => str(item['name']) == name).toList();
    if (row.isEmpty) return 0;
    final data = row.first;
    if (_room.contains('鲅鱼圈')) return doubleOf(data['bayuquan']);
    if (_room.contains('新民')) return doubleOf(data['xinmin']);
    if (_room.contains('总库')) return doubleOf(data['warehouse']);
    return doubleOf(data['total']);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/materials/options'),
        Api.get<Map<String, dynamic>>('/materials/settings', {'status': '正常', 'page': 1, 'size': 500}),
        Api.get<Map<String, dynamic>>('/materials/stock', {'page': 1, 'size': 500}),
      ]);
      final options = results[0];
      final settingsPage = PageData.from<Map<String, dynamic>>(results[1], (json) => json);
      final stockPage = PageData.from<Map<String, dynamic>>(results[2], (json) => json);
      final rooms = (options['rooms'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      if (!mounted) return;
      setState(() {
        _materials = mapList(options['materials']).where((item) => str(item['status']) != '停用').toList();
        _rooms = rooms;
        _room = rooms.isEmpty ? '总库' : rooms.first;
        _settings = settingsPage.records;
        _stocks = stockPage.records;
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
        _error = '教材数据加载失败';
      });
    }
  }

  Future<void> _pickMaterial() async {
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择教材',
      items: _materials,
      labelOf: (item) => str(item['name']),
      subtitleOf: (item) {
        final setting = _settings.where((s) => str(s['name']) == str(item['name'])).toList();
        if (setting.isEmpty) return '未设置售价';
        return '售价 ${money(doubleOf(setting.first['salePrice']))} 元';
      },
      selected: _material,
    );
    if (picked == null) return;
    final setting = _settings.where((s) => str(s['name']) == str(picked['name'])).toList();
    setState(() {
      _material = picked;
      _unitPrice.text = setting.isEmpty ? '' : amountInput(doubleOf(setting.first['salePrice']));
      _balanceAmount.text = '0';
    });
  }

  Future<void> _pickRoom() async {
    final picked = await showOptionPicker<String>(
      context: context,
      title: '选择出库仓库',
      items: _rooms,
      labelOf: (item) => item,
      selected: _room,
    );
    if (picked != null) setState(() => _room = picked);
  }

  Future<void> _submit() async {
    if (_material == null) {
      showToast(context, '请选择教材');
      return;
    }
    if (_quantityValue <= 0) {
      showToast(context, '请填写正确的购买数量');
      return;
    }
    if (doubleOf(_unitPrice.text) <= 0) {
      showToast(context, '请填写正确的教材单价');
      return;
    }
    if (_balanceValue > _total) {
      showToast(context, '余额抵扣不能超过教材金额');
      return;
    }
    if (_balanceValue > widget.balance) {
      showToast(context, '抵扣金额不能超过学员账户余额');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/materials/purchases', {
        'studentId': widget.studentId,
        'roomName': _room,
        'items': [
          {
            'materialName': str(_material?['name']),
            'quantity': _quantityValue.round(),
            'unitPrice': round2(doubleOf(_unitPrice.text)),
          },
        ],
        if (_balanceValue > 0) 'balanceAmount': round2(_balanceValue),
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      setState(() => _submitting = false);
      showToast(context, '教材出库成功，单号 ${str(result['extOrderNo'], '-')}');
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

    final materialName = str(_material?['name']);
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
                      label: '教材',
                      value: materialName,
                      hint: _materials.isEmpty ? '暂无可用教材' : '请选择教材',
                      enabled: _materials.isNotEmpty,
                      onTap: _pickMaterial,
                    ),
                    if (materialName.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '$_room可用库存：${countText(_availableStock)}',
                          style: TextStyle(
                            fontSize: 12,
                            color: _availableStock <= 0 ? AppColors.danger : AppColors.muted,
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 10),
                    PickerField(label: '出库仓库', value: _room, hint: '请选择仓库', enabled: _rooms.isNotEmpty, onTap: _pickRoom),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _quantity,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            onChanged: (_) => setState(() {}),
                            decoration: const InputDecoration(labelText: '数量'),
                            style: const TextStyle(fontSize: 14),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _unitPrice,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            onChanged: (_) => setState(() {}),
                            decoration: const InputDecoration(labelText: '单价', prefixText: '¥ '),
                            style: const TextStyle(fontSize: 14),
                          ),
                        ),
                      ],
                    ),
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
                            label: '教材金额',
                            value: money(_total),
                            unit: '元',
                            color: AppColors.danger,
                          ),
                        ),
                        Expanded(
                          child: MetricTile(
                            label: '账户余额',
                            value: money(widget.balance),
                            unit: '元',
                            align: CrossAxisAlignment.end,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: const Text('使用账户剩余未用学费抵扣', style: TextStyle(fontSize: 13.5)),
                      subtitle: const Text('抵扣后仅收取差额现金', style: TextStyle(fontSize: 11.5, color: AppColors.muted)),
                      value: _useBalance,
                      onChanged: (value) => setState(() {
                        _useBalance = value;
                        _balanceAmount.text = value ? amountInput(_total < widget.balance ? _total : widget.balance) : '0';
                      }),
                    ),
                    if (_useBalance) ...[
                      const SizedBox(height: 4),
                      TextField(
                        controller: _balanceAmount,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        onChanged: (_) => setState(() {}),
                        decoration: const InputDecoration(labelText: '抵扣金额', prefixText: '¥ '),
                        style: const TextStyle(fontSize: 14),
                      ),
                    ],
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        const Expanded(
                          child: Text('需收现金', style: TextStyle(fontSize: 13, color: AppColors.muted)),
                        ),
                        Text(
                          '¥ ${money((_total - _balanceValue).clamp(0, double.infinity))}',
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.danger),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SurfaceCard(
                padding: const EdgeInsets.all(14),
                child: TextField(
                  controller: _remark,
                  maxLines: 2,
                  decoration: const InputDecoration(hintText: '备注（可选）'),
                  style: const TextStyle(fontSize: 13.5),
                ),
              ),
            ],
          ),
        ),
        SubmitBar(label: '确认购买', submitting: _submitting, onSubmit: _submit),
      ],
    );
  }
}
