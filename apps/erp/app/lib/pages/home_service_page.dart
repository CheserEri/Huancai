import 'package:flutter/material.dart';

import '../core/api.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../state/app_state.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'home_service_album_tab.dart';
import 'suspend_create_page.dart';

class HomeServicePage extends StatefulWidget {
  const HomeServicePage({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  State<HomeServicePage> createState() => _HomeServicePageState();
}

class _HomeServicePageState extends State<HomeServicePage> {
  late int _tab = widget.initialTab.clamp(0, 3);

  List<MessageInfo> _messages = const [];
  List<HomeworkInfo> _homeworks = const [];
  List<SuspendInfo> _suspends = const [];
  List<NamedOption> _classOptions = const [];
  bool _loading = true;
  String? _error;

  List<String> get _tabs => [
        '消息推送',
        '作业',
        '班级休课',
        if (appState.can(const ['/classAlbum'])) '班级相册',
      ];

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
        Api.get<Map<String, dynamic>>('/messages', {'page': 1, 'size': 50}).catchError((_) => <String, dynamic>{}),
        Api.get<Map<String, dynamic>>('/homeworks', {'page': 1, 'size': 50}).catchError((_) => <String, dynamic>{}),
        Api.get<Map<String, dynamic>>('/class-suspends', {'page': 1, 'size': 50}).catchError((_) => <String, dynamic>{}),
      ]);
      if (!mounted) return;
      setState(() {
        _messages = PageData.from<MessageInfo>(results[0], MessageInfo.from).records;
        _homeworks = PageData.from<HomeworkInfo>(results[1], HomeworkInfo.from).records;
        _suspends = PageData.from<SuspendInfo>(results[2], SuspendInfo.from).records;
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
        _error = '家校服务数据加载失败';
      });
    }
  }

  Future<void> _ensureClassOptions() async {
    if (_classOptions.isNotEmpty) return;
    final data = await Api.get<Map<String, dynamic>>('/schedules/options');
    final classes = mapList(data['classes']).map((e) => NamedOption(intOf(e['id']), str(e['name']))).toList();
    if (mounted) setState(() => _classOptions = classes);
  }

  Future<void> _openSendMessage() async {
    if (!appState.can(const ['/messagePush'])) {
      showToast(context, '当前账号无消息推送权限');
      return;
    }
    try {
      await _ensureClassOptions();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
      return;
    }
    if (!mounted) return;
    final sent = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => _SendMessageSheet(classOptions: _classOptions),
    );
    if (sent == true && mounted) {
      showToast(context, '消息已发送');
      _load();
    }
  }

  Future<void> _openCreateHomework() async {
    if (!appState.can(const ['/homework'])) {
      showToast(context, '当前账号无作业权限');
      return;
    }
    try {
      await _ensureClassOptions();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
      return;
    }
    if (!mounted) return;
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => _CreateHomeworkSheet(classOptions: _classOptions),
    );
    if (created == true && mounted) {
      showToast(context, '作业已布置');
      _load();
    }
  }

  Future<void> _openSuspend() async {
    if (!appState.can(const ['/classSuspend'])) {
      showToast(context, '当前账号无班级休课权限');
      return;
    }
    try {
      await _ensureClassOptions();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
      return;
    }
    if (!mounted) return;
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => SuspendCreatePage(classOptions: _classOptions)),
    );
    if (created == true && mounted) {
      showToast(context, '休课处理完成');
      _load();
    }
  }

  Future<void> _restoreSuspend(SuspendInfo item) async {
    final ok = await confirmDialog(context, '恢复课次', '确认恢复「${item.className ?? ''} ${dateOnly(item.lessonDate)}」的课次吗？');
    if (!ok) return;
    try {
      await Api.post('/class-suspends/${item.id}/restore');
      if (!mounted) return;
      showToast(context, '课次已恢复');
      _load();
    } on ApiException catch (e) {
      if (mounted) showToast(context, e.message);
    }
  }

  String get _actionLabel => switch (_tab) {
        0 => '发消息',
        1 => '布置作业',
        2 => '发起休课',
        _ => '',
      };

  VoidCallback? get _action {
    switch (_tab) {
      case 0:
        return appState.can(const ['/messagePush']) ? _openSendMessage : null;
      case 1:
        return appState.can(const ['/homework']) ? _openCreateHomework : null;
      case 2:
        return appState.can(const ['/classSuspend']) ? _openSuspend : null;
      default:
        return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('家校服务'),
        actions: [
          if (_action != null)
            TextButton.icon(
              onPressed: _action,
              icon: const Icon(Icons.add_rounded, size: 18),
              label: Text(_actionLabel),
              style: TextButton.styleFrom(foregroundColor: AppColors.primary),
            ),
          const SizedBox(width: 8),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: PillTabs(
              labels: _tabs,
              index: _tab,
              padding: EdgeInsets.zero,
              onChanged: (value) => setState(() => _tab = value),
            ),
          ),
          Expanded(
            child: _tab == 3
                ? const ClassAlbumTab()
                : _loading
                    ? const LoadingView()
                    : _error != null
                        ? ErrorView(_error!, onRetry: _load)
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: _tab == 0
                                ? _messageList()
                                : _tab == 1
                                    ? _homeworkList()
                                    : _suspendList(),
                          ),
          ),
        ],
      ),
    );
  }

  Widget _messageList() {
    if (_messages.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无消息', subtitle: '点击右上角「发消息」向家长推送通知')],
      );
    }
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
      itemCount: _messages.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final message = _messages[index];
        return SurfaceCard(
          padding: const EdgeInsets.all(15),
          onTap: () => _showMessageDetail(message),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      message.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                  ),
                  const SizedBox(width: 8),
                  StatusTag(message.msgType, compact: true),
                ],
              ),
              const SizedBox(height: 5),
              Text(
                message.content,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, color: AppColors.sub, height: 1.6),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Text(
                    '${dateTimeText(message.sentAt)} · ${message.audienceType}',
                    style: const TextStyle(fontSize: 11, color: AppColors.muted),
                  ),
                  const Spacer(),
                  Text(
                    '送达 ${message.receiverCount} · 已读 ${message.readCount}',
                    style: const TextStyle(fontSize: 11, color: AppColors.muted),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  void _showMessageDetail(MessageInfo message) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(message.title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.text)),
                  ),
                  StatusTag(message.msgType, compact: true),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                '${dateTimeText(message.sentAt)} · ${message.sender ?? '-'} · ${message.audienceType}',
                style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
              ),
              const SizedBox(height: 16),
              Text(message.content, style: const TextStyle(fontSize: 14, color: AppColors.text, height: 1.8)),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(child: MetricTile(label: '目标人数', value: '${message.targetCount}', unit: '人', align: CrossAxisAlignment.center)),
                  Expanded(child: MetricTile(label: '送达', value: '${message.receiverCount}', unit: '人', align: CrossAxisAlignment.center)),
                  Expanded(child: MetricTile(label: '已读', value: '${message.readCount}', unit: '人', align: CrossAxisAlignment.center, color: AppColors.success)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _homeworkList() {
    if (_homeworks.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无作业', subtitle: '点击右上角「布置作业」创建新的作业')],
      );
    }
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
      itemCount: _homeworks.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final item = _homeworks[index];
        return SurfaceCard(
          padding: const EdgeInsets.all(15),
          onTap: () => _showHomeworkDetail(item),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                  ),
                  const SizedBox(width: 8),
                  StatusTag(item.status, compact: true),
                ],
              ),
              const SizedBox(height: 5),
              Text(
                item.content,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, color: AppColors.sub, height: 1.6),
              ),
              const SizedBox(height: 10),
              Text(
                [
                  item.className ?? '未关联班级',
                  item.teacherName ?? '',
                  '发布 ${dateOnly(item.publishDate)}',
                  if ((item.dueDate ?? '').isNotEmpty) '截止 ${dateOnly(item.dueDate)}',
                ].where((e) => e.isNotEmpty).join(' · '),
                style: const TextStyle(fontSize: 11, color: AppColors.muted),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showHomeworkDetail(HomeworkInfo item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.text)),
              const SizedBox(height: 6),
              Text(
                '${item.className ?? ''} · ${item.publisher ?? item.teacherName ?? ''} · ${dateOnly(item.publishDate)}',
                style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
              ),
              const SizedBox(height: 16),
              Text(item.content, style: const TextStyle(fontSize: 14, color: AppColors.text, height: 1.8)),
              if ((item.dueDate ?? '').isNotEmpty) ...[
                const SizedBox(height: 14),
                Text('截止日期：${dateOnly(item.dueDate)}', style: const TextStyle(fontSize: 12, color: AppColors.sub)),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _suspendList() {
    if (_suspends.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [EmptyState(title: '暂无休课记录', subtitle: '点击右上角「发起休课」对指定课次发起休课')],
      );
    }
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
      itemCount: _suspends.length,
      separatorBuilder: (_, _) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final item = _suspends[index];
        return SurfaceCard(
          padding: const EdgeInsets.all(15),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      item.className ?? '未命名班级',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, color: AppColors.text),
                    ),
                  ),
                  StatusTag(item.status, compact: true),
                ],
              ),
              const SizedBox(height: 5),
              Text(
                [
                  dateOnly(item.lessonDate),
                  if ((item.startTime ?? '').isNotEmpty) '${timeOnly(item.startTime)}-${timeOnly(item.endTime)}',
                  item.courseName ?? '',
                ].where((e) => e.isNotEmpty).join(' · '),
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
              const SizedBox(height: 8),
              Text(
                '休课原因：${item.reason}',
                style: const TextStyle(fontSize: 12.5, color: AppColors.sub, height: 1.6),
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  Text(
                    '${item.operatorName ?? '-'} · ${dateTimeText(item.suspendedAt)}',
                    style: const TextStyle(fontSize: 11, color: AppColors.muted),
                  ),
                  const Spacer(),
                  if (item.status != '已恢复')
                    TextButton(
                      onPressed: () => _restoreSuspend(item),
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        minimumSize: const Size(0, 0),
                        foregroundColor: AppColors.primary,
                        backgroundColor: AppColors.primarySoft,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                      child: const Text('恢复课次', style: TextStyle(fontSize: 12)),
                    ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}

class _SendMessageSheet extends StatefulWidget {
  const _SendMessageSheet({required this.classOptions});

  final List<NamedOption> classOptions;

  @override
  State<_SendMessageSheet> createState() => _SendMessageSheetState();
}

class _SendMessageSheetState extends State<_SendMessageSheet> {
  final _title = TextEditingController();
  final _content = TextEditingController();
  String _audience = '全员';
  final Set<int> _classIds = {};
  bool _submitting = false;

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_title.text.trim().isEmpty || _content.text.trim().isEmpty) {
      showToast(context, '请填写通知标题与内容');
      return;
    }
    if (_audience == '班级' && _classIds.isEmpty) {
      showToast(context, '请选择接收班级');
      return;
    }
    setState(() => _submitting = true);
    try {
      await Api.post('/messages/send', {
        'title': _title.text.trim(),
        'content': _content.text.trim(),
        'audienceType': _audience,
        if (_audience == '班级') 'classIds': _classIds.toList(),
      });
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) {
        showToast(context, e.message);
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('发送消息', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.text)),
              const SizedBox(height: 16),
              TextField(
                controller: _title,
                decoration: const InputDecoration(hintText: '通知标题'),
                style: const TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _content,
                maxLines: 4,
                decoration: const InputDecoration(hintText: '通知内容'),
                style: const TextStyle(fontSize: 14, height: 1.6),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  const Text('接收对象', style: TextStyle(fontSize: 13, color: AppColors.sub)),
                  const SizedBox(width: 12),
                  for (final type in const ['全员', '班级'])
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(type),
                        selected: _audience == type,
                        onSelected: (_) => setState(() => _audience = type),
                      ),
                    ),
                ],
              ),
              if (_audience == '班级') ...[
                const SizedBox(height: 10),
                GestureDetector(
                  onTap: () async {
                    final selected = await showModalBottomSheet<Set<int>>(
                      context: context,
                      builder: (context) => _ClassPickerSheet(options: widget.classOptions, selected: _classIds),
                    );
                    if (selected != null) setState(() => _classIds..clear()..addAll(selected));
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppColors.border),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            _classIds.isEmpty ? '选择接收班级' : '已选择 ${_classIds.length} 个班级',
                            style: TextStyle(fontSize: 13.5, color: _classIds.isEmpty ? AppColors.muted : AppColors.text),
                          ),
                        ),
                        const Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.muted),
                      ],
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('发送'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CreateHomeworkSheet extends StatefulWidget {
  const _CreateHomeworkSheet({required this.classOptions});

  final List<NamedOption> classOptions;

  @override
  State<_CreateHomeworkSheet> createState() => _CreateHomeworkSheetState();
}

class _CreateHomeworkSheetState extends State<_CreateHomeworkSheet> {
  final _title = TextEditingController();
  final _content = TextEditingController();
  int? _classId;
  DateTime? _dueDate;
  bool _submitting = false;

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  Future<void> _pickClassName() async {
    final picked = await showModalBottomSheet<NamedOption>(
      context: context,
      builder: (context) => _SingleClassPicker(options: widget.classOptions),
    );
    if (picked != null) setState(() => _classId = picked.id);
  }

  Future<void> _pickDueDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime.now().add(const Duration(days: 1)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (picked != null) setState(() => _dueDate = picked);
  }

  Future<void> _submit() async {
    if (_classId == null) {
      showToast(context, '请选择班级');
      return;
    }
    if (_title.text.trim().isEmpty || _content.text.trim().isEmpty) {
      showToast(context, '请填写作业标题与内容');
      return;
    }
    setState(() => _submitting = true);
    try {
      await Api.post('/homeworks', {
        'classId': _classId,
        'title': _title.text.trim(),
        'content': _content.text.trim(),
        if (_dueDate != null) 'dueDate': dateText(_dueDate!),
      });
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) {
        showToast(context, e.message);
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    final className = widget.classOptions.firstWhere((e) => e.id == _classId, orElse: () => NamedOption(0, '')).name;
    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('布置作业', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.text)),
              const SizedBox(height: 16),
              GestureDetector(
                onTap: _pickClassName,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          _classId == null ? '选择班级' : className,
                          style: TextStyle(fontSize: 14, color: _classId == null ? AppColors.muted : AppColors.text),
                        ),
                      ),
                      const Icon(Icons.expand_more_rounded, size: 18, color: AppColors.muted),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _title,
                decoration: const InputDecoration(hintText: '作业标题'),
                style: const TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _content,
                maxLines: 4,
                decoration: const InputDecoration(hintText: '作业内容'),
                style: const TextStyle(fontSize: 14, height: 1.6),
              ),
              const SizedBox(height: 10),
              GestureDetector(
                onTap: _pickDueDate,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.event_outlined, size: 17, color: AppColors.muted),
                      const SizedBox(width: 8),
                      Text(
                        _dueDate == null ? '设置截止日期（可选）' : '截止 ${dateText(_dueDate!)}',
                        style: TextStyle(fontSize: 13.5, color: _dueDate == null ? AppColors.muted : AppColors.text),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('发布作业'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ClassPickerSheet extends StatefulWidget {
  const _ClassPickerSheet({required this.options, required this.selected});

  final List<NamedOption> options;
  final Set<int> selected;

  @override
  State<_ClassPickerSheet> createState() => _ClassPickerSheetState();
}

class _ClassPickerSheetState extends State<_ClassPickerSheet> {
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
                TextButton(
                  onPressed: () => Navigator.pop(context, _selected),
                  child: const Text('确定'),
                ),
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
                final checked = _selected.contains(option.id);
                return CheckboxListTile(
                  value: checked,
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

class _SingleClassPicker extends StatefulWidget {
  const _SingleClassPicker({required this.options});

  final List<NamedOption> options;

  @override
  State<_SingleClassPicker> createState() => _SingleClassPickerState();
}

class _SingleClassPickerState extends State<_SingleClassPicker> {
  String _keyword = '';

  @override
  Widget build(BuildContext context) {
    final options = widget.options.where((e) => e.name.contains(_keyword)).toList();
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 18, 20, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('选择班级', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.text)),
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
              itemBuilder: (context, index) => ListTile(
                dense: true,
                title: Text(options[index].name, style: const TextStyle(fontSize: 13.5)),
                onTap: () => Navigator.pop(context, options[index]),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
