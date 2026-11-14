import 'dart:async';

import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/arrears_form.dart';
import 'payment/data_fee_form.dart';
import 'payment/enroll_form.dart';
import 'payment/material_form.dart';
import 'payment/recharge_form.dart';

/// 财务缴费：课时费、教材费、资料费、余额充值、欠费补缴。
class PaymentPage extends StatefulWidget {
  const PaymentPage({super.key, this.studentId, this.studentName});

  final int? studentId;
  final String? studentName;

  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> {
  static const _types = ['课时费', '教材费', '资料费', '充值', '补缴'];

  int? _studentId;
  String _studentName = '';
  String _campusName = '';
  double _balance = 0;
  double _debt = 0;
  int _type = 0;
  bool _loadingStudent = false;

  @override
  void initState() {
    super.initState();
    _studentId = widget.studentId;
    _studentName = widget.studentName ?? '';
    if (_studentId != null) _loadStudent(_studentId!);
  }

  Future<void> _loadStudent(int id) async {
    setState(() => _loadingStudent = true);
    try {
      final data = await Api.get<Map<String, dynamic>>('/students/$id');
      final info = Map<String, dynamic>.from((data['student'] as Map?) ?? const {});
      if (!mounted) return;
      setState(() {
        _studentId = id;
        _studentName = str(info['name'], _studentName);
        _campusName = str(info['campusText'] ?? info['campusName']);
        _balance = doubleOf(info['balance']);
        _debt = doubleOf(info['debt']);
        _loadingStudent = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _loadingStudent = false);
      showToast(context, e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingStudent = false);
      showToast(context, '学员信息加载失败');
    }
  }

  Future<void> _pickStudent() async {
    final picked = await showModalBottomSheet<Student>(
      context: context,
      isScrollControlled: true,
      builder: (context) => const _StudentPickerSheet(),
    );
    if (picked == null || !mounted) return;
    setState(() {
      _studentId = picked.id;
      _studentName = picked.name;
      _campusName = picked.campusName;
      _balance = picked.balance;
      _debt = picked.debt;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('缴费')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
            child: _studentCard(),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: PillTabs(
              labels: _types,
              index: _type,
              padding: EdgeInsets.zero,
              onChanged: (value) => setState(() => _type = value),
            ),
          ),
          Expanded(child: _formBody()),
        ],
      ),
    );
  }

  Widget _studentCard() {
    if (_studentId == null) {
      return SurfaceCard(
        onTap: _pickStudent,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
        child: Row(
          children: [
            const Icon(Icons.person_add_alt_1_rounded, size: 22, color: AppColors.primary),
            const SizedBox(width: 10),
            const Expanded(
              child: Text('选择缴费学员', style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text)),
            ),
            const Text('选择', style: TextStyle(fontSize: 13, color: AppColors.primary)),
            const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.primary),
          ],
        ),
      );
    }
    return SurfaceCard(
      onTap: _pickStudent,
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      child: Column(
        children: [
          Row(
            children: [
              AvatarBadge(_studentName, size: 42),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _studentName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _campusName.isEmpty ? '更换学员' : '$_campusName · 点击更换',
                      style: const TextStyle(fontSize: 12, color: AppColors.muted),
                    ),
                  ],
                ),
              ),
              if (_loadingStudent)
                const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: MetricTile(label: '账户余额', value: money(_balance), unit: '元', align: CrossAxisAlignment.start, color: AppColors.success),
              ),
              Expanded(
                child: MetricTile(label: '欠费', value: money(_debt), unit: '元', align: CrossAxisAlignment.end, color: _debt > 0 ? AppColors.danger : AppColors.text),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _formBody() {
    final studentId = _studentId;
    if (studentId == null) {
      return const EmptyState(title: '请先选择缴费学员', subtitle: '选择学员后可办理课时费、教材费、资料费、充值与欠费补缴', icon: Icons.account_balance_wallet_outlined);
    }
    final key = ValueKey('$studentId-$_type');
    switch (_type) {
      case 0:
        return EnrollForm(key: key, studentId: studentId, onDone: () => _loadStudent(studentId));
      case 1:
        return MaterialForm(key: key, studentId: studentId, balance: _balance, onDone: () => _loadStudent(studentId));
      case 2:
        return DataFeeForm(key: key, studentId: studentId, onDone: () => _loadStudent(studentId));
      case 3:
        return RechargeForm(key: key, studentId: studentId, onDone: () => _loadStudent(studentId));
      default:
        return ArrearsForm(key: key, studentId: studentId, onDone: () => _loadStudent(studentId));
    }
  }
}

/// 学员搜索选择弹层（按姓名 / 手机号搜索，分页加载）。
class _StudentPickerSheet extends StatefulWidget {
  const _StudentPickerSheet();

  @override
  State<_StudentPickerSheet> createState() => _StudentPickerSheetState();
}

class _StudentPickerSheetState extends State<_StudentPickerSheet> {
  final _students = <Student>[];
  final _searchController = TextEditingController();
  Timer? _debounce;
  int _page = 1;
  int _total = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String _keyword = '';

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load({bool reset = false}) async {
    if (reset) setState(() => _loading = true);
    try {
      final data = await Api.get<Map<String, dynamic>>('/students', {
        'page': reset ? 1 : _page,
        'size': 20,
        if (_keyword.isNotEmpty) 'keyword': _keyword,
      });
      final page = PageData.from<Student>(data, Student.from);
      if (!mounted) return;
      setState(() {
        if (reset) {
          _students
            ..clear()
            ..addAll(page.records);
          _page = 1;
        } else {
          _students.addAll(page.records);
        }
        _total = page.total;
        _page += 1;
        _loading = false;
        _loadingMore = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
      });
      showToast(context, e.message);
    }
  }

  void _onKeyword(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () {
      _keyword = value.trim();
      _load(reset: true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final hasMore = _students.length < _total;
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.72,
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 18, 20, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text('选择学员', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                controller: _searchController,
                decoration: const InputDecoration(hintText: '搜索学员姓名 / 手机号', prefixIcon: Icon(Icons.search_rounded, size: 19)),
                onChanged: _onKeyword,
                style: const TextStyle(fontSize: 13.5),
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _students.isEmpty
                      ? const EmptyState(title: '未找到学员', subtitle: '换个关键词试试')
                      : NotificationListener<ScrollNotification>(
                          onNotification: (notification) {
                            if (notification.metrics.extentAfter < 200 && hasMore && !_loadingMore) {
                              setState(() => _loadingMore = true);
                              _load();
                            }
                            return false;
                          },
                          child: ListView.separated(
                            padding: const EdgeInsets.fromLTRB(20, 4, 20, 20),
                            itemCount: _students.length,
                            separatorBuilder: (_, _) => const SizedBox(height: 8),
                            itemBuilder: (context, index) {
                              final student = _students[index];
                              return SurfaceCard(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                                onTap: () => Navigator.pop(context, student),
                                child: Row(
                                  children: [
                                    AvatarBadge(student.name, size: 36),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            student.name,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                                          ),
                                          const SizedBox(height: 3),
                                          Text(
                                            '${student.campusName} · 余额 ${money(student.balance)}'
                                            '${student.debt > 0 ? ' · 欠费 ${money(student.debt)}' : ''}',
                                            style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                                          ),
                                        ],
                                      ),
                                    ),
                                    const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
                                  ],
                                ),
                              );
                            },
                          ),
                        ),
            ),
          ],
        ),
      ),
    );
  }
}
