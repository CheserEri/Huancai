import 'package:flutter_test/flutter_test.dart';
import 'package:huancai_app/core/format.dart';
import 'package:huancai_app/core/models.dart';

void main() {
  test('金额格式化保留千分位并省略整数小数位', () {
    expect(money(1280), '1,280');
    expect(money(1234.5), '1,234.50');
    expect(money(-30), '-30');
    expect(money(null), '0');
  });

  test('日期截取兼容后端时间格式', () {
    expect(dateOnly('2026-11-13 10:20:30'), '2026-11-13');
    expect(dateOnly(null), '-');
    expect(dateTimeText('2026-11-13 10:20:30'), '2026-11-13 10:20');
  });

  test('分页结果解析兼容 records/list 两种字段', () {
    final page = PageData.from<Map<String, dynamic>>(
      {
        'records': [
          {'id': 1},
        ],
        'total': 5,
      },
      (json) => json,
    );
    expect(page.records.length, 1);
    expect(page.total, 5);
  });
}
