package cn.huancai.erp.module.teaching.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.common.OperatorResolver;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.service.ClassAlbumService;
import cn.huancai.erp.module.teaching.vo.ClassPhotoVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 家校服务-班级相册：上传与删除照片（查看接口见 /api/classes/{classId}/photos）。 */
@RestController
@RequestMapping("/api/class-album")
public class ClassAlbumWriteController {

    private final ClassAlbumService albumService;
    private final ClassGroupMapper classGroupMapper;
    private final OperatorResolver operatorResolver;

    public ClassAlbumWriteController(ClassAlbumService albumService,
                                     ClassGroupMapper classGroupMapper,
                                     OperatorResolver operatorResolver) {
        this.albumService = albumService;
        this.classGroupMapper = classGroupMapper;
        this.operatorResolver = operatorResolver;
    }

    @PostMapping("/{classId}/photos")
    public ApiResult<ClassPhotoVO> upload(@PathVariable Long classId,
                                          @RequestParam("file") MultipartFile file) {
        var classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null) {
            return ApiResult.fail("班级不存在");
        }
        try {
            return ApiResult.ok(albumService.upload(
                    classId, classGroup.getCampusId(), file, operatorResolver.currentName()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @DeleteMapping("/photos/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        if (!albumService.delete(id)) {
            return ApiResult.fail("照片不存在或已被删除");
        }
        return ApiResult.ok(null);
    }
}
