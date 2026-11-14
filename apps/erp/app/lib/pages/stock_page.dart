import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/shared.dart';

/// 教材出入库：库存查询、出入库记录与新增出入库单据。
class StockPage extends StatefulWidget {
  const StockPage({super.key});

  @override
  State<StockPage> createState() => _StockPageState();
}

class _StockPageState extends State<StockPage> {
  final _searchController = TextEditingController();

  List<Map<String, dynamic>> _stocks = const [];
  List<Map<String, dynamic>> _changes = const [];
  int _tab = 0;
  String _bizFilter = '';
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/materials/stock', {
          'page': 1,
          'size': 100,
          if (_searchController.text.trim().isNotEmpty) 'keyword': _searchController.text.trim(),
        }),
        Api.get<Map<String, dynamic>>('/materials/stock/changes', {
          'page': 1,
          'size': 100,
          if (_bizFilter.isNotEmpty) 'bizType': _bizFilter,
        }),
      ]);
      final stockPage = PageData.from<Map<String, dynamic>>(results[0], (json) => json);
      final changePage = PageData.from<Map<String, dynamic>>(results[1], (json) => json);
      if (!mounted) return;
      setState(() {
        _stocks = stockPage.records;
        _changes = changePage.records;
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
        _error = '教材库存加载失败';
      });
    }
  }

  Future<void> _openCreate() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
        child: const _ChangeSheet(),
      ),
    );
    if (created == true) {
      if (!mounted) return;
      showToast(context, '出入库登记成功');
      await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('教材出入库'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded), tooltip: '刷新'),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
            child: PillTabs(
              labels: const ['库存', '出入库记录'],
              index: _tab,
              padding: EdgeInsets.zero,
              onChanged: (value) => setState(() => _tab = value),
            ),
          ),
          if (_tab == 0)
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
              child: TextField(
                controller: _searchController,
                decoration: const InputDecoration(hintText: '搜索教材名称', prefixIcon: Icon(Icons.search_rounded, size: 19)),
                style: const TextStyle(fontSize: 13.5),
                onSubmitted: (_) => _load(),
                onChanged: (value) {
                  if (value.isEmpty) _load();
                },
              ),
            )
          else
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
              child: Row(
                children: [
                  for (final option in const ['', '入库', '出库']) ...[
                    ChoiceChip(
                      label: Text(option.isEmpty ? '全部' : option),
                      selected: _bizFilter == option,
                      onSelected: (_) {
                        setState(() => _bizFilter = option);
                        _load();
                      },
                      labelStyle: TextStyle(
                        fontSize: 12,
                        color: _bizFilter == option ? AppColors.primary : AppColors.muted,
                      ),
                      selectedColor: AppColors.primarySoft,
                      showCheckmark: false,
                    ),
                    const SizedBox(width: 8),
                  ],
                ],
              ),
            ),
          Expanded(child: _body()),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _openCreate,
        icon: const Icon(Icons.swap_vert_rounded),
        label: const Text('新增出入库'),
      ),
    );
  }

  Widget _body() {
    if (_loading) return const LoadingView();
    if (_error != null) return ErrorView(_error!, onRetry: _load);
    return RefreshIndicator(
      onRefresh: _load,
      child: _tab == 0 ? _stockList() : _changeList(),
    );
  }

  Widget _stockList() {
    if (_stocks.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无库存数据', subtitle: '请在教材设置中新建教材，或登记入库', icon: Icons.inventory_2_outlined)],
      );
    }
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 90),
      itemCount: _stocks.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final item = _stocks[index];
        final total = doubleOf(item['total']);
        return SurfaceCard(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      str(item['name'], '未命名教材'),
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                  ),
                  Text(
                    '合计 ${countText(total)}',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: total <= 0 ? AppColors.danger : AppColors.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(child: _stockCell('总库', doubleOf(item['warehouse']))),
                  Expanded(child: _stockCell('鲅鱼圈', doubleOf(item['bayuquan']))),
                  Expanded(child: _stockCell('新民', doubleOf(item['xinmin']))),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                '${str(item['category'], '未分类')} · ${str(item['status'], '正常')}',
                style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _stockCell(String label, double value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
        const SizedBox(height: 2),
        Text(
          countText(value),
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: value <= 0 ? AppColors.muted : AppColors.text),
        ),
      ],
    );
  }

  Widget _changeList() {
    if (_changes.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无出入库记录', subtitle: '点击右下角「新增出入库」登记入库或出库', icon: Icons.receipt_long_outlined)],
      );
    }
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 90),
      itemCount: _changes.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final item = _changes[index];
        final quantity = intOf(item['quantity']);
        final isIn = quantity >= 0 || str(item['bizType']) == '入库';
        return SurfaceCard(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: isIn ? AppColors.success.withValues(alpha: 0.12) : AppColors.danger.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      str(item['bizType'], isIn ? '入库' : '出库'),
                      style: TextStyle(fontSize: 11, color: isIn ? AppColors.success : AppColors.danger),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      str(item['materialName'], '—'),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                  ),
                  Text(
                    '${isIn ? '+' : ''}$quantity',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: isIn ? AppColors.success : AppColors.danger),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                '${str(item['roomName'], '—')} · ${str(item['changeDate'], '—')} · 单价 ${money(doubleOf(item['unitPrice']))} 元 · 经办人 ${str(item['handler'], '—')}',
                style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
              ),
              if (str(item['studentName']).isNotEmpty || str(item['remark']).isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  [
                    if (str(item['studentName']).isNotEmpty) '学员：${str(item['studentName'])}',
                    if (str(item['remark']).isNotEmpty) str(item['remark']),
                  ].join(' · '),
                  style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}

