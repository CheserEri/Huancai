import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../core/api.dart';
import '../core/config.dart';
import '../core/format.dart';
import '../core/models.dart';
import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'payment/shared.dart';

/// 家校服务 - 班级相册：助教现场拍照或从相册多选上传，与班级相册数据互通。
class ClassAlbumTab extends StatefulWidget {
  const ClassAlbumTab({super.key});

  @override
  State<ClassAlbumTab> createState() => _ClassAlbumTabState();
}

class _ClassAlbumTabState extends State<ClassAlbumTab> {
  static const _maxFileSize = 10 * 1024 * 1024;

  final _picker = ImagePicker();

  List<NamedOption> _classes = const [];
  List<ClassPhoto> _photos = const [];
  NamedOption? _class;
  bool _loading = true;
  bool _uploading = false;
  int _uploadIndex = 0;
  int _uploadTotal = 0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<Map<String, dynamic>>('/schedules/options');
      final classes = mapList(data['classes']).map((e) => NamedOption(intOf(e['id']), str(e['name']))).toList();
      if (!mounted) return;
      setState(() {
        _classes = classes;
        _loading = false;
      });
      if (classes.isNotEmpty) {
        await _selectClass(classes.first);
      }
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
        _error = '班级列表加载失败';
      });
    }
  }

  Future<void> _pickClass() async {
    final picked = await showOptionPicker<NamedOption>(
      context: context,
      title: '选择班级',
      items: _classes,
      labelOf: (item) => item.name,
      selected: _class,
    );
    if (picked != null) await _selectClass(picked);
  }

  Future<void> _selectClass(NamedOption option) async {
    setState(() {
      _class = option;
      _loading = true;
      _error = null;
    });
    try {
      final data = await Api.get<List<dynamic>>('/classes/${option.id}/photos');
      if (!mounted) return;
      setState(() {
        _photos = mapList(data).map(ClassPhoto.from).toList();
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
        _error = '相册加载失败';
      });
    }
  }

  Future<void> _reload() async {
    final current = _class;
    if (current != null) await _selectClass(current);
  }

  Future<void> _pickSource() async {
    if (_class == null) {
      showToast(context, '请先选择班级');
      return;
    }
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined, color: AppColors.primary),
              title: const Text('拍照上传'),
              subtitle: const Text('现场拍摄一张照片'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined, color: AppColors.primary),
              title: const Text('从相册选择'),
              subtitle: const Text('可一次选择多张照片'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    if (source == ImageSource.camera) {
      final shot = await _picker.pickImage(source: ImageSource.camera, maxWidth: 1600, imageQuality: 80);
      if (shot != null) await _upload([shot]);
    } else {
      final picked = await _picker.pickMultiImage(maxWidth: 1600, imageQuality: 80);
      if (picked.isNotEmpty) await _upload(picked);
    }
  }

  Future<void> _upload(List<XFile> files) async {
    final classId = _class?.id;
    if (classId == null) return;
    final valid = <XFile>[];
    var skipped = 0;
    for (final file in files) {
      final size = await file.length();
      if (size > _maxFileSize) {
        skipped += 1;
      } else {
        valid.add(file);
      }
    }
    if (valid.isEmpty) {
      if (mounted) showToast(context, '照片超过 10MB 限制，请重新选择');
      return;
    }
    setState(() {
      _uploading = true;
      _uploadTotal = valid.length;
      _uploadIndex = 0;
    });
    var failed = 0;
    for (final file in valid) {
      try {
        await Api.upload<Map<String, dynamic>>('/class-album/$classId/photos', filePath: file.path);
      } on ApiException {
        failed += 1;
      } catch (_) {
        failed += 1;
      }
      if (!mounted) return;
      setState(() => _uploadIndex += 1);
    }
    if (!mounted) return;
    setState(() => _uploading = false);
    await _reload();
    if (!mounted) return;
    final success = valid.length - failed;
    final parts = <String>['成功上传 $success 张'];
    if (failed > 0) parts.add('$failed 张上传失败');
    if (skipped > 0) parts.add('$skipped 张超过 10MB 已跳过');
    showToast(context, parts.join('，'));
  }

  @override
  Widget build(BuildContext context) {
    if (_classes.isEmpty && _loading) return const LoadingView();
    if (_classes.isEmpty && _error != null) return ErrorView(_error!, onRetry: _init);

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
          child: PickerField(label: '班级', value: _class?.name, hint: '请选择班级', onTap: _pickClass),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _uploading ? null : _pickSource,
                  icon: const Icon(Icons.add_a_photo_outlined, size: 18),
                  label: const Text('拍照 / 上传'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.primary,
                    side: const BorderSide(color: AppColors.primary),
                    minimumSize: const Size(0, 40),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              IconButton.outlined(
                onPressed: _uploading ? null : _reload,
                icon: const Icon(Icons.refresh_rounded, size: 20),
                tooltip: '刷新相册',
              ),
            ],
          ),
        ),
        if (_uploading)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: LinearProgressIndicator(
                    value: _uploadTotal == 0 ? null : _uploadIndex / _uploadTotal,
                    minHeight: 5,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  '正在上传 $_uploadIndex / $_uploadTotal 张',
                  style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                ),
              ],
            ),
          ),
        Expanded(child: _photoBody()),
      ],
    );
  }

  Widget _photoBody() {
    if (_loading) return const LoadingView();
    if (_error != null) return ErrorView(_error!, onRetry: _reload);
    return RefreshIndicator(
      onRefresh: _reload,
      child: _photos.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                EmptyState(
                  title: '该班级暂无相册照片',
                  subtitle: '点击上方「拍照 / 上传」为班级添加现场照片，家长端与管理端可同步查看',
                  icon: Icons.photo_library_outlined,
                ),
              ],
            )
          : GridView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
              itemCount: _photos.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 8,
                mainAxisSpacing: 8,
                childAspectRatio: 1,
              ),
              itemBuilder: (context, index) {
                final url = AppConfig.fileUrl(_photos[index].url);
                return GestureDetector(
                  onTap: () => showDialog(
                    context: context,
                    builder: (context) => Dialog(
                      backgroundColor: Colors.transparent,
                      insetPadding: const EdgeInsets.all(12),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: InteractiveViewer(child: Image.network(url, fit: BoxFit.contain)),
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
            ),
    );
  }
}
