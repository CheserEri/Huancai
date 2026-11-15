import 'package:flutter/material.dart';

import '../../ui/theme.dart';

/// 金额统一保留两位小数，避免浮点累加误差。
double round2(num value) => (value * 100).roundToDouble() / 100;

/// 输入框回填用的金额文本：整数不带小数，其余保留两位。
String amountInput(num value) {
  final fixed = round2(value);
  return fixed == fixed.roundToDouble() ? fixed.toStringAsFixed(0) : fixed.toStringAsFixed(2);
}

/// 表单里的可选字段：展示标签与当前值，点击唤起底部选择器。
class PickerField extends StatelessWidget {
  const PickerField({
    super.key,
    required this.label,
    required this.value,
    required this.onTap,
    this.hint = '请选择',
    this.enabled = true,
    this.trailingText,
  });

  final String label;
  final String? value;
  final VoidCallback onTap;
  final String hint;
  final bool enabled;
  final String? trailingText;

  @override
  Widget build(BuildContext context) {
    final empty = (value ?? '').isEmpty;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: enabled ? onTap : null,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
        decoration: BoxDecoration(
          color: enabled ? Colors.white : const Color(0xFFF7F8FA),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            Text(label, style: const TextStyle(fontSize: 13, color: AppColors.muted)),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                empty ? hint : value!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.right,
                style: TextStyle(
                  fontSize: 13.5,
                  color: empty ? AppColors.muted : AppColors.text,
                  fontWeight: empty ? FontWeight.w400 : FontWeight.w500,
                ),
              ),
            ),
            if (trailingText != null) ...[
              const SizedBox(width: 8),
              Text(trailingText!, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
            ],
            const SizedBox(width: 4),
            const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
          ],
        ),
      ),
    );
  }
}

/// 通用单选底部弹层（带搜索），用于课程、班级、模板、教材等长列表选择。
class OptionPickerSheet<T> extends StatefulWidget {
  const OptionPickerSheet({
    super.key,
    required this.title,
    required this.items,
    required this.labelOf,
    this.subtitleOf,
    this.selected,
  });

  final String title;
  final List<T> items;
  final String Function(T item) labelOf;
  final String Function(T item)? subtitleOf;
  final T? selected;

  @override
  State<OptionPickerSheet<T>> createState() => _OptionPickerSheetState<T>();
}

class _OptionPickerSheetState<T> extends State<OptionPickerSheet<T>> {
  String _keyword = '';

