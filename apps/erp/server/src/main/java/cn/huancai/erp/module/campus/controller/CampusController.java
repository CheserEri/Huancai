package cn.huancai.erp.module.campus.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campus")
public class CampusController {

    private final CampusMapper campusMapper;

    public CampusController(CampusMapper campusMapper) {
        this.campusMapper = campusMapper;
    }

    @GetMapping
    public ApiResult<List<Campus>> list() {
        List<Campus> list = campusMapper.selectList(
                new LambdaQueryWrapper<Campus>()
                        .eq(Campus::getStatus, 1)
                        .orderByAsc(Campus::getId));
        return ApiResult.ok(list);
    }
}
