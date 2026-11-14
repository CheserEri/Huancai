import 'package:flutter/material.dart';

import 'theme.dart';

void showToast(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

Future<bool> confirmDialog(BuildContext context, String title, String content) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      title: Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
      content: Text(content, style: const TextStyle(fontSize: 14, color: AppColors.sub, height: 1.6)),
      actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          style: TextButton.styleFrom(foregroundColor: AppColors.sub),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          style: FilledButton.styleFrom(minimumSize: const Size(88, 40)),
          child: const Text('确定'),
        ),
      ],
    ),
  );
  return result ?? false;
}

class SurfaceCard extends StatelessWidget {
  const SurfaceCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.onTap,
    this.margin,
    this.background,
    this.border = true,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry? margin;
  final Color? background;
  final bool border;

  @override
  Widget build(BuildContext context) {
    final content = Container(
      margin: margin,
      decoration: BoxDecoration(
        color: background ?? AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: border ? Border.all(color: AppColors.border) : null,
      ),
      child: Padding(padding: padding, child: child),
    );
    if (onTap == null) return content;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: content,
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.title, {super.key, this.trailing, this.padding = const EdgeInsets.fromLTRB(20, 22, 20, 10)});

  final String title;
  final Widget? trailing;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding,
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}

class StatusTag extends StatelessWidget {
  const StatusTag(this.text, {super.key, this.color, this.background, this.compact = false});

  final String text;
  final Color? color;
  final Color? background;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final palette = statusColors(text);
    final fg = color ?? palette.color;
    final bg = background ?? palette.background;
    return Container(
      padding: EdgeInsets.symmetric(horizontal: compact ? 6 : 8, vertical: compact ? 2 : 3),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
      child: Text(
        text,
        style: TextStyle(fontSize: compact ? 11 : 12, color: fg, fontWeight: FontWeight.w500, height: 1.3),
      ),
    );
  }
}

class AvatarBadge extends StatelessWidget {
  const AvatarBadge(this.name, {super.key, this.size = 44, this.color});

  final String name;
  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final text = name.isEmpty ? '?' : name.characters.first;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color ?? AppColors.primarySoft,
        borderRadius: BorderRadius.circular(size / 2.6),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: size * 0.4,
          fontWeight: FontWeight.w600,
          color: color == null ? AppColors.primary : Colors.white,
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, this.title = '暂无数据', this.subtitle, this.icon = Icons.inbox_outlined, this.action});

  final String title;
  final String? subtitle;
  final IconData icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: const Color(0xFFF1F3F6), borderRadius: BorderRadius.circular(20)),
              child: Icon(icon, size: 28, color: AppColors.muted),
            ),
            const SizedBox(height: 14),
            Text(title, style: const TextStyle(fontSize: 14, color: AppColors.sub, fontWeight: FontWeight.w500)),
            if (subtitle != null) ...[
              const SizedBox(height: 6),
              Text(
                subtitle!,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: AppColors.muted, height: 1.6),
              ),
            ],
            if (action != null) ...[
              const SizedBox(height: 16),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView(this.message, {super.key, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return EmptyState(
      icon: Icons.cloud_off_rounded,
      title: '数据加载失败',
      subtitle: message,
      action: onRetry == null
          ? null
          : OutlinedButton(onPressed: onRetry, child: const Text('重新加载')),
    );
  }
}

class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.height = 220});

  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: const Center(
        child: SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)),
      ),
    );
  }
}

class SearchBarField extends StatelessWidget {
  const SearchBarField({
    super.key,
    required this.controller,
    required this.onSubmitted,
    this.hint = '搜索',
    this.onChanged,
  });

  final TextEditingController controller;
  final ValueChanged<String> onSubmitted;
  final String hint;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      textInputAction: TextInputAction.search,
      onSubmitted: onSubmitted,
      onChanged: onChanged,
      style: const TextStyle(fontSize: 14),
      decoration: InputDecoration(
        hintText: hint,
        prefixIcon: const Icon(Icons.search_rounded, size: 20, color: AppColors.muted),
        suffixIcon: controller.text.isEmpty
            ? null
            : IconButton(
                icon: const Icon(Icons.close_rounded, size: 18, color: AppColors.muted),
                onPressed: () {
                  controller.clear();
                  onSubmitted('');
                },
              ),
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.symmetric(vertical: 11),
      ),
    );
  }
}

class PillTabs extends StatelessWidget {
  const PillTabs({super.key, required this.labels, required this.index, required this.onChanged, this.padding});

  final List<String> labels;
  final int index;
  final ValueChanged<int> onChanged;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: padding ?? const EdgeInsets.symmetric(horizontal: 20),
      child: Row(
        children: [
          for (var i = 0; i < labels.length; i++)
            Padding(
              padding: EdgeInsets.only(right: i == labels.length - 1 ? 0 : 8),
              child: GestureDetector(
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 160),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: i == index ? AppColors.primary : Colors.white,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: i == index ? AppColors.primary : AppColors.border),
                  ),
                  child: Text(
                    labels[i],
                    style: TextStyle(
                      fontSize: 13,
                      color: i == index ? Colors.white : AppColors.sub,
                      fontWeight: i == index ? FontWeight.w600 : FontWeight.w400,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class InfoRow extends StatelessWidget {
  const InfoRow(this.label, this.value, {super.key, this.valueColor, this.trailing});

  final String label;
  final String value;
  final Color? valueColor;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 84,
            child: Text(label, style: const TextStyle(fontSize: 13, color: AppColors.muted, height: 1.5)),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(fontSize: 13, color: valueColor ?? AppColors.text, height: 1.5),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}

class MetricTile extends StatelessWidget {
  const MetricTile({
    super.key,
    required this.label,
    required this.value,
    this.unit,
    this.color = AppColors.text,
    this.align = CrossAxisAlignment.start,
  });

  final String label;
  final String value;
  final String? unit;
  final Color color;
  final CrossAxisAlignment align;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: align,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
        const SizedBox(height: 6),
        Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Flexible(
              child: Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 21, fontWeight: FontWeight.w600, color: color, height: 1.1),
              ),
            ),
            if (unit != null) ...[
              const SizedBox(width: 2),
              Text(unit!, style: const TextStyle(fontSize: 11, color: AppColors.muted)),
            ],
          ],
        ),
      ],
    );
  }
}

class QuickEntry extends StatelessWidget {
  const QuickEntry({super.key, required this.icon, required this.label, required this.onTap, required this.color, required this.background});

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color color;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 46,
              height: 46,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(14)),
              child: Icon(icon, size: 22, color: color),
            ),
            const SizedBox(height: 8),
            Text(label, style: const TextStyle(fontSize: 12, color: AppColors.sub)),
          ],
        ),
      ),
    );
  }
}

/// 简易进度条（用于排行/出勤率等）。
class MiniBar extends StatelessWidget {
  const MiniBar({super.key, required this.value, this.color = AppColors.primary, this.height = 6});

  final double value;
  final Color color;
  final double height;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(height),
      child: LinearProgressIndicator(
        value: value.clamp(0, 1),
        minHeight: height,
        backgroundColor: AppColors.border,
        valueColor: AlwaysStoppedAnimation(color),
      ),
    );
  }
}

/// 单日/单条数据为空时的内联提示，避免整页空白。
class InlineHint extends StatelessWidget {
  const InlineHint(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
      child: Text(text, style: const TextStyle(fontSize: 12, color: AppColors.muted, height: 1.6)),
    );
  }
}
