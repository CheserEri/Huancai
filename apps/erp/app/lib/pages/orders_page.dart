import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'order_detail_page.dart';

class OrdersPage extends StatefulWidget {
  const OrdersPage({super.key});

  @override
  State<OrdersPage> createState() => _OrdersPageState();
}

class _OrdersPageState extends State<OrdersPage> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();
  Timer? _debounce;

  final List<OrderInfo> _orders = [];
  OrderSummary _summary = OrderSummary(0, 0, 0, 0);
  String _keyword = '';
  String _range = '近30天';
  int _page = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  static const _ranges = ['今天', '近7天', '近30天', '本月'];

  bool get _hasMore => _orders.length < _total;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scrollController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter < 260 && !_loadingMore && _hasMore && !_loading) {
      _loadMore();
    }
  }

  (String, String) _dealRange() {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    switch (_range) {
      case '今天':
        return (dateText(today), dateText(today));
      case '近7天':
        return (dateText(today.subtract(const Duration(days: 6))), dateText(today));
      case '本月':
        return (dateText(DateTime(now.year, now.month, 1)), dateText(today));
      default:
        return (dateText(today.subtract(const Duration(days: 29))), dateText(today));
    }
  }

  Future<void> _load({bool reset = false}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 1;
      });
    }
    final range = _dealRange();
    try {
      final data = await Api.post<Map<String, dynamic>>('/sp-orders/query', {
        'pageIndex': reset ? 1 : _page,
        'pageSize': 20,
        'dealStart': range.$1,
        'dealEnd': range.$2,
        if (_keyword.isNotEmpty) 'keyword': _keyword,
      });
      final list = mapList(data['list']).map(OrderInfo.from).toList();
      if (!mounted) return;
      setState(() {
        if (reset) _orders.clear();
        _orders.addAll(list);
        _total = intOf(data['total']);
        _summary = OrderSummary.from(data['summary'] is Map ? Map<String, dynamic>.from(data['summary']) : const {});
        _page = (reset ? 1 : _page) + 1;
        _loading = false;
        _loadingMore = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        _error = '订单数据加载失败';
      });
    }
  }

  Future<void> _loadMore() async {
    setState(() => _loadingMore = true);
    await _load();
  }

  void _refresh() => _load(reset: true);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('订单')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 0),
            child: Column(
              children: [
                SearchBarField(
                  controller: _searchController,
                  hint: '搜索学员 / 订单号',
                  onSubmitted: (value) {
                    _keyword = value.trim();
                    _refresh();
                  },
                  onChanged: (value) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () {
                      _keyword = value.trim();
                      _refresh();
                    });
                  },
                ),
                const SizedBox(height: 12),
                PillTabs(
                  labels: _ranges,
                  index: _ranges.indexOf(_range),
                  padding: EdgeInsets.zero,
                  onChanged: (index) {
                    setState(() => _range = _ranges[index]);
                    _refresh();
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: SurfaceCard(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              child: Row(
                children: [
                  Expanded(child: MetricTile(label: '订单数', value: '$_total', unit: '笔')),
                  Expanded(child: MetricTile(label: '应收', value: money(_summary.shouldPay), align: CrossAxisAlignment.center)),
                  Expanded(child: MetricTile(label: '实收', value: money(_summary.realPay), align: CrossAxisAlignment.center, color: AppColors.success)),
                  Expanded(
                    child: MetricTile(
                      label: '欠费',
                      value: money(_summary.arrearage),
                      align: CrossAxisAlignment.end,
                      color: _summary.arrearage > 0 ? AppColors.danger : AppColors.text,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _loading
                ? const LoadingView()
                : _error != null
                    ? ErrorView(_error!, onRetry: _refresh)
                    : RefreshIndicator(
                        onRefresh: () async => _refresh(),
                        child: _orders.isEmpty
                            ? ListView(
                                physics: const AlwaysScrollableScrollPhysics(),
                                children: const [EmptyState(title: '暂无订单', subtitle: '当前时间范围内没有匹配的订单记录')],
                              )
                            : ListView.separated(
                                controller: _scrollController,
                                physics: const AlwaysScrollableScrollPhysics(),
                                padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
                                itemCount: _orders.length + 1,
                                separatorBuilder: (_, _) => const SizedBox(height: 10),
                                itemBuilder: (context, index) {
                                  if (index == _orders.length) {
                                    return Padding(
                                      padding: const EdgeInsets.symmetric(vertical: 16),
                                      child: Center(
                                        child: Text(
                                          _hasMore ? '加载中…' : '已加载全部 $_total 笔订单',
                                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                                        ),
                                      ),
                                    );
                                  }
                                  final order = _orders[index];
                                  return SurfaceCard(
                                    padding: const EdgeInsets.all(15),
                                    onTap: () => Navigator.of(context).push(
                                      MaterialPageRoute(builder: (_) => OrderDetailPage(order: order)),
                                    ),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          children: [
                                            Expanded(
                                              child: Row(
                                                children: [
                                                  Flexible(
                                                    child: Text(
                                                      order.stuName,
                                                      maxLines: 1,
                                                      overflow: TextOverflow.ellipsis,
                                                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.text),
                                                    ),
                                                  ),
                                                  const SizedBox(width: 8),
                                                  Text(
                                                    order.typeName,
                                                    style: const TextStyle(fontSize: 12, color: AppColors.muted),
                                                  ),
                                                  if (order.isLocal) ...[
                                                    const SizedBox(width: 6),
                                                    Container(
                                                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                                                      decoration: BoxDecoration(
                                                        color: AppColors.primarySoft,
                                                        borderRadius: BorderRadius.circular(4),
                                                      ),
                                                      child: const Text('本地', style: TextStyle(fontSize: 10, color: AppColors.primary)),
                                                    ),
                                                  ],
                                                ],
                                              ),
                                            ),
                                            StatusTag(order.statusName, compact: true),
                                          ],
                                        ),
                                        const SizedBox(height: 5),
                                        Text(
                                          '${order.orderNo} · ${dateTimeText(order.dealDate)}',
                                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                                        ),
                                        const SizedBox(height: 10),
                                        Row(
                                          children: [
                                            Text(
                                              '实收 ${yuan(order.realPay)}',
                                              style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                                            ),
                                            const SizedBox(width: 12),
                                            if (order.arrearage > 0)
                                              Text(
                                                '欠费 ${money(order.arrearage)}',
                                                style: const TextStyle(fontSize: 11.5, color: AppColors.danger),
                                              ),
                                            const Spacer(),
                                            Text(
                                              [order.campusName, order.creator ?? ''].where((e) => e.isNotEmpty).join(' · '),
                                              style: const TextStyle(fontSize: 11, color: AppColors.muted),
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                  );
                                },
                              ),
                      ),
          ),
        ],
      ),
    );
  }
}
