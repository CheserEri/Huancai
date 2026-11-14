import 'package:flutter/material.dart';

import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

class OrderDetailPage extends StatelessWidget {
  const OrderDetailPage({super.key, required this.order});

  final OrderInfo order;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('订单详情')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
        children: [
          SurfaceCard(
            padding: const EdgeInsets.all(18),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        order.typeName,
                        style: const TextStyle(fontSize: 14, color: AppColors.sub, fontWeight: FontWeight.w500),
                      ),
                    ),
                    StatusTag(order.statusName, compact: true),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      yuan(order.realPay),
                      style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w600, color: AppColors.text, height: 1),
                    ),
                    const SizedBox(width: 8),
                    const Padding(
                      padding: EdgeInsets.only(bottom: 3),
                      child: Text('实收金额', style: TextStyle(fontSize: 11.5, color: AppColors.muted)),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(color: const Color(0xFFF8F9FB), borderRadius: BorderRadius.circular(12)),
                  child: Row(
                    children: [
                      Expanded(child: MetricTile(label: '应收', value: money(order.shouldPay), align: CrossAxisAlignment.center)),
                      Container(width: 1, height: 28, color: AppColors.border),
                      Expanded(child: MetricTile(label: '欠费', value: money(order.arrearage), align: CrossAxisAlignment.center, color: order.arrearage > 0 ? AppColors.danger : AppColors.text)),
                      Container(width: 1, height: 28, color: AppColors.border),
                      Expanded(child: MetricTile(label: '结转', value: money(order.credit), align: CrossAxisAlignment.center)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SurfaceCard(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Column(
              children: [
                InfoRow('订单编号', order.orderNo),
                InfoRow('学员', order.stuName),
                if ((order.stuTel ?? '').isNotEmpty) InfoRow('联系电话', order.stuTel!),
                InfoRow('校区', order.campusName),
                InfoRow('成交日期', dateTimeText(order.dealDate)),
                InfoRow('创建时间', dateTimeText(order.createdTime)),
                InfoRow('经办人', order.creator ?? '-'),
                InfoRow('销售', order.salesman ?? '-'),
                InfoRow('支付方式', order.payMethod ?? '-'),
                InfoRow('订单来源', [order.sourceName, order.isLocal ? '本地办理' : ''].where((e) => e.isNotEmpty).join(' · ')),
                if ((order.expiryDate ?? '').isNotEmpty) InfoRow('有效期至', dateOnly(order.expiryDate)),
                if ((order.comment ?? '').isNotEmpty) InfoRow('备注', order.comment!),
              ],
            ),
          ),
          if ((order.content ?? '').isNotEmpty) ...[
            const SizedBox(height: 12),
            SurfaceCard(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('订单内容', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  Text(order.content!, style: const TextStyle(fontSize: 13, color: AppColors.text, height: 1.7)),
                ],
              ),
            ),
          ],
          if ((order.voidReason ?? '').isNotEmpty) ...[
            const SizedBox(height: 12),
            SurfaceCard(
              background: AppColors.dangerSoft,
              border: false,
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('作废信息', style: TextStyle(fontSize: 13, color: AppColors.danger, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  Text(
                    '${order.voidReason}（${dateTimeText(order.voidAt)}）',
                    style: const TextStyle(fontSize: 12.5, color: AppColors.danger, height: 1.6),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
