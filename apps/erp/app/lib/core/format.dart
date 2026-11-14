/// 数据解析与展示格式化工具。
///
/// 后端字段可能为 null，这里统一做安全兜底，避免页面因个别缺失字段报错。
String str(dynamic value, [String fallback = '']) {
  if (value == null) return fallback;
  final text = '$value';
  return text.isEmpty ? fallback : text;
}

String? strOrNull(dynamic value) {
  if (value == null) return null;
  final text = '$value';
  return text.isEmpty ? null : text;
}

int intOf(dynamic value, [int fallback = 0]) {
  if (value is int) return value;
  if (value is num) return value.round();
  return int.tryParse(str(value)) ?? fallback;
}

num? numOrNull(dynamic value) {
  if (value == null) return null;
  if (value is num) return value;
  return num.tryParse(str(value));
}

double doubleOf(dynamic value, [double fallback = 0]) => numOrNull(value)?.toDouble() ?? fallback;

List<Map<String, dynamic>> mapList(dynamic value) {
  if (value is List) {
    return value.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }
  return const [];
}

List<int> intList(dynamic value) {
  if (value is List) return value.map((e) => intOf(e)).toList();
  if (value is String && value.isNotEmpty) {
    return value.split(',').map((e) => intOf(e)).toList();
  }
  return const [];
}

String _thousand(String digits) {
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buffer.write(',');
    buffer.write(digits[i]);
  }
  return buffer.toString();
}

/// 金额：整数不带小数，小数保留两位。
String money(num? value) {
  if (value == null) return '0';
  final negative = value < 0;
  final abs = value.abs();
  final fixed = abs == abs.roundToDouble() ? abs.toStringAsFixed(0) : abs.toStringAsFixed(2);
  final parts = fixed.split('.');
  final text = _thousand(parts[0]) + (parts.length > 1 ? '.${parts[1]}' : '');
  return negative ? '-$text' : text;
}

String yuan(num? value) => '¥${money(value)}';

/// 数字占位：0 也正常显示。
String countText(num? value) => money(value);

String _two(int value) => value.toString().padLeft(2, '0');

String dateOnly(String? value) {
  if (value == null || value.isEmpty) return '-';
  if (value.length >= 10) return value.substring(0, 10);
  return value;
}

String dateTimeText(String? value) {
  if (value == null || value.isEmpty) return '-';
  if (value.length >= 16) return value.substring(0, 16).replaceFirst('T', ' ');
  return value;
}

String timeOnly(String? value) {
  if (value == null || value.isEmpty) return '-';
  if (value.contains(' ')) return value.split(' ').last.substring(0, 5);
  if (value.contains('T')) return value.split('T').last.substring(0, 5);
  return value;
}

String todayText() {
  final now = DateTime.now();
  return '${now.year}-${_two(now.month)}-${_two(now.day)}';
}

String dateText(DateTime date) => '${date.year}-${_two(date.month)}-${_two(date.day)}';

String compactDate(DateTime date) => '${_two(date.month)}-${_two(date.day)}';

String weekdayCn(DateTime date) => const ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][date.weekday - 1];

bool sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

DateTime? parseDate(String? value) {
  if (value == null || value.isEmpty) return null;
  try {
    return DateTime.parse(value.replaceFirst(' ', 'T'));
  } catch (_) {
    return null;
  }
}

String greetingText() {
  final hour = DateTime.now().hour;
  if (hour < 6) return '凌晨好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}
