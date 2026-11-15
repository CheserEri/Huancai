import 'package:flutter/material.dart';

import '../core/models.dart';
import '../ui/widgets.dart';
import 'grade_analysis_page.dart';
import 'grade_entry_page.dart';
import 'grade_exams_page.dart';
import 'grade_query_page.dart';

/// 成绩中心：考试管理、成绩录入、成绩查询、成绩分析。
class GradesPage extends StatefulWidget {
  const GradesPage({super.key});

  @override
  State<GradesPage> createState() => _GradesPageState();
}

class _GradesPageState extends State<GradesPage> {
  static const _tabs = ['考试管理', '成绩录入', '成绩查询', '成绩分析'];

  int _tab = 0;
  final Set<int> _built = {0};
  int _entryExamId = 0;
  int _entryToken = 0;
  int _analysisExamId = 0;
  int _analysisToken = 0;

  void _switchTab(int index) {
    setState(() {
      _tab = index;
      _built.add(index);
    });
  }

  void _openEntry(GradeExam exam) {
    setState(() {
      _entryExamId = exam.id;
      _entryToken++;
      _tab = 1;
      _built.add(1);
    });
  }

  void _openAnalysis(GradeExam exam) {
    setState(() {
      _analysisExamId = exam.id;
      _analysisToken++;
      _tab = 3;
      _built.add(3);
    });
  }

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[
      GradeExamsTab(onEntry: _openEntry, onAnalyze: _openAnalysis),
      if (_built.contains(1))
        GradeEntryTab(key: ValueKey('grade-entry-$_entryToken'), requestedExamId: _entryExamId == 0 ? null : _entryExamId)
      else
        const SizedBox.shrink(),
      if (_built.contains(2)) const GradeQueryTab() else const SizedBox.shrink(),
      if (_built.contains(3))
        GradeAnalysisTab(key: ValueKey('grade-analysis-$_analysisToken'), requestedExamId: _analysisExamId == 0 ? null : _analysisExamId)
      else
        const SizedBox.shrink(),
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('成绩中心')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: PillTabs(labels: _tabs, index: _tab, onChanged: _switchTab),
          ),
          const SizedBox(height: 10),
          Expanded(child: IndexedStack(index: _tab, children: children)),
        ],
      ),
    );
  }
}
