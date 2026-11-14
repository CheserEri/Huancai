import 'package:flutter/material.dart';

import '../../core/api.dart';
import '../../core/format.dart';
import '../../ui/theme.dart';
import '../../ui/widgets.dart';
import 'shared.dart';

/// 课时费：报名 / 续费（含价格模板档位、赠送课时、优惠与活动模板）。
class EnrollForm extends StatefulWidget {
  const EnrollForm({super.key, required this.studentId, required this.onDone});

  final int studentId;
  final VoidCallback onDone;

  @override
  State<EnrollForm> createState() => _EnrollFormState();
}

class _EnrollFormState extends State<EnrollForm> {
  final _lessons = TextEditingController();
  final _unitPrice = TextEditingController();
  final _gift = TextEditingController(text: '0');
  final _discount = TextEditingController(text: '0');
  final _received = TextEditingController();
  final _remark = TextEditingController();

  List<Map<String, dynamic>> _courses = const [];
  List<Map<String, dynamic>> _classes = const [];
  List<Map<String, dynamic>> _priceTemplates = const [];
  List<Map<String, dynamic>> _activityTemplates = const [];
  List<String> _payMethods = const ['现金'];

  int? _courseId;
  int? _classId;
  int? _templateId;
  int _tierIndex = -1;
  int? _activityId;
  String _payMethod = '现金';
  DateTime? _expireDate;
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
    _lessons.dispose();
    _unitPrice.dispose();
    _gift.dispose();
    _discount.dispose();
    _received.dispose();
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

  Map<String, dynamic>? get _course => _pick(_courses, _courseId);
  Map<String, dynamic>? get _template => _pick(_courseTemplates, _templateId);
  List<Map<String, dynamic>> get _courseTemplates =>
      _priceTemplates.where((item) => intList(item['courseIds']).contains(_courseId)).toList();
  List<Map<String, dynamic>> get _tiers => mapList(_template?['prices']);
  List<Map<String, dynamic>> get _courseClasses =>
      _courseId == null ? _classes : _classes.where((item) => intOf(item['courseId']) == _courseId).toList();
  List<Map<String, dynamic>> get _activityChoices => [
        {'id': 0, 'name': '不使用优惠'},
        ..._activityTemplates,
      ];

  double get _lessonsValue => doubleOf(_lessons.text);
  double get _unitPriceValue => doubleOf(_unitPrice.text);
  double get _courseAmount => round2(_lessonsValue * _unitPriceValue);
  double get _discountValue => doubleOf(_discount.text);
  double get _giftValue => doubleOf(_gift.text);
  /// 应收为优惠前原价，应付为应收减优惠，实收必须等于应付（不支持赊账）。
  double get _shouldPay => round2(_courseAmount);
  double get _payable => round2((_courseAmount - _discountValue).clamp(0, double.infinity));
  double get _receivedValue {
    final text = _received.text.trim();
    return text.isEmpty ? _payable : doubleOf(text);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<Map<String, dynamic>>('/local-orders/options', {'studentId': widget.studentId});
      final methods = (data['payMethods'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      if (!mounted) return;
      setState(() {
        _courses = mapList(data['courses']);
        _classes = mapList(data['classes']);
        _priceTemplates = mapList(data['priceTemplates']);
        _activityTemplates = mapList(data['activityTemplates']);
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
        _error = '缴费选项加载失败';
      });
    }
  }

  Future<void> _pickCourse() async {
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择课程',
      items: _courses,
      labelOf: (item) => str(item['name']),
      selected: _course,
    );
    if (picked == null) return;
    setState(() {
      _courseId = intOf(picked['id']);
      _classId = null;
      _templateId = null;
      _tierIndex = -1;
      _lessons.clear();
      _unitPrice.clear();
      _gift.text = '0';
      _discount.text = '0';
      final defaultTemplateId = intOf(picked['defaultPriceTemplateId']);
      if (defaultTemplateId > 0) {
        final matched = _priceTemplates.where((item) => intOf(item['id']) == defaultTemplateId).toList();
        if (matched.isNotEmpty) {
          _templateId = defaultTemplateId;
          final tiers = mapList(matched.first['prices']);
          if (tiers.isNotEmpty) _applyTier(tiers.first, 0);
        }
      }
    });
  }

