import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';

class SuspendCreatePage extends StatefulWidget {
  const SuspendCreatePage({super.key, required this.classOptions});

  final List<NamedOption> classOptions;

  @override
  State<SuspendCreatePage> createState() => _SuspendCreatePageState();
}

class _SuspendCandidate {
  _SuspendCandidate.from(Map<String, dynamic> json)
      : id = intOf(json['id']),
        classId = intOf(json['classId']),
        className = strOrNull(json['className']),
        courseName = strOrNull(json['courseName']),
        teacherName = strOrNull(json['teacherName']),
        lessonDate = str(json['lessonDate']),
        startTime = str(json['startTime']),
        endTime = str(json['endTime']),
        room = strOrNull(json['room']),
        status = str(json['status'], '待上课'),
        suspendId = json['suspendId'] == null ? null : intOf(json['suspendId']);

  final int id;
  final int classId;
  final String? className;
  final String? courseName;
  final String? teacherName;
  final String lessonDate;
  final String startTime;
  final String endTime;
  final String? room;
  final String status;
  final int? suspendId;
}

class _SuspendCreatePageState extends State<SuspendCreatePage> {
  final Set<int> _classIds = {};
  DateTime _start = DateTime.now();
  DateTime _end = DateTime.now().add(const Duration(days: 6));
  final _reason = TextEditingController();
  bool _notify = true;
  bool _loading = false;
  bool _submitting = false;
  String? _error;
  List<_SuspendCandidate> _candidates = const [];
  final Set<int> _selected = {};
  bool _queried = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _pickRange() async {
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime.now().subtract(const Duration(days: 30)),
      lastDate: DateTime.now().add(const Duration(days: 180)),
      initialDateRange: DateTimeRange(start: _start, end: _end),
    );
    if (range != null) {
      setState(() {
        _start = range.start;
        _end = range.end;
      });
    }
  }

  Future<void> _pickClasses() async {
    final selected = await showModalBottomSheet<Set<int>>(
      context: context,
      builder: (context) => _ClassMultiPicker(options: widget.classOptions, selected: _classIds),
    );
    if (selected != null) setState(() => _classIds..clear()..addAll(selected));
  }

  Future<void> _search() async {
    if (_classIds.isEmpty) {
      showToast(context, '请先选择班级');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _selected.clear();
    });
    try {
      final data = await Api.get<List<dynamic>>('/class-suspends/candidates', {
        'start': dateText(_start),
        'end': dateText(_end),
        'classIds': _classIds.join(','),
      });
      if (!mounted) return;
      setState(() {
        _candidates = mapList(data).map(_SuspendCandidate.from).toList();
        _queried = true;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = e.message;
      });
    }
  }

  Future<void> _submit() async {
    if (_selected.isEmpty) {
      showToast(context, '请勾选需要休课的课次');
      return;
    }
    if (_reason.text.trim().isEmpty) {
      showToast(context, '请填写休课原因');
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await Api.post<Map<String, dynamic>>('/class-suspends/batch', {
        'scheduleIds': _selected.toList(),
        'reason': _reason.text.trim(),
        'notify': _notify,
      });
      if (!mounted) return;
      final suspended = intOf(result['suspended']);
      final skipped = intOf(result['skipped']);
      final receivers = intOf(result['receiverCount']);
      showToast(
        context,
        '已休课 $suspended 节${skipped > 0 ? '，跳过 $skipped 节' : ''}${_notify && receivers > 0 ? '，已通知 $receivers 位家长' : ''}',
      );
      Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) {
        showToast(context, e.message);
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final classNameText = _classIds.isEmpty
        ? '选择班级'
        : widget.classOptions.where((e) => _classIds.contains(e.id)).map((e) => e.name).join('、');

    return Scaffold(
      appBar: AppBar(title: const Text('发起休课')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
        children: [
          SurfaceCard(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('休课范围', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w600)),
                const SizedBox(height: 12),
                GestureDetector(
                  onTap: _pickClasses,
                  child: _field(
                    icon: Icons.groups_outlined,
                    text: classNameText,
                    placeholder: _classIds.isEmpty,
                    trailing: _classIds.isEmpty ? null : '已选 ${_classIds.length} 个',
                  ),
                ),
                const SizedBox(height: 10),
                GestureDetector(
                  onTap: _pickRange,
                  child: _field(
                    icon: Icons.date_range_outlined,
                    text: '${dateText(_start)} 至 ${dateText(_end)}',
                    placeholder: false,
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 42,
                  child: OutlinedButton.icon(
                    onPressed: _loading ? null : _search,
                    icon: const Icon(Icons.search_rounded, size: 18),
                    label: Text(_loading ? '查询中…' : '查询可休课次'),
                  ),
                ),
              ],
            ),
          ),
          const SectionTitle('课次列表', padding: EdgeInsets.fromLTRB(0, 20, 0, 8)),
          if (_error != null)
            SurfaceCard(child: InlineHint(_error!))
          else if (!_queried)
            const SurfaceCard(child: InlineHint('选择班级与日期范围后，点击「查询可休课次」加载待休课次。'))
          else if (_candidates.isEmpty)
            const SurfaceCard(child: InlineHint('所选范围内没有可休课的课次。'))
          else
            SurfaceCard(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              child: Column(
                children: [
                  for (final item in _candidates)
                    CheckboxListTile(
                      value: item.suspendId != null || _selected.contains(item.id),
                      onChanged: item.suspendId != null
                          ? null
                          : (value) => setState(() {
                                if (value == true) {
                                  _selected.add(item.id);
                                } else {
                                  _selected.remove(item.id);
                                }
                              }),
                      activeColor: AppColors.primary,
                      dense: true,
                      controlAffinity: ListTileControlAffinity.trailing,
                      title: Text(
                        item.className ?? '未命名班级',
                        style: TextStyle(
                          fontSize: 13.5,
                          color: item.suspendId != null ? AppColors.muted : AppColors.text,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: Text(
                        '${dateOnly(item.lessonDate)} ${item.startTime}-${item.endTime} · ${item.courseName ?? ''} · ${item.status}${item.suspendId != null ? ' · 已休课' : ''}',
                        style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                      ),
                    ),
                ],
              ),
            ),
          const SizedBox(height: 16),
          SurfaceCard(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('休课原因', style: TextStyle(fontSize: 13, color: AppColors.sub, fontWeight: FontWeight.w600)),
                const SizedBox(height: 10),
                TextField(
                  controller: _reason,
                  maxLines: 3,
                  decoration: const InputDecoration(hintText: '请填写休课原因，将记录在休课单中'),
                  style: const TextStyle(fontSize: 13.5, height: 1.6),
                ),
                const SizedBox(height: 6),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  value: _notify,
                  activeThumbColor: AppColors.primary,
                  title: const Text('同步通知家长', style: TextStyle(fontSize: 13.5)),
                  subtitle: const Text('通过家校消息推送休课通知', style: TextStyle(fontSize: 11.5, color: AppColors.muted)),
                  onChanged: (value) => setState(() => _notify = value),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : Text('确认休课${_selected.isEmpty ? '' : '（${_selected.length} 节）'}'),
          ),
        ],
      ),
    );
  }

  Widget _field({required IconData icon, required String text, required bool placeholder, String? trailing}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Icon(icon, size: 17, color: AppColors.muted),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 13.5, color: placeholder ? AppColors.muted : AppColors.text),
            ),
          ),
          if (trailing != null)
            Text(trailing, style: const TextStyle(fontSize: 11.5, color: AppColors.primary)),
          const Icon(Icons.expand_more_rounded, size: 18, color: AppColors.muted),
        ],
      ),
    );
  }
}

