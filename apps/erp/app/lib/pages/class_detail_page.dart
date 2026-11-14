import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'student_detail_page.dart';

class ClassDetailPage extends StatefulWidget {
  const ClassDetailPage({super.key, required this.classId, required this.className});

  final int classId;
  final String className;

  @override
  State<ClassDetailPage> createState() => _ClassDetailPageState();
}

class _ClassDetailPageState extends State<ClassDetailPage> {
  ClassInfo? _info;
  List<ClassStudent> _students = const [];
  List<ClassSchedule> _schedules = const [];
  List<ClassPhoto> _photos = const [];
  List<Map<String, dynamic>> _comments = const [];
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
        Api.get<Map<String, dynamic>>('/classes/${widget.classId}'),
        Api.get<List<dynamic>>('/classes/${widget.classId}/students').catchError((_) => <dynamic>[]),
        Api.get<List<dynamic>>('/classes/${widget.classId}/schedules').catchError((_) => <dynamic>[]),
        Api.get<List<dynamic>>('/classes/${widget.classId}/photos').catchError((_) => <dynamic>[]),
        Api.get<List<dynamic>>('/classes/${widget.classId}/comments').catchError((_) => <dynamic>[]),
      ]);
      if (!mounted) return;
      setState(() {
        _info = ClassInfo.from(Map<String, dynamic>.from(results[0] as Map));
        _students = mapList(results[1]).map(ClassStudent.from).toList();
        _schedules = mapList(results[2]).map(ClassSchedule.from).toList();
        _photos = mapList(results[3]).map(ClassPhoto.from).toList();
        _comments = mapList(results[4]);
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
        _error = '班级详情加载失败';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_info?.name ?? widget.className)),
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
                      if (_info != null) _HeaderCard(info: _info!),
                      const SizedBox(height: 16),
                      PillTabs(
                        labels: ['学员 ${_students.length}', '课表 ${_schedules.length}', '相册 ${_photos.length}', '点评 ${_comments.length}'],
                        index: _tab,
                        padding: EdgeInsets.zero,
                        onChanged: (value) => setState(() => _tab = value),
                      ),
                      const SizedBox(height: 14),
                      if (_tab == 0) _StudentList(students: _students),
                      if (_tab == 1) _ScheduleList(schedules: _schedules),
                      if (_tab == 2) _PhotoGrid(photos: _photos),
                      if (_tab == 3) _CommentList(comments: _comments),
                    ],
                  ),
                ),
    );
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.info});

  final ClassInfo info;

  @override
  Widget build(BuildContext context) {
    return SurfaceCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  info.name,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.text),
                ),
              ),
              StatusTag(info.status, compact: true),
              if (info.enrolling) ...[
                const SizedBox(width: 6),
                const StatusTag('开放招生', compact: true),
              ],
            ],
          ),
          const SizedBox(height: 6),
          Text(
            [info.courseName ?? '未绑定课程', info.campusName].join(' · '),
            style: const TextStyle(fontSize: 12.5, color: AppColors.muted),
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(vertical: 12),
            decoration: BoxDecoration(color: const Color(0xFFF8F9FB), borderRadius: BorderRadius.circular(12)),
            child: Row(
              children: [
                Expanded(child: MetricTile(label: '班级学员', value: '${info.studentCount}', unit: '人', align: CrossAxisAlignment.center)),
                Container(width: 1, height: 28, color: AppColors.border),
                Expanded(child: MetricTile(label: '已排课次', value: '${info.lessonCount}', unit: '节', align: CrossAxisAlignment.center)),
                Container(width: 1, height: 28, color: AppColors.border),
                Expanded(
                  child: MetricTile(
                    label: '出勤率',
                    value: info.attendanceRate == null ? '-' : '${info.attendanceRate!.toStringAsFixed(0)}%',
                    align: CrossAxisAlignment.center,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          InfoRow('授课教师', [info.teacherName, info.assistName].whereType<String>().where((e) => e.isNotEmpty).join(' / ').isEmpty ? '-' : [info.teacherName, info.assistName].whereType<String>().where((e) => e.isNotEmpty).join(' / ')),
          InfoRow('上课时间', info.scheduleText ?? '-'),
          InfoRow('教室', info.room ?? '-'),
          InfoRow('班级人数', '${info.enrolledCount}/${info.capacity == 0 ? '不限' : info.capacity}'),
          InfoRow('开课日期', '${dateOnly(info.startDate)} 至 ${dateOnly(info.endDate)}'),
        ],
      ),
    );
  }
}

