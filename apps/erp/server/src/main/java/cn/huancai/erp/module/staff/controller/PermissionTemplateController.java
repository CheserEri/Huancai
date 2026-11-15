package cn.huancai.erp.module.staff.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.staff.dto.PermissionTemplateForm;
import cn.huancai.erp.module.staff.entity.PermissionTemplate;
import cn.huancai.erp.module.staff.mapper.PermissionTemplateMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import cn.huancai.erp.module.staff.support.StaffRoles;
import cn.huancai.erp.module.staff.vo.PermissionTemplateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 组织架构 - 权限模板：内置角色模板可编辑不可删，自定义模板可自由增删改。 */
@RestController
@RequestMapping("/api/permission-templates")
public class PermissionTemplateController {

    private static final int MAX_NAME_LENGTH = 64;

    private final PermissionTemplateMapper templateMapper;

    public PermissionTemplateController(PermissionTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @GetMapping
    public ApiResult<List<PermissionTemplateVO>> list() {
        List<PermissionTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<PermissionTemplate>()
                        .orderByDesc(PermissionTemplate::getBuiltin)
                        .orderByAsc(PermissionTemplate::getId));
        return ApiResult.ok(templates.stream().map(this::toVO).toList());
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
        return ApiResult.ok(toVO(templateMapper.selectById(template.getId())));
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
        return ApiResult.ok(toVO(templateMapper.selectById(id)));
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
        templateMapper.deleteById(id);
        return ApiResult.ok(null);
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

    private PermissionTemplateVO toVO(PermissionTemplate template) {
        PermissionTemplateVO vo = new PermissionTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setRole(template.getRole());
        vo.setBuiltin(template.getBuiltin());
        vo.setPermissions(PermissionCodec.decode(template.getPermissions()));
        vo.setUpdatedAt(template.getUpdatedAt());
        return vo;
    }
}
