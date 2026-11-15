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
  List<int> _activityIds = const [];
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
  List<Map<String, dynamic>> get _selectedActivities =>
      _activityTemplates.where((item) => _activityIds.contains(intOf(item['id']))).toList();
  String get _activityNames => _selectedActivities.map((item) => str(item['name'])).join('、');

  double get _lessonsValue => doubleOf(_lessons.text);
  double get _unitPriceValue => doubleOf(_unitPrice.text);
  double get _courseAmount => round2(_lessonsValue * _unitPriceValue);
  /// 价格模板附带教材费，与 PC 端报名字段一致，计入应收并随订单保存。
  double get _materialFee => round2(doubleOf(_template?['materialFee']));
  double get _discountValue => doubleOf(_discount.text);
  double get _giftValue => doubleOf(_gift.text);
  /// 应收为优惠前原价（含教材费），应付为应收减优惠，实收必须等于应付（不支持赊账）。
  double get _shouldPay => round2(_courseAmount + _materialFee);
  double get _payable => round2((_shouldPay - _discountValue).clamp(0, double.infinity));
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
      _activityIds = const [];
      _lessons.clear();
      _unitPrice.clear();
      _gift.text = '0';
      _discount.text = '0';
      final defaultTemplateId = intOf(picked['defaultPriceTemplateId']);
      // 与 PC 端一致：优先课程默认模板，未配置默认模板时取该课程第一个模板；
      // 都没有模板时回退课程原价。
      final templates = _priceTemplates.where((item) => intList(item['courseIds']).contains(_courseId)).toList();
      final preferred = templates.where((item) => intOf(item['id']) == defaultTemplateId).toList();
      final template = preferred.isNotEmpty ? preferred.first : (templates.isEmpty ? null : templates.first);
      if (template != null) {
        _templateId = intOf(template['id']);
        final tiers = mapList(template['prices']);
        if (tiers.isNotEmpty) {
          _applyTier(tiers.first, 0);
        } else {
          final listPrice = doubleOf(picked['listPrice']);
          if (listPrice > 0) _unitPrice.text = amountInput(listPrice);
        }
      } else {
        final listPrice = doubleOf(picked['listPrice']);
        if (listPrice > 0) _unitPrice.text = amountInput(listPrice);
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
    if (_activityTemplates.isEmpty) {
      showToast(context, '暂无可用活动模板');
      return;
    }
    final picked = await showMultiOptionPicker<Map<String, dynamic>>(
      context: context,
      title: '选择活动模板（可多选叠加）',
      items: _activityTemplates,
      labelOf: (item) => str(item['name']),
      subtitleOf: (item) {
        final type = str(item['type']);
        if (type.isEmpty) return '';
        final end = str(item['endDate']);
        return end.isEmpty ? type : '$type · 至 ${dateOnly(end)}';
      },
      selected: _selectedActivities,
    );
    if (picked == null) return;
    setState(() {
      _activityIds = picked.map((item) => intOf(item['id'])).toList();
      _applyActivityTemplate();
    });
  }

  /// 与 PC 端口径一致：报课优惠取满足课时门槛的最高档，多选时折扣/立减累加，
  /// 优惠总额不超过课程金额，赠送课时累加。
  void _applyActivityTemplate() {
    var discountTotal = 0.0;
    var giftTotal = 0.0;
    for (final template in _selectedActivities) {
      final rules = mapList(template['rules']);
      if (rules.isEmpty) continue;
      Map<String, dynamic> rule = rules.first;
      if (str(template['type']) == '报课优惠') {
        final matched = rules.where((item) => _lessonsValue >= doubleOf(item['lessons'])).toList()
          ..sort((a, b) => doubleOf(b['lessons']).compareTo(doubleOf(a['lessons'])));
        if (matched.isEmpty) continue;
        rule = matched.first;
      }
      final value = doubleOf(rule['discountValue']);
      final mode = str(rule['discountMode']);
      if (mode == '折扣') {
        discountTotal += _courseAmount * (1 - value / 10);
      } else if (mode == '立减') {
        discountTotal += value;
      } else {
        giftTotal += value;
      }
    }
    _discount.text = amountInput(round2(discountTotal).clamp(0, _courseAmount));
    _gift.text = amountInput(round2(giftTotal));
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
    if (_submitting) return;
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
        'localActivityTemplateIds': _activityIds,
        if (_materialFee > 0)
          'materials': [
            {'materialId': null, 'name': '教材费', 'price': _materialFee, 'quantity': 1},
          ],
        if (_expireDate != null) 'expireDate': dateText(_expireDate!),
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      final typeName = str(result['typeName'], '缴费');
      final orderNo = str(result['orderNo'], '-');
      final paid = _receivedValue;
      // 成功后立即清空表单，防止误以为未成功而重复提交生成两笔订单。
      setState(() {
        _submitting = false;
        _resetForNext();
      });
      await showPaymentSuccessDialog(context, title: '$typeName成功', orderNo: orderNo, summary: '实收 ¥${money(paid)}');
      if (!mounted) return;
      widget.onDone();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      showToast(context, e.message);
    }
  }

  /// 提交成功后复位表单，保留支付方式。
  void _resetForNext() {
    _courseId = null;
    _classId = null;
    _templateId = null;
    _tierIndex = -1;
    _activityIds = const [];
    _expireDate = null;
    _lessons.clear();
    _unitPrice.clear();
    _gift.text = '0';
    _discount.text = '0';
    _received.clear();
    _remark.clear();
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
    final activityName = _activityNames;

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
                    PickerField(label: '活动模板', value: activityName, hint: '可多选叠加，不选则不使用优惠', onTap: _pickActivity),
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
                    if (_materialFee > 0) ...[
                      const SizedBox(height: 6),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '含价格模板教材费 ¥${money(_materialFee)}',
                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                        ),
                      ),
                    ],
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
      if (_activityIds.isNotEmpty) _applyActivityTemplate();
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
