package cn.huancai.erp.module.system.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;

    public SystemController(StudentMapper studentMapper, CampusMapper campusMapper) {
        this.studentMapper = studentMapper;
        this.campusMapper = campusMapper;
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        return ApiResult.ok(Map.of(
                "status", "UP",
                "students", studentMapper.selectCount(null),
                "campuses", campusMapper.selectCount(null),
                "time", LocalDateTime.now().toString()));
    }
}