/// 新增出入库表单。
class _ChangeSheet extends StatefulWidget {
  const _ChangeSheet();

  @override
  State<_ChangeSheet> createState() => _ChangeSheetState();
}

class _ChangeSheetState extends State<_ChangeSheet> {
  final _quantity = TextEditingController(text: '1');
  final _unitPrice = TextEditingController();
  final _remark = TextEditingController();

  List<String> _rooms = const [];
  List<String> _materials = const [];
  List<Map<String, dynamic>> _settings = const [];
  String _room = '';
  String _materialName = '';
  String _bizType = '入库';
  DateTime _changeDate = DateTime.now();
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
    _remark.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/materials/options'),
        Api.get<Map<String, dynamic>>('/materials/settings', {'status': '正常', 'page': 1, 'size': 500}),
      ]);
      final options = results[0];
      final settingsPage = PageData.from<Map<String, dynamic>>(results[1], (json) => json);
      final rooms = (options['rooms'] as List?)?.map((e) => '$e').toList() ?? const <String>[];
      final materials = mapList(options['materials'])
          .where((item) => str(item['status']) != '停用')
          .map((item) => str(item['name']))
          .where((name) => name.isNotEmpty)
          .toList();
      if (!mounted) return;
      setState(() {
        _rooms = rooms;
        _materials = materials;
        _settings = settingsPage.records;
        _room = rooms.isEmpty ? '' : rooms.first;
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
        _error = '出入库选项加载失败';
      });
    }
  }

  double get _defaultPrice {
    final matched = _settings.where((item) => str(item['name']) == _materialName).toList();
    if (matched.isEmpty) return 0;
    return doubleOf(_bizType == '入库' ? matched.first['costPrice'] : matched.first['salePrice']);
  }

  Future<void> _pickRoom() async {
    final picked = await showOptionPicker<String>(
      context: context,
      title: '选择仓库 / 校区',
      items: _rooms,
      labelOf: (item) => item,
      selected: _room,
    );
    if (picked != null) setState(() => _room = picked);
  }

  Future<void> _pickMaterial() async {
    final picked = await showOptionPicker<String>(
      context: context,
      title: '选择教材',
      items: _materials,
      labelOf: (item) => item,
      selected: _materials.contains(_materialName) ? _materialName : null,
    );
    if (picked == null) return;
    setState(() {
      _materialName = picked;
      _unitPrice.text = amountInput(_defaultPrice);
    });
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _changeDate,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (picked != null) setState(() => _changeDate = picked);
  }

  Future<void> _submit() async {
    if (_room.isEmpty) {
      showToast(context, '请选择仓库');
      return;
    }
    if (_materialName.isEmpty) {
      showToast(context, '请选择教材');
      return;
    }
    final quantity = doubleOf(_quantity.text);
    if (quantity <= 0) {
      showToast(context, '请填写正确的数量');
      return;
    }
    setState(() => _submitting = true);
    try {
      await Api.post<Map<String, dynamic>>('/materials/stock/changes', {
        'roomName': _room,
        'materialName': _materialName,
        'bizType': _bizType,
        'quantity': quantity.round(),
        if (doubleOf(_unitPrice.text) > 0) 'unitPrice': round2(doubleOf(_unitPrice.text)),
        'changeDate': dateText(_changeDate),
        if (_remark.text.trim().isNotEmpty) 'remark': _remark.text.trim(),
      });
      if (!mounted) return;
      Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      showToast(context, e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
        child: _loading
            ? const SizedBox(height: 260, child: LoadingView())
            : _error != null
                ? SizedBox(height: 260, child: ErrorView(_error!, onRetry: _load))
                : Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text('新增出入库', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          for (final option in const ['入库', '出库']) ...[
                            Expanded(
                              child: GestureDetector(
                                onTap: () => setState(() {
                                  _bizType = option;
                                  if (_unitPrice.text.isEmpty || _unitPrice.text == '0') {
                                    _unitPrice.text = amountInput(_defaultPrice);
                                  }
                                }),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(vertical: 10),
                                  decoration: BoxDecoration(
                                    color: _bizType == option ? AppColors.primarySoft : Colors.white,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: _bizType == option ? AppColors.primary : AppColors.border),
                                  ),
                                  alignment: Alignment.center,
                                  child: Text(
                                    option,
                                    style: TextStyle(
                                      fontSize: 13,
                                      fontWeight: FontWeight.w600,
                                      color: _bizType == option ? AppColors.primary : AppColors.muted,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            if (option == '入库') const SizedBox(width: 10),
                          ],
                        ],
                      ),
                      const SizedBox(height: 12),
                      PickerField(label: '仓库', value: _room, hint: '请选择仓库', onTap: _pickRoom),
                      const SizedBox(height: 10),
                      PickerField(
                        label: '教材',
                        value: _materialName,
                        hint: _materials.isEmpty ? '请先在教材设置中新建' : '请选择教材',
                        enabled: _materials.isNotEmpty,
                        onTap: _pickMaterial,
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _quantity,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(labelText: '数量'),
                              style: const TextStyle(fontSize: 14),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: TextField(
                              controller: _unitPrice,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: InputDecoration(labelText: '单价', prefixText: '¥ ', hintText: money(_defaultPrice)),
                              style: const TextStyle(fontSize: 14),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      PickerField(label: '日期', value: dateText(_changeDate), hint: '请选择日期', onTap: _pickDate),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _remark,
                        maxLines: 2,
                        decoration: const InputDecoration(hintText: '备注（可选）'),
                        style: const TextStyle(fontSize: 13.5),
                      ),
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _submitting ? null : _submit,
                          child: _submitting
                              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                              : const Text('确认保存'),
                        ),
                      ),
                    ],
                  ),
      ),
    );
  }
}
