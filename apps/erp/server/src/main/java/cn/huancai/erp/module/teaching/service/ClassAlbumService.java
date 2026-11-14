package cn.huancai.erp.module.teaching.service;

import cn.huancai.erp.module.teaching.entity.ClassPhoto;
import cn.huancai.erp.module.teaching.mapper.ClassPhotoMapper;
import cn.huancai.erp.module.teaching.vo.ClassPhotoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 班级相册：照片落本地磁盘，数据库只保存访问地址。 */
@Service
public class ClassAlbumService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final String URL_PREFIX = "/uploads/";

    private final ClassPhotoMapper photoMapper;
    private final Path root;

    public ClassAlbumService(ClassPhotoMapper photoMapper,
                             @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.photoMapper = photoMapper;
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<ClassPhotoVO> list(Long classId) {
        return photoMapper.selectList(new LambdaQueryWrapper<ClassPhoto>()
                        .eq(ClassPhoto::getClassId, classId)
                        .orderByDesc(ClassPhoto::getCreatedAt)
                        .orderByDesc(ClassPhoto::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public ClassPhotoVO upload(Long classId, Long campusId, MultipartFile file, String uploader) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的照片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("单张照片不能超过 10MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、GIF 格式的图片");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path dir = root.resolve("class-album").resolve(String.valueOf(classId));
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("照片保存失败，请稍后重试", e);
        }

        ClassPhoto photo = new ClassPhoto();
        photo.setCampusId(campusId);
        photo.setClassId(classId);
        photo.setUrl(URL_PREFIX + "class-album/" + classId + "/" + fileName);
        photo.setUploader(uploader);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        return toVO(photo);
    }

    public boolean delete(Long id) {
        ClassPhoto photo = photoMapper.selectById(id);
        if (photo == null) {
            return false;
        }
        photoMapper.deleteById(id);
        deleteFile(photo.getUrl());
        return true;
    }

    private void deleteFile(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 文件已不存在时不影响数据库删除
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private ClassPhotoVO toVO(ClassPhoto photo) {
        ClassPhotoVO vo = new ClassPhotoVO();
        vo.setId(photo.getId());
        vo.setClassId(photo.getClassId());
        vo.setUrl(photo.getUrl());
        vo.setUploader(photo.getUploader());
        vo.setCreatedAt(photo.getCreatedAt());
        return vo;
    }
}
