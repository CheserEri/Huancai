import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment_page.dart';

class StudentDetailPage extends StatefulWidget {
  const StudentDetailPage({super.key, required this.studentId, required this.studentName});

  final int studentId;
  final String studentName;

  @override
  State<StudentDetailPage> createState() => _StudentDetailPageState();
}

class _StudentDetailPageState extends State<StudentDetailPage> {
  StudentDetail? _detail;
  List<Map<String, dynamic>> _scores = const [];
  bool _loading = true;
  String? _error;
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        Api.get<Map<String, dynamic>>('/students/${widget.studentId}'),
        Api.get<List<dynamic>>('/grades/student/${widget.studentId}').catchError((_) => <dynamic>[]),
      ]);
      if (!mounted) return;
      setState(() {
        _detail = StudentDetail.from(Map<String, dynamic>.from(results[0] as Map));
        _scores = mapList(results[1]);
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
        _error = '学员详情加载失败';
      });
    }
  }

  Future<void> _openPayment() async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => PaymentPage(studentId: widget.studentId, studentName: widget.studentName),
      ),
    );
    if (mounted) await _load();
  }

  @override
  Widget build(BuildContext context) {
    final info = _detail?.info ?? const <String, dynamic>{};
    final name = str(info['name'], widget.studentName);

    return Scaffold(
      appBar: AppBar(
        title: Text(name),
        actions: [
          TextButton.icon(
            onPressed: _openPayment,
            icon: const Icon(Icons.account_balance_wallet_outlined, size: 18),
            label: const Text('缴费'),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: _loading
          ? const LoadingView(height: 400)
          : _error != null
              ? ErrorView(_error!, onRetry: _load)
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
                    children: [
                      _HeaderCard(info: info),
                      const SizedBox(height: 16),
                      PillTabs(
                        labels: const ['报读课程', '课时流水', '成绩', '基本信息'],
                        index: _tab,
                        padding: EdgeInsets.zero,
                        onChanged: (value) => setState(() => _tab = value),
                      ),
                      const SizedBox(height: 14),
                      if (_tab == 0) _Enrollments(enrollments: _detail!.enrollments),
                      if (_tab == 1) _Txns(txns: _detail!.txns),
                      if (_tab == 2) _Scores(scores: _scores),
                      if (_tab == 3) _Profile(info: info, guardians: _detail!.guardians),
                    ],
                  ),
                ),
    );
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.info});

  final Map<String, dynamic> info;

  @override
  Widget build(BuildContext context) {
    final name = str(info['name'], '未命名');
    final status = str(info['status'], '在读');
    final gender = str(info['gender']);
    final ageText = strOrNull(info['ageText']);
    final campus = str(info['campusText'] ?? info['campusName'], '-');
    final balance = doubleOf(info['balance']);
    final debt = doubleOf(info['debt']);
    final points = intOf(info['points']);

    return SurfaceCard(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
      child: Column(
        children: [
          Row(
            children: [
              AvatarBadge(name, size: 52),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w600, color: AppColors.text),
                          ),
                        ),
                        if (intOf(info['isStar']) == 1) ...[
                          const SizedBox(width: 4),
                          const Icon(Icons.star_rounded, size: 16, color: AppColors.warning),
                        ],
                        const SizedBox(width: 8),
                        StatusTag(status, compact: true),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      [
                        if (gender.isNotEmpty) gender,
                        if ((ageText ?? '').isNotEmpty) ageText!,
                        campus,
                        '在读 ${intOf(info['courseCount'])} 门',
                      ].join(' · '),
                      style: const TextStyle(fontSize: 12, color: AppColors.muted),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      str(info['phone'], '-'),
                      style: const TextStyle(fontSize: 12, color: AppColors.muted),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 12),
            decoration: BoxDecoration(color: const Color(0xFFF8F9FB), borderRadius: BorderRadius.circular(12)),
            child: Row(
              children: [
                Expanded(child: MetricTile(label: '账户余额', value: money(balance), unit: '元', align: CrossAxisAlignment.center, color: AppColors.success)),
                Container(width: 1, height: 28, color: AppColors.border),
                Expanded(child: MetricTile(label: '欠费', value: money(debt), unit: '元', align: CrossAxisAlignment.center, color: debt > 0 ? AppColors.danger : AppColors.text)),
                Container(width: 1, height: 28, color: AppColors.border),
                Expanded(child: MetricTile(label: '积分', value: '$points', align: CrossAxisAlignment.center, color: AppColors.purple)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Enrollments extends StatelessWidget {
  const _Enrollments({required this.enrollments});

  final List<Enrollment> enrollments;

  @override
  Widget build(BuildContext context) {
    if (enrollments.isEmpty) {
      return const SurfaceCard(child: InlineHint('该学员暂无报读课程记录。'));
    }
    return Column(
      children: [
        for (final item in enrollments)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: SurfaceCard(
              padding: const EdgeInsets.all(15),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          item.courseName,
                          style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                        ),
                      ),
                      StatusTag(item.status, compact: true),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    [
                      if ((item.className ?? '').isNotEmpty) item.className!,
                      if ((item.teacher ?? '').isNotEmpty) item.teacher!,
                      if ((item.expireDate ?? '').isNotEmpty) '到期 ${dateOnly(item.expireDate)}',
                    ].join(' · '),
                    style: const TextStyle(fontSize: 12, color: AppColors.muted),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(child: MetricTile(label: '剩余课时', value: countText(item.remainLessons), align: CrossAxisAlignment.start, color: item.remainLessons < 0 ? AppColors.danger : AppColors.primary)),
                      Expanded(child: MetricTile(label: '已用 / 总课时', value: '${countText(item.usedLessons)} / ${countText(item.totalLessons)}', align: CrossAxisAlignment.center)),
                      Expanded(child: MetricTile(label: '实收金额', value: money(item.paidAmount), align: CrossAxisAlignment.end)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  MiniBar(
                    value: item.totalLessons <= 0 ? 0 : item.usedLessons / item.totalLessons,
                    height: 5,
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _Txns extends StatelessWidget {
  const _Txns({required this.txns});

  final List<LessonTxn> txns;

  @override
  Widget build(BuildContext context) {
    if (txns.isEmpty) {
      return const SurfaceCard(child: InlineHint('暂无课时流水记录。'));
    }
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Column(
        children: [
          for (var i = 0; i < txns.length; i++) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 11),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          txns[i].bizType ?? txns[i].type,
                          style: const TextStyle(fontSize: 13.5, color: AppColors.text, fontWeight: FontWeight.w500),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          [
                            dateTimeText(txns[i].createdAt),
                            if ((txns[i].note ?? '').isNotEmpty) txns[i].note!,
                          ].join(' · '),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted, height: 1.5),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '${txns[i].lessons > 0 ? '+' : ''}${countText(txns[i].lessons)}',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: txns[i].lessons >= 0 ? AppColors.success : AppColors.text,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '余 ${countText(txns[i].balanceAfter ?? 0)}',
                        style: const TextStyle(fontSize: 11, color: AppColors.muted),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            if (i != txns.length - 1) const Divider(height: 1, color: AppColors.border),
          ],
        ],
      ),
    );
  }
}

class _Scores extends StatelessWidget {
  const _Scores({required this.scores});

  final List<Map<String, dynamic>> scores;

  @override
  Widget build(BuildContext context) {
    if (scores.isEmpty) {
      return const SurfaceCard(child: InlineHint('暂无已发布的考试成绩。'));
    }
    return Column(
      children: [
        for (final score in scores)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: SurfaceCard(
              padding: const EdgeInsets.all(15),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          str(score['examName'], '-'),
                          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          [
                            dateOnly(str(score['examDate'])),
                            str(score['className'], ''),
                            str(score['status'], ''),
                          ].where((e) => e.isNotEmpty).join(' · '),
                          style: const TextStyle(fontSize: 12, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        score['totalScore'] == null ? '-' : countText(numOrNull(score['totalScore']) ?? 0),
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: AppColors.primary, height: 1.1),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        score['rankClass'] == null ? '满分 ${countText(numOrNull(score['fullScore']) ?? 0)}' : '班级第 ${score['rankClass']} 名',
                        style: const TextStyle(fontSize: 11, color: AppColors.muted),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _Profile extends StatelessWidget {
  const _Profile({required this.info, required this.guardians});

  final Map<String, dynamic> info;
  final List<Guardian> guardians;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SurfaceCard(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Column(
            children: [
              InfoRow('手机号', str(info['phone'], '-')),
              InfoRow('校区', str(info['campusText'] ?? info['campusName'], '-')),
              InfoRow('年级', str(info['grade'], '-')),
              InfoRow('学校', str(info['school'], '-')),
              InfoRow('年级班级', str(info['publicSchoolClass'], '-')),
              InfoRow('出生日期', dateOnly(strOrNull(info['birthday']))),
              InfoRow('报名日期', dateOnly(strOrNull(info['enrollDate']))),
              InfoRow('来源', str(info['source'], '-')),
              InfoRow('渠道', str(info['channelName'], '-')),
              InfoRow('地址', str(info['address'], '-')),
              InfoRow('备注', str(info['remark'] ?? info['extraInfo'], '-')),
            ],
          ),
        ),
        if (guardians.isNotEmpty) ...[
          const SizedBox(height: 12),
          SurfaceCard(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('联系人', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                for (final guardian in guardians)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            '${guardian.name}${(guardian.relation ?? '').isEmpty ? '' : '（${guardian.relation}）'}',
                            style: const TextStyle(fontSize: 13, color: AppColors.text),
                          ),
                        ),
                        Text(guardian.phone, style: const TextStyle(fontSize: 12.5, color: AppColors.muted)),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ],
    );
  }
}