  Future<void> _pickTemplate() async {
    if (_courseId == null) {
      showToast(context, '请先选择报读课程');
      return;
    }
    if (_courseTemplates.isEmpty) {
      showToast(context, '该课程未绑定价格模板，可直接填写课时与单价');
      return;
    }
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择价格模板',
      items: _courseTemplates,
      labelOf: (item) => str(item['name']),
      subtitleOf: (item) {
        final tiers = mapList(item['prices']);
        return tiers.isEmpty ? '未配置课时档位' : '${tiers.length} 个课时档位';
      },
      selected: _template,
    );
    if (picked == null) return;
    setState(() {
      _templateId = intOf(picked['id']);
      final tiers = mapList(picked['prices']);
      _tierIndex = -1;
      if (tiers.isNotEmpty) _applyTier(tiers.first, 0);
    });
  }

  Future<void> _pickTier() async {
    final tiers = _tiers;
    if (tiers.isEmpty) {
      showToast(context, '当前模板未配置课时档位，可手动填写课时与单价');
      return;
    }
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择课时档位',
      items: tiers,
      labelOf: (item) => '${countText(doubleOf(item['lessons']))} 课时',
      subtitleOf: (item) =>
          '单价 ${money(doubleOf(item['unitPrice']))} 元 · 合计 ${money(doubleOf(item['totalPrice']))} 元',
      selected: _tierIndex >= 0 && _tierIndex < tiers.length ? tiers[_tierIndex] : null,
    );
    if (picked == null) return;
    setState(() => _applyTier(picked, tiers.indexOf(picked)));
  }

  void _applyTier(Map<String, dynamic> tier, int index) {
    _tierIndex = index;
    _lessons.text = amountInput(doubleOf(tier['lessons']));
    _unitPrice.text = amountInput(doubleOf(tier['unitPrice']));
    _applyActivityTemplate();
  }

  Future<void> _pickActivity() async {
    final current = _activityId == null ? _activityChoices.first : _pick(_activityTemplates, _activityId);
    final picked = await showOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择活动模板',
      items: _activityChoices,
      labelOf: (item) => str(item['name']),
      subtitleOf: (item) {
        final type = str(item['type']);
        if (type.isEmpty) return '';
        final end = str(item['endDate']);
        return end.isEmpty ? type : '$type · 至 ${dateOnly(end)}';
      },
      selected: current,
    );
    if (picked == null) return;
    setState(() {
      final id = intOf(picked['id']);
      _activityId = id == 0 ? null : id;
      _gift.text = '0';
      _applyActivityTemplate();
    });
  }

  /// 与 PC 端口径一致：报课优惠取满足课时门槛的最高档，按折扣/立减/赠送课时分别计算。
  void _applyActivityTemplate() {
    final template = _pick(_activityTemplates, _activityId);
    if (template == null) return;
    final rules = mapList(template['rules']);
    if (rules.isEmpty) return;
    Map<String, dynamic> rule = rules.first;
    if (str(template['type']) == '报课优惠') {
      final matched = rules.where((item) => _lessonsValue >= doubleOf(item['lessons'])).toList()
        ..sort((a, b) => doubleOf(b['lessons']).compareTo(doubleOf(a['lessons'])));
      if (matched.isEmpty) return;
      rule = matched.first;
    }
    final value = doubleOf(rule['discountValue']);
    final mode = str(rule['discountMode']);
    if (mode == '折扣') {
      _discount.text = amountInput(round2(_courseAmount * (1 - value / 10)));
    } else if (mode == '立减') {
      _discount.text = amountInput(round2(value).clamp(0, _courseAmount));
    } else {
      _gift.text = amountInput(value);
    }
  }

  Future<void> _pickExpireDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _expireDate ?? now.add(const Duration(days: 180)),
      firstDate: now,
      lastDate: now.add(const Duration(days: 1825)),
    );
    if (picked != null) setState(() => _expireDate = picked);
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
    if (_courseId == null) {
      showToast(context, '请选择报读课程');
      return;
    }
    if (_lessonsValue <= 0) {
      showToast(context, '请填写正确的报读课时');
      return;
    }
    if (_unitPriceValue <= 0) {
      showToast(context, '请填写正确的课时单价');
      return;
    }
    if ((_receivedValue - _payable).abs() > 0.009) {
      showToast(context, '实收金额需等于应付金额，不支持赊账');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/local-orders/enroll', {
        'studentId': widget.studentId,
        'courseId': _courseId,
        if (_classId != null && _classId != 0) 'classId': _classId,
        'lessons': round2(_lessonsValue),
        'giftLessons': round2(_giftValue),
        'unitPrice': round2(_unitPriceValue),
        'discount': round2(_discountValue),
        'shouldPay': round2(_shouldPay),
        'received': round2(_receivedValue),
        'payMethod': _payMethod,
        if (_templateId != null) 'priceTemplateId': _templateId,
        if (_activityId != null) 'localActivityTemplateIds': [_activityId],
        if (_expireDate != null) 'expireDate': dateText(_expireDate!),
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      setState(() => _submitting = false);
      showToast(context, '${str(result['typeName'], '缴费')}成功，订单号 ${str(result['orderNo'], '-')}');
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

    final courseName = str(_course?['name']);
    final className = str(_pick(_courseClasses, _classId)?['name']);
    final templateName = str(_template?['name']);
    final tierLabel = _tierIndex >= 0 && _tierIndex < _tiers.length
        ? '${countText(doubleOf(_tiers[_tierIndex]['lessons']))} 课时档'
        : '';
    final activityName = _activityId == null ? '' : str(_pick(_activityTemplates, _activityId)?['name']);

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
                    PickerField(label: '报读课程', value: courseName, hint: '请选择课程', onTap: _pickCourse),
                    const SizedBox(height: 10),
                    PickerField(
                      label: '上课班级',
                      value: className,
                      hint: _courseId == null ? '请先选择课程' : '暂不分班',
                      enabled: _courseId != null,
                      onTap: () async {
                        final picked = await showOptionPicker<Map<String, dynamic>>(
                          context: context,
                          title: '选择班级',
                          items: [{'id': 0, 'name': '暂不分班'}, ..._courseClasses],
                          labelOf: (item) => str(item['name']),
                          selected: _classId == null ? null : _pick(_courseClasses, _classId),
                        );
                        if (picked != null) setState(() => _classId = intOf(picked['id']));
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SurfaceCard(
                padding: const EdgeInsets.all(14),
                child: Column(
                  children: [
                    PickerField(
                      label: '价格模板',
                      value: templateName,
                      hint: _courseId == null ? '请先选择课程' : '课程默认模板',
                      enabled: _courseId != null,
                      trailingText: tierLabel,
                      onTap: _pickTemplate,
                    ),
                    const SizedBox(height: 10),
                    PickerField(
                      label: '课时档位',
                      value: tierLabel,
                      hint: _tiers.isEmpty ? '未配置档位，手动填写' : '请选择档位',
                      enabled: _tierIndex >= 0 || _tiers.isNotEmpty,
                      onTap: _pickTier,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(child: _numberField('报读课时', _lessons, onChanged: (_) => _onAmountChanged())),
                        const SizedBox(width: 10),
                        Expanded(child: _numberField('课时单价', _unitPrice, onChanged: (_) => _onAmountChanged())),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(child: _numberField('赠送课时', _gift)),
                        const SizedBox(width: 10),
                        Expanded(child: _numberField('优惠金额', _discount)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    PickerField(label: '活动模板', value: activityName, hint: '不使用优惠', onTap: _pickActivity),
                    const SizedBox(height: 10),
                    PickerField(
                      label: '有效期至',
                      value: _expireDate == null ? '' : dateText(_expireDate!),
                      hint: '按价格模板默认有效期',
                      onTap: _pickExpireDate,
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
                        Expanded(child: MetricTile(label: '应收金额（优惠前）', value: money(_shouldPay), unit: '元', color: AppColors.danger)),
                        Expanded(child: MetricTile(label: '应付金额', value: money(_payable), unit: '元', align: CrossAxisAlignment.end)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _received,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '实收金额（须等于应付）', prefixText: '¥ '),
                      style: const TextStyle(fontSize: 14),
                    ),
                    const SizedBox(height: 10),
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
        SubmitBar(label: '确认缴费', submitting: _submitting, onSubmit: _submit),
      ],
    );
  }

  void _onAmountChanged() {
    setState(() {
      if (_activityId != null) _applyActivityTemplate();
    });
  }

  Widget _numberField(String label, TextEditingController controller, {ValueChanged<String>? onChanged}) {
    return TextField(
      controller: controller,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      onChanged: onChanged,
      decoration: InputDecoration(labelText: label),
      style: const TextStyle(fontSize: 14),
    );
  }
}
