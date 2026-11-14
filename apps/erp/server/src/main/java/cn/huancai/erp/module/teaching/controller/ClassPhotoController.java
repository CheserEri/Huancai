package cn.huancai.erp.module.teaching.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.teaching.service.ClassAlbumService;
import cn.huancai.erp.module.teaching.vo.ClassPhotoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 班级相册查看（上传与删除见 /api/class-album）。 */
@RestController
@RequestMapping("/api/classes")
public class ClassPhotoController {

    private final ClassAlbumService albumService;

    public ClassPhotoController(ClassAlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping("/{classId}/photos")
    public ApiResult<List<ClassPhotoVO>> list(@PathVariable Long classId) {
        return ApiResult.ok(albumService.list(classId));
    }
}
