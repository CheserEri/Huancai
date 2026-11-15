package cn.huancai.erp.module.staff.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import cn.huancai.erp.module.staff.support.StaffRoles;
import cn.huancai.erp.module.staff.vo.StaffSummaryVO;
import cn.huancai.erp.module.staff.vo.StaffVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffMapper staffMapper;

    public StaffController(StaffMapper staffMapper) {
        this.staffMapper = staffMapper;
    }

    @GetMapping
    public ApiResult<List<StaffVO>> list(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        List<StaffVO> list = staffMapper.selectStaffList(campusId, keyword, role, status);
        for (StaffVO vo : list) {
            vo.setPermissions(PermissionCodec.resolve(vo.getRole(), vo.getMenuPermissions()));
            vo.setExtraRoles(StaffRoles.splitExtras(vo.getExtraRolesText()));
        }
        return ApiResult.ok(list);
    }

    @GetMapping("/summary")
    public ApiResult<StaffSummaryVO> summary(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(staffMapper.selectSummary(campusId, keyword, role, status));
    }
}