class _ClassMultiPicker extends StatefulWidget {
  const _ClassMultiPicker({required this.options, required this.selected});

  final List<NamedOption> options;
  final Set<int> selected;

  @override
  State<_ClassMultiPicker> createState() => _ClassMultiPickerState();
}

class _ClassMultiPickerState extends State<_ClassMultiPicker> {
  late final Set<int> _selected = {...widget.selected};
  String _keyword = '';

  @override
  Widget build(BuildContext context) {
    final options = widget.options.where((e) => e.name.contains(_keyword)).toList();
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
            child: Row(
              children: [
                const Expanded(
                  child: Text('选择班级', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
                ),
                TextButton(onPressed: () => Navigator.pop(context, _selected), child: const Text('确定')),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: TextField(
              decoration: const InputDecoration(hintText: '搜索班级名称', prefixIcon: Icon(Icons.search_rounded, size: 19)),
              onChanged: (value) => setState(() => _keyword = value.trim()),
              style: const TextStyle(fontSize: 13.5),
            ),
          ),
          const SizedBox(height: 8),
          Flexible(
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: options.length,
              itemBuilder: (context, index) {
                final option = options[index];
                return CheckboxListTile(
                  value: _selected.contains(option.id),
                  onChanged: (value) => setState(() {
                    if (value == true) {
                      _selected.add(option.id);
                    } else {
                      _selected.remove(option.id);
                    }
                  }),
                  dense: true,
                  title: Text(option.name, style: const TextStyle(fontSize: 13.5)),
                  activeColor: AppColors.primary,
                  controlAffinity: ListTileControlAffinity.trailing,
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