class _StudentList extends StatelessWidget {
  const _StudentList({required this.students});

  final List<ClassStudent> students;

  @override
  Widget build(BuildContext context) {
    if (students.isEmpty) {
      return const SurfaceCard(child: InlineHint('班级暂无学员，可在家校服务或班级管理中分班。'));
    }
    return Column(
      children: [
        for (final student in students)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: SurfaceCard(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => StudentDetailPage(studentId: student.studentId, studentName: student.studentName),
                ),
              ),
              child: Row(
                children: [
                  AvatarBadge(student.studentName, size: 38),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: Text(
                                student.studentName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                              ),
                            ),
                            const SizedBox(width: 6),
                            StatusTag(student.status, compact: true),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '剩余 ${countText(student.remainLessons)} 课时 · 已用 ${countText(student.usedLessons)}',
                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                  const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _ScheduleList extends StatelessWidget {
  const _ScheduleList({required this.schedules});

  final List<ClassSchedule> schedules;

  @override
  Widget build(BuildContext context) {
    if (schedules.isEmpty) {
      return const SurfaceCard(child: InlineHint('班级暂无排课记录。'));
    }
    return SurfaceCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Column(
        children: [
          for (var i = 0; i < schedules.length; i++) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 11),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 46,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          schedules[i].lessonDate.length >= 10 ? schedules[i].lessonDate.substring(5, 10) : schedules[i].lessonDate,
                          style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: AppColors.text),
                        ),
                        const SizedBox(height: 2),
                        Text(schedules[i].startTime, style: const TextStyle(fontSize: 11, color: AppColors.muted)),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                schedules[i].topic ?? '日常上课',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, color: AppColors.text),
                              ),
                            ),
                            StatusTag(schedules[i].status, compact: true),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '应到 ${schedules[i].shouldCount} · 实到 ${schedules[i].actualCount}'
                          '${schedules[i].attendanceRate == null ? '' : ' · 出勤 ${schedules[i].attendanceRate!.toStringAsFixed(0)}%'}'
                          '${schedules[i].commentCount > 0 ? ' · 点评 ${schedules[i].commentCount}' : ''}',
                          style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (i != schedules.length - 1) const Divider(height: 1, color: AppColors.border),
          ],
        ],
      ),
    );
  }
}

class _PhotoGrid extends StatelessWidget {
  const _PhotoGrid({required this.photos});

  final List<ClassPhoto> photos;

  @override
  Widget build(BuildContext context) {
    if (photos.isEmpty) {
      return const SurfaceCard(child: InlineHint('班级暂无相册照片，可在管理端上传后同步查看。'));
    }
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: photos.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
        childAspectRatio: 1,
      ),
      itemBuilder: (context, index) {
        final url = AppConfig.fileUrl(photos[index].url);
        return GestureDetector(
          onTap: () => showDialog(
            context: context,
            builder: (context) => Dialog(
              backgroundColor: Colors.transparent,
              insetPadding: const EdgeInsets.all(12),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: InteractiveViewer(
                  child: Image.network(url, fit: BoxFit.contain),
                ),
              ),
            ),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.network(
              url,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => Container(
                color: const Color(0xFFF1F3F6),
                alignment: Alignment.center,
                child: const Icon(Icons.broken_image_outlined, size: 20, color: AppColors.muted),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _CommentList extends StatelessWidget {
  const _CommentList({required this.comments});

  final List<Map<String, dynamic>> comments;

  @override
  Widget build(BuildContext context) {
    if (comments.isEmpty) {
      return const SurfaceCard(child: InlineHint('暂无课堂点评记录。'));
    }
    return Column(
      children: [
        for (final item in comments)
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
                          str(item['studentName'], '-'),
                          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.text),
                        ),
                      ),
                      if ((str(item['rating'])).isNotEmpty) StatusTag(str(item['rating']), compact: true),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    [dateOnly(strOrNull(item['lessonDate'])), str(item['teacherName'], '')].where((e) => e.isNotEmpty).join(' · '),
                    style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    str(item['content'], '暂无点评内容'),
                    style: const TextStyle(fontSize: 13, color: AppColors.sub, height: 1.6),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
