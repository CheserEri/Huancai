package cn.huancai.erp.module.student.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.student.dto.StudentQuery;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.student.vo.StudentListVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentMapper studentMapper;

    public StudentController(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    @GetMapping
    public ApiResult<IPage<StudentListVO>> list(StudentQuery query) {
        if (query.getPage() < 1) {
            query.setPage(1);
        }
        if (query.getSize() < 1 || query.getSize() > 200) {
            query.setSize(20);
        }
        Page<StudentListVO> page = new Page<>(query.getPage(), query.getSize());
        return ApiResult.ok(studentMapper.selectStudentPage(page, query));
    }

    @GetMapping("/channels")
    public ApiResult<List<String>> channels() {
        return ApiResult.ok(studentMapper.selectChannels());
    }
}