  @override
  Widget build(BuildContext context) {
    final items = widget.items.where((item) => widget.labelOf(item).contains(_keyword)).toList();
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.62,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(widget.title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                decoration: const InputDecoration(hintText: '输入关键词搜索', prefixIcon: Icon(Icons.search_rounded, size: 19)),
                onChanged: (value) => setState(() => _keyword = value.trim()),
                style: const TextStyle(fontSize: 13.5),
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: items.isEmpty
                  ? const Center(child: Text('没有匹配的选项', style: TextStyle(fontSize: 13, color: AppColors.muted)))
                  : ListView.builder(
                      itemCount: items.length,
                      itemBuilder: (context, index) {
                        final item = items[index];
                        final subtitle = widget.subtitleOf?.call(item) ?? '';
                        final active = identical(item, widget.selected);
                        return ListTile(
                          dense: true,
                          title: Text(
                            widget.labelOf(item),
                            style: TextStyle(fontSize: 13.5, color: active ? AppColors.primary : AppColors.text, fontWeight: active ? FontWeight.w600 : FontWeight.w400),
                          ),
                          subtitle: subtitle.isEmpty
                              ? null
                              : Text(subtitle, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                          trailing: active ? const Icon(Icons.check_rounded, size: 18, color: AppColors.primary) : null,
                          onTap: () => Navigator.pop(context, item),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

Future<T?> showOptionPicker<T>({
  required BuildContext context,
  required String title,
  required List<T> items,
  required String Function(T item) labelOf,
  String Function(T item)? subtitleOf,
  T? selected,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    builder: (context) => OptionPickerSheet<T>(
      title: title,
      items: items,
      labelOf: labelOf,
      subtitleOf: subtitleOf,
      selected: selected,
    ),
  );
}

/// 通用多选底部弹层（带搜索），用于优惠模板等多选叠加场景。
class MultiOptionPickerSheet<T> extends StatefulWidget {
  const MultiOptionPickerSheet({
    super.key,
    required this.title,
    required this.items,
    required this.labelOf,
    this.subtitleOf,
    this.selected = const [],
  });

  final String title;
  final List<T> items;
  final String Function(T item) labelOf;
  final String Function(T item)? subtitleOf;
  final List<T> selected;

  @override
  State<MultiOptionPickerSheet<T>> createState() => _MultiOptionPickerSheetState<T>();
}

class _MultiOptionPickerSheetState<T> extends State<MultiOptionPickerSheet<T>> {
  String _keyword = '';
  late final Set<T> _selected = {...widget.selected};

  @override
  Widget build(BuildContext context) {
    final items = widget.items.where((item) => widget.labelOf(item).contains(_keyword)).toList();
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.72,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(widget.title,
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
                  ),
                  if (_selected.isNotEmpty)
                    TextButton(
                      onPressed: () => setState(_selected.clear),
                      child: const Text('清空', style: TextStyle(fontSize: 13)),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                decoration: const InputDecoration(hintText: '输入关键词搜索', prefixIcon: Icon(Icons.search_rounded, size: 19)),
                onChanged: (value) => setState(() => _keyword = value.trim()),
                style: const TextStyle(fontSize: 13.5),
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: items.isEmpty
                  ? const Center(child: Text('没有匹配的选项', style: TextStyle(fontSize: 13, color: AppColors.muted)))
                  : ListView.builder(
                      itemCount: items.length,
                      itemBuilder: (context, index) {
                        final item = items[index];
                        final subtitle = widget.subtitleOf?.call(item) ?? '';
                        final active = _selected.contains(item);
                        return ListTile(
                          dense: true,
                          leading: Icon(
                            active ? Icons.check_box_rounded : Icons.check_box_outline_blank_rounded,
                            size: 20,
                            color: active ? AppColors.primary : AppColors.muted,
                          ),
                          title: Text(
                            widget.labelOf(item),
                            style: TextStyle(
                              fontSize: 13.5,
                              color: active ? AppColors.primary : AppColors.text,
                              fontWeight: active ? FontWeight.w600 : FontWeight.w400,
                            ),
                          ),
                          subtitle: subtitle.isEmpty
                              ? null
                              : Text(subtitle, style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                          onTap: () => setState(() {
                            if (!_selected.remove(item)) _selected.add(item);
                          }),
                        );
                      },
                    ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(context, _selected.toList()),
                  child: Text(_selected.isEmpty ? '不使用优惠' : '确定（已选 ${_selected.length} 项）'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

Future<List<T>?> showMultiOptionPicker<T>({
  required BuildContext context,
  required String title,
  required List<T> items,
  required String Function(T item) labelOf,
  String Function(T item)? subtitleOf,
  List<T> selected = const [],
}) {
  return showModalBottomSheet<List<T>>(
    context: context,
    isScrollControlled: true,
    builder: (context) => MultiOptionPickerSheet<T>(
      title: title,
      items: items,
      labelOf: labelOf,
      subtitleOf: subtitleOf,
      selected: selected,
    ),
  );
}

/// 缴费成功弹窗：强提示订单号，避免提示不明显导致重复提交。
Future<void> showPaymentSuccessDialog(
  BuildContext context, {
  required String title,
  required String orderNo,
  String? summary,
}) {
  return showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (context) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      contentPadding: const EdgeInsets.fromLTRB(20, 26, 20, 6),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: const BoxDecoration(color: Color(0xFFE8F7EE), shape: BoxShape.circle),
            child: const Icon(Icons.check_rounded, size: 34, color: AppColors.success),
          ),
          const SizedBox(height: 14),
          Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: AppColors.text)),
          const SizedBox(height: 8),
          Text('订单号 $orderNo', style: const TextStyle(fontSize: 13, color: AppColors.muted)),
          if (summary != null) ...[
            const SizedBox(height: 4),
            Text(summary, style: const TextStyle(fontSize: 13, color: AppColors.muted)),
          ],
        ],
      ),
      actionsPadding: const EdgeInsets.fromLTRB(20, 10, 20, 14),
      actions: [
        SizedBox(
          width: double.infinity,
          child: FilledButton(onPressed: () => Navigator.pop(context), child: const Text('完成')),
        ),
      ],
    ),
  );
}

/// 表单底部的提交按钮，统一处理提交中状态。
class SubmitBar extends StatelessWidget {
  const SubmitBar({super.key, required this.label, required this.submitting, required this.onSubmit});

  final String label;
  final bool submitting;
  final VoidCallback? onSubmit;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
        child: SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: submitting ? null : onSubmit,
            child: submitting
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : Text(label),
          ),
        ),
      ),
    );
  }
}
