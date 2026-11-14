import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

class GradeAnalysisPage extends StatefulWidget {
  const GradeAnalysisPage({super.key, required this.exam});

  final GradeExam exam;

  @override
  State<GradeAnalysisPage> createState() => _GradeAnalysisPageState();
}

class _GradeAnalysisPageState extends State<GradeAnalysisPage> {
  GradeAnalysis? _analysis;
  bool _loading = true;
  String? _error;

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
      final data = await Api.get<Map<String, dynamic>>('/grades/analysis', {'examId': widget.exam.id});
      if (!mounted) return;
      setState(() {
        _analysis = GradeAnalysis.from(data);
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
        _error = '成绩分析加载失败';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('成绩分析')),
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
                      _summaryCard(_analysis!),
                      const SizedBox(height: 12),
                      _metricCard(_analysis!),
                      if (_analysis!.distributions.isNotEmpty) ...[
                        const SectionTitle('分数分布', padding: EdgeInsets.fromLTRB(0, 20, 0, 10)),
                        _distributionCard(_analysis!),
                      ],
                      if (_analysis!.classes.isNotEmpty) ...[
                        const SectionTitle('班级对比', padding: EdgeInsets.fromLTRB(0, 20, 0, 10)),
                        _classCard(_analysis!),
                      ],
                      if (_analysis!.rankings.isNotEmpty) ...[
                        const SectionTitle('成绩排名', padding: EdgeInsets.fromLTRB(0, 20, 0, 10)),
                        _rankingCard(_analysis!),
                      ],
                      if (_analysis!.enteredCount == 0) ...[
                        const SizedBox(height: 16),
                        const SurfaceCard(
                          child: InlineHint('该考试尚未录入任何成绩，排名、平均分等指标暂不可用。'),
                        ),
                      ],
                    ],
                  ),
                ),
    );
  }

  Widget _summaryCard(GradeAnalysis analysis) {
    return SurfaceCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  analysis.examName,
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
              ),
              StatusTag(widget.exam.status, compact: true),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            [analysis.examType, dateOnly(analysis.examDate), '满分 ${countText(analysis.fullScore)}'].join(' · '),
            style: const TextStyle(fontSize: 12, color: AppColors.muted),
          ),
          const SizedBox(height: 12),
          Text(
            '应考 ${analysis.studentCount} 人 · 已录 ${analysis.enteredCount} 人 · 缺考 ${analysis.absentCount} 人',
            style: const TextStyle(fontSize: 12.5, color: AppColors.sub),
          ),
        ],
      ),
    );
  }

  Widget _metricCard(GradeAnalysis analysis) {
    String format(num? value) => value == null ? '-' : (value == value.roundToDouble() ? value.toStringAsFixed(0) : value.toStringAsFixed(1));
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(child: MetricTile(label: '平均分', value: format(analysis.avgScore), align: CrossAxisAlignment.center, color: AppColors.primary)),
              Expanded(child: MetricTile(label: '最高分', value: format(analysis.maxScore), align: CrossAxisAlignment.center)),
              Expanded(child: MetricTile(label: '最低分', value: format(analysis.minScore), align: CrossAxisAlignment.center)),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1, color: AppColors.border),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: MetricTile(
                  label: '及格率',
                  value: analysis.passRate == null ? '-' : '${analysis.passRate!.toStringAsFixed(0)}%',
                  align: CrossAxisAlignment.center,
                  color: AppColors.success,
                ),
              ),
              Expanded(
                child: MetricTile(
                  label: '优秀率',
                  value: analysis.excellentRate == null ? '-' : '${analysis.excellentRate!.toStringAsFixed(0)}%',
                  align: CrossAxisAlignment.center,
                  color: AppColors.warning,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _distributionCard(GradeAnalysis analysis) {
    final maxCount = analysis.distributions
        .map((e) => doubleOf(e['count']))
        .fold<double>(0, (previous, value) => value > previous ? value : previous);
    return SurfaceCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          for (final item in analysis.distributions)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  SizedBox(
                    width: 62,
                    child: Text(
                      str(item['label'], '-'),
                      style: const TextStyle(fontSize: 12, color: AppColors.sub),
                    ),
                  ),
                  Expanded(
                    child: MiniBar(
                      value: maxCount == 0 ? 0 : doubleOf(item['count']) / maxCount,
                      height: 10,
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(width: 10),
                  SizedBox(
                    width: 34,
                    child: Text(
                      '${intOf(item['count'])} 人',
                      textAlign: TextAlign.right,
                      style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _classCard(GradeAnalysis analysis) {
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Column(
        children: [
          for (var i = 0; i < analysis.classes.length; i++) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 10),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          str(analysis.classes[i]['className'], '-'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 13.5, color: AppColors.text, fontWeight: FontWeight.w500),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          '参考 ${intOf(analysis.classes[i]['count'])} 人 · 及格率 ${analysis.classes[i]['passRate'] == null ? '-' : '${doubleOf(analysis.classes[i]['passRate']).toStringAsFixed(0)}%'}',
                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        analysis.classes[i]['avgScore'] == null ? '-' : doubleOf(analysis.classes[i]['avgScore']).toStringAsFixed(1),
                        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.primary),
                      ),
                      const SizedBox(height: 2),
                      const Text('平均分', style: TextStyle(fontSize: 10.5, color: AppColors.muted)),
                    ],
                  ),
                ],
              ),
            ),
            if (i != analysis.classes.length - 1) const Divider(height: 1, color: AppColors.border),
          ],
        ],
      ),
    );
  }

  Widget _rankingCard(GradeAnalysis analysis) {
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Column(
        children: [
          for (var i = 0; i < analysis.rankings.length; i++) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 9),
              child: Row(
                children: [
                  Container(
                    width: 24,
                    height: 24,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: i < 3 ? AppColors.primarySoft : const Color(0xFFF3F4F6),
                      borderRadius: BorderRadius.circular(7),
                    ),
                    child: Text(
                      '${intOf(analysis.rankings[i]['rank'], i + 1)}',
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        color: i < 3 ? AppColors.primary : AppColors.muted,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          str(analysis.rankings[i]['studentName'], '-'),
                          style: const TextStyle(fontSize: 13.5, color: AppColors.text, fontWeight: FontWeight.w500),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          [
                            str(analysis.rankings[i]['className'], ''),
                            if ((analysis.rankings[i]['level'] ?? '') != '') str(analysis.rankings[i]['level']),
                          ].where((e) => e.isNotEmpty).join(' · '),
                          style: const TextStyle(fontSize: 11, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    countText(numOrNull(analysis.rankings[i]['totalScore']) ?? 0),
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                  ),
                ],
              ),
            ),
            if (i != analysis.rankings.length - 1) const Divider(height: 1, color: AppColors.border),
          ],
        ],
      ),
    );
  }
}
