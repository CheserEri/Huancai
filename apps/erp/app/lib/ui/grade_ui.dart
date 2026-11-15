import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/models.dart';
import 'theme.dart';

/// 数值展示：空值显示 -，整数不带小数，其余保留指定小数位。
String scoreText(num? value, {int digits = 1}) {
  if (value == null) return '-';
  if (value == value.roundToDouble()) return value.toStringAsFixed(0);
  return value.toStringAsFixed(digits);
}

String percentText(num? value) => value == null ? '-' : '${scoreText(value)}%';

/// 返回第一个满足条件的元素，没有时返回 null。
T? firstOrNullWhere<T>(Iterable<T> items, bool Function(T item) test) {
  for (final item in items) {
    if (test(item)) return item;
  }
  return null;
}

/// 分项权重：优先取配置值，旧模板缺少权重时按分项满分占比折算。
num? templatePartWeight(List<GradePart> parts, GradePart part) {
  if (part.weight != null) return part.weight;
  final total = parts.fold<num>(0, (sum, item) => sum + (item.fullScore ?? 0));
  if (total == 0 || part.fullScore == null) return null;
  return part.fullScore! * 100 / total;
}

/// 得分率（0-100）。
double? scoreRateOf(num? score, num fullScore) {
  if (score == null || fullScore == 0) return null;
  return score * 100 / fullScore;
}

const gradeLevels = ['优秀', '良好', '合格', '待提高'];

Color levelColor(String level) {
  switch (level) {
    case '优秀':
      return AppColors.success;
    case '良好':
      return AppColors.primary;
    case '合格':
      return AppColors.warning;
    case '待提高':
      return AppColors.danger;
    default:
      return AppColors.sub;
  }
}

Color levelBackground(String level) {
  switch (level) {
    case '优秀':
      return AppColors.successSoft;
    case '良好':
      return AppColors.primarySoft;
    case '合格':
      return AppColors.warningSoft;
    case '待提高':
      return AppColors.dangerSoft;
    default:
      return const Color(0xFFF2F3F5);
  }
}

/// 项目符号 + 文本，用于卡片内的次要说明。
class GradeHintLine extends StatelessWidget {
  const GradeHintLine(this.text, {super.key, this.color = AppColors.muted});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 4,
          height: 4,
          margin: const EdgeInsets.only(top: 7),
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 7),
        Expanded(
          child: Text(text, style: TextStyle(fontSize: 11.5, color: color, height: 1.6)),
        ),
      ],
    );
  }
}

/// 进度标签：已录 / 应考。
class GradeProgressTag extends StatelessWidget {
  const GradeProgressTag({super.key, required this.entered, required this.total});

  final int entered;
  final int total;

  @override
  Widget build(BuildContext context) {
    final done = total > 0 && entered >= total;
    return Text(
      '已录 $entered/$total 人',
      style: TextStyle(
        fontSize: 11.5,
        color: done ? AppColors.success : AppColors.sub,
        fontWeight: done ? FontWeight.w600 : FontWeight.w400,
      ),
    );
  }
}

/// 一条趋势线的数据。
class GradeTrendSeries {
  const GradeTrendSeries({required this.name, required this.color, required this.values});

  final String name;
  final Color color;

  /// 百分比数值（0-100），空值表示该场无数据。
  final List<double?> values;
}

/// 成绩趋势折线图（自绘，避免引入第三方图表库）。
class GradeTrendChart extends StatelessWidget {
  const GradeTrendChart({super.key, required this.labels, required this.series, this.height = 170});

  final List<String> labels;
  final List<GradeTrendSeries> series;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 14,
          runSpacing: 6,
          children: [
            for (final item in series)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(width: 8, height: 8, decoration: BoxDecoration(color: item.color, shape: BoxShape.circle)),
                  const SizedBox(width: 5),
                  Text(item.name, style: const TextStyle(fontSize: 11, color: AppColors.muted)),
                ],
              ),
          ],
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: height,
          width: double.infinity,
          child: CustomPaint(
            painter: _GradeTrendPainter(labels: labels, series: series),
          ),
        ),
      ],
    );
  }
}

class _GradeTrendPainter extends CustomPainter {
  _GradeTrendPainter({required this.labels, required this.series});

  final List<String> labels;
  final List<GradeTrendSeries> series;

  @override
  void paint(Canvas canvas, Size size) {
    const left = 30.0;
    const right = 8.0;
    const top = 8.0;
    const bottom = 22.0;
    final chartWidth = math.max(size.width - left - right, 1);
    final chartHeight = math.max(size.height - top - bottom, 1);
    final gridPaint = Paint()
      ..color = AppColors.border
      ..strokeWidth = 1;

    for (var i = 0; i <= 4; i++) {
      final y = top + chartHeight * i / 4;
      canvas.drawLine(Offset(left, y), Offset(left + chartWidth, y), gridPaint);
      _drawText(canvas, '${100 - i * 25}%', Offset(0, y - 6), 9.5, AppColors.muted);
    }

    final count = labels.length;
    if (count == 0) return;
    double xOf(int index) => count == 1 ? left + chartWidth / 2 : left + chartWidth * index / (count - 1);
    double yOf(double value) => top + chartHeight * (1 - value.clamp(0, 100) / 100);

    for (final item in series) {
      final linePaint = Paint()
        ..color = item.color
        ..strokeWidth = 2
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round;
      final dotPaint = Paint()..color = item.color;
      Path? path;
      for (var i = 0; i < item.values.length && i < count; i++) {
        final value = item.values[i];
        if (value == null) {
          if (path != null) {
            canvas.drawPath(path, linePaint);
            path = null;
          }
          continue;
        }
        final point = Offset(xOf(i), yOf(value));
        if (path == null) {
          path = Path()..moveTo(point.dx, point.dy);
        } else {
          path.lineTo(point.dx, point.dy);
        }
        canvas.drawCircle(point, 2.6, dotPaint);
      }
      if (path != null) canvas.drawPath(path, linePaint);
    }

    final step = count <= 6 ? 1 : (count / 5).ceil();
    for (var i = 0; i < count; i++) {
      if (i % step != 0 && i != count - 1) continue;
      final text = labels[i];
      final tp = _textPainter(text, 9.5, AppColors.muted);
      final x = (xOf(i) - tp.width / 2).clamp(left - 10, size.width - tp.width);
      tp.paint(canvas, Offset(x, top + chartHeight + 6));
    }
  }

  void _drawText(Canvas canvas, String text, Offset offset, double fontSize, Color color) {
    _textPainter(text, fontSize, color).paint(canvas, offset);
  }

  TextPainter _textPainter(String text, double fontSize, Color color) {
    return TextPainter(
      text: TextSpan(text: text, style: TextStyle(fontSize: fontSize, color: color)),
      textDirection: TextDirection.ltr,
    )..layout();
  }

  @override
  bool shouldRepaint(covariant _GradeTrendPainter oldDelegate) => oldDelegate.labels != labels || oldDelegate.series != series;
}
