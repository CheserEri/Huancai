package cn.huancai.erp.module.staff.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.staff.dto.PermissionTemplateForm;
import cn.huancai.erp.module.staff.entity.PermissionTemplate;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.PermissionTemplateMapper;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import cn.huancai.erp.module.staff.support.StaffRoles;
import cn.huancai.erp.module.staff.vo.PermissionTemplateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 组织架构 - 权限模板：内置角色模板可编辑不可删，自定义模板可自由增删改。 */
@RestController
@RequestMapping("/api/permission-templates")
public class PermissionTemplateController {

    private static final int MAX_NAME_LENGTH = 64;

    private final PermissionTemplateMapper templateMapper;
    private final StaffMapper staffMapper;

    public PermissionTemplateController(PermissionTemplateMapper templateMapper, StaffMapper staffMapper) {
        this.templateMapper = templateMapper;
        this.staffMapper = staffMapper;
    }

    @GetMapping
    public ApiResult<List<PermissionTemplateVO>> list() {
        List<PermissionTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<PermissionTemplate>()
                        .orderByDesc(PermissionTemplate::getBuiltin)
                        .orderByAsc(PermissionTemplate::getId));
        Map<Long, Integer> staffCounts = countBoundStaff();
        return ApiResult.ok(templates.stream().map(item -> toVO(item, staffCounts)).toList());
    }

    @PostMapping
    public ApiResult<PermissionTemplateVO> create(
            @RequestBody PermissionTemplateForm form,
            @RequestAttribute(name = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        if (!"admin".equals(operatorRole)) {
            return ApiResult.fail("只有管理员可以维护权限模板");
        }
        String error = validate(form, true);
        if (error != null) {
            return ApiResult.fail(error);
        }
        PermissionTemplate template = new PermissionTemplate();
        template.setName(form.getName().trim());
        template.setRole(blankToNull(form.getRole()));
        template.setBuiltin(0);
        template.setPermissions(PermissionCodec.encode(form.getPermissions()));
        templateMapper.insert(template);
        return ApiResult.ok(toVO(templateMapper.selectById(template.getId()), countBoundStaff()));
    }

    @PutMapping("/{id}")
    public ApiResult<PermissionTemplateVO> update(
            @PathVariable Long id,
            @RequestBody PermissionTemplateForm form,
            @RequestAttribute(name = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        if (!"admin".equals(operatorRole)) {
            return ApiResult.fail("只有管理员可以维护权限模板");
        }
        PermissionTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            return ApiResult.fail("权限模板不存在");
        }
        boolean builtin = Integer.valueOf(1).equals(existing.getBuiltin());
        String error = validate(form, !builtin);
        if (error != null) {
            return ApiResult.fail(error);
        }
        PermissionTemplate update = new PermissionTemplate();
        update.setId(id);
        // 内置模板的名称与适用角色由角色决定，只允许调整菜单勾选
        if (!builtin) {
            update.setName(form.getName().trim());
            update.setRole(blankToNull(form.getRole()));
        }
        update.setPermissions(PermissionCodec.encode(form.getPermissions()));
        templateMapper.updateById(update);
        // 保存模板后把新权限同步给所有绑定该模板的员工，实现「改模板即改员工权限」
        syncPermissionToBoundStaff(id, update.getPermissions());
        return ApiResult.ok(toVO(templateMapper.selectById(id), countBoundStaff()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @PathVariable Long id,
            @RequestAttribute(name = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        if (!"admin".equals(operatorRole)) {
            return ApiResult.fail("只有管理员可以维护权限模板");
        }
        PermissionTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            return ApiResult.fail("权限模板不存在");
        }
        if (Integer.valueOf(1).equals(existing.getBuiltin())) {
            return ApiResult.fail("内置角色模板不可删除");
        }
        // 先解除员工绑定，避免删除后留下指向不存在模板的悬空记录
        staffMapper.update(null, new LambdaUpdateWrapper<Staff>()
                .eq(Staff::getPermissionTemplateId, id)
                .set(Staff::getPermissionTemplateId, null));
        templateMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    /** 把模板最新的菜单集合覆盖写入绑定该模板的员工，管理员不受影响。 */
    private void syncPermissionToBoundStaff(Long templateId, String encodedPermissions) {
        staffMapper.update(null, new LambdaUpdateWrapper<Staff>()
                .eq(Staff::getPermissionTemplateId, templateId)
                .ne(Staff::getRole, "admin")
                .set(Staff::getMenuPermissions, encodedPermissions));
    }

    private Map<Long, Integer> countBoundStaff() {
        Map<Long, Integer> counts = new HashMap<>();
        List<Staff> bound = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .isNotNull(Staff::getPermissionTemplateId)
                .select(Staff::getId, Staff::getPermissionTemplateId));
        for (Staff staff : bound) {
            counts.merge(staff.getPermissionTemplateId(), 1, Integer::sum);
        }
        return counts;
    }

    /** 内置模板名称、角色不可改，因此仅在自定义模板场景校验名称与角色。 */
    private String validate(PermissionTemplateForm form, boolean checkNameAndRole) {
        if (!checkNameAndRole) {
            return null;
        }
        if (form.getName() == null || form.getName().isBlank()) {
            return "请填写模板名称";
        }
        if (form.getName().trim().length() > MAX_NAME_LENGTH) {
            return "模板名称不能超过 64 个字符";
        }
        String role = blankToNull(form.getRole());
        if (role != null && !StaffRoles.isValid(role)) {
            return "请选择有效的适用角色";
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PermissionTemplateVO toVO(PermissionTemplate template, Map<Long, Integer> staffCounts) {
        PermissionTemplateVO vo = new PermissionTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setRole(template.getRole());
        vo.setBuiltin(template.getBuiltin());
        vo.setPermissions(PermissionCodec.decode(template.getPermissions()));
        vo.setStaffCount(staffCounts.getOrDefault(template.getId(), 0));
        vo.setUpdatedAt(template.getUpdatedAt());
        return vo;
    }
}
