package cn.huancai.erp.module.staff.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.config.AuthInterceptor;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.staff.dto.StaffForm;
import cn.huancai.erp.module.staff.dto.StaffPermissionForm;
import cn.huancai.erp.module.staff.entity.Staff;
import cn.huancai.erp.module.staff.mapper.StaffMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import cn.huancai.erp.module.staff.vo.StaffOptionsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** 员工新建 / 编辑 / 在职状态切换的写接口。 */
@RestController
@RequestMapping("/api/staff")
public class StaffWriteController {

    private static final Set<String> ROLES =
            Set.of("admin", "manager", "teacher", "consultant", "finance");

    private final StaffMapper staffMapper;
    private final CampusMapper campusMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffWriteController(StaffMapper staffMapper, CampusMapper campusMapper) {
        this.staffMapper = staffMapper;
        this.campusMapper = campusMapper;
    }

    @GetMapping("/options")
    public ApiResult<StaffOptionsVO> options() {
        List<StaffOptionsVO.CampusOption> campuses = campusMapper
                .selectList(new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getId))
                .stream()
                .map(item -> new StaffOptionsVO.CampusOption(item.getId(), item.getName(), item.getStatus()))
                .toList();
        return ApiResult.ok(new StaffOptionsVO(campuses, List.of("admin", "manager", "teacher", "consultant", "finance")));
    }

    @PostMapping
    public ApiResult<Staff> create(@RequestBody StaffForm form) {
        String error = validate(form, null);
        if (error != null) {
            return ApiResult.fail(error);
        }
        String account = trim(form.getAccount());
        if (account != null && (form.getPassword() == null || form.getPassword().isBlank())) {
            return ApiResult.fail("填写登录账号时必须设置登录密码");
        }
        Staff staff = new Staff();
        staff.setName(form.getName().trim());
        staff.setPhone(trim(form.getPhone()));
        staff.setAccount(account);
        staff.setPasswordHash(account == null ? null : passwordEncoder.encode(form.getPassword()));
        staff.setRole(form.getRole());
        staff.setMenuPermissions(formatPermissions(form));
        staff.setTitle(trim(form.getTitle()));
        staff.setCampusId(form.getCampusId());
        staff.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        staffMapper.insert(staff);
        return ApiResult.ok(staff);
    }

    @PutMapping("/{id}")
    public ApiResult<Staff> update(@PathVariable Long id, @RequestBody StaffForm form) {
        Staff existing = staffMapper.selectById(id);
        if (existing == null) {
            return ApiResult.fail("员工不存在");
        }
        String error = validate(form, id);
        if (error != null) {
            return ApiResult.fail(error);
        }
        String account = trim(form.getAccount());
        String password = form.getPassword();
        boolean hasPassword = existing.getPasswordHash() != null && !existing.getPasswordHash().isBlank();
        if (account != null && !hasPassword && (password == null || password.isBlank())) {
            return ApiResult.fail("请为该登录账号设置密码");
        }
        LambdaUpdateWrapper<Staff> update = new LambdaUpdateWrapper<Staff>()
                .eq(Staff::getId, id)
                .set(Staff::getName, form.getName().trim())
                .set(Staff::getPhone, trim(form.getPhone()))
                .set(Staff::getAccount, account)
                .set(Staff::getRole, form.getRole())
                .set(Staff::getMenuPermissions, formatPermissions(form))
                .set(Staff::getTitle, trim(form.getTitle()))
                .set(Staff::getCampusId, form.getCampusId())
                .set(Staff::getStatus, form.getStatus() == null ? existing.getStatus() : form.getStatus());
        if (password != null && !password.isBlank()) {
            update.set(Staff::getPasswordHash, passwordEncoder.encode(password));
        }
        staffMapper.update(null, update);
        return ApiResult.ok(staffMapper.selectById(id));
    }

    /** 组织架构页直接调整员工的可见菜单；仅管理员可操作。 */
    @PutMapping("/{id}/permissions")
    public ApiResult<List<String>> updatePermissions(
            @PathVariable Long id,
            @RequestBody StaffPermissionForm form,
            @RequestAttribute(name = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        if (!"admin".equals(operatorRole)) {
            return ApiResult.fail("只有管理员可以分配菜单权限");
        }
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            return ApiResult.fail("员工不存在");
        }
        if ("admin".equals(staff.getRole())) {
            return ApiResult.fail("管理员固定拥有全部菜单权限，无需配置");
        }
        String encoded = PermissionCodec.encode(form.getPermissions());
        staffMapper.update(null, new LambdaUpdateWrapper<Staff>()
                .eq(Staff::getId, id)
                .set(Staff::getMenuPermissions, encoded));
        return ApiResult.ok(PermissionCodec.resolve(staff.getRole(), encoded));
    }

    @PostMapping("/{id}/status")
    public ApiResult<Staff> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            return ApiResult.fail("员工不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            return ApiResult.fail("状态只能是 0（离职）或 1（在职）");
        }
        staff.setStatus(status);
        staffMapper.updateById(staff);
        return ApiResult.ok(staff);
    }

    private String validate(StaffForm form, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "员工姓名不能为空";
        }
        if (form.getRole() == null || !ROLES.contains(form.getRole())) {
            return "请选择有效的角色";
        }
        if (form.getStatus() != null && form.getStatus() != 0 && form.getStatus() != 1) {
            return "状态只能是 0（离职）或 1（在职）";
        }
        String account = trim(form.getAccount());
        if (account != null) {
            LambdaQueryWrapper<Staff> query = new LambdaQueryWrapper<Staff>()
                    .eq(Staff::getAccount, account);
            if (excludeId != null) {
                query.ne(Staff::getId, excludeId);
            }
            Long exists = staffMapper.selectCount(query);
            if (exists != null && exists > 0) {
                return "登录账号「" + account + "」已被使用";
            }
        }
        String phone = trim(form.getPhone());
        if (phone != null) {
            LambdaQueryWrapper<Staff> query = new LambdaQueryWrapper<Staff>()
                    .eq(Staff::getPhone, phone);
            if (excludeId != null) {
                query.ne(Staff::getId, excludeId);
            }
            Long exists = staffMapper.selectCount(query);
            if (exists != null && exists > 0) {
                return "手机号「" + phone + "」已被其他员工使用";
            }
        }
        if (form.getPassword() != null && !form.getPassword().isBlank() && form.getPassword().length() < 6) {
            return "登录密码至少 6 位";
        }
        if (form.getCampusId() != null && campusMapper.selectById(form.getCampusId()) == null) {
            return "所选校区不存在";
        }
        return null;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** admin 固定拥有全部菜单权限，其余角色按表单勾选的菜单路径存储。 */
    private String formatPermissions(StaffForm form) {
        if ("admin".equals(form.getRole())) {
            return null;
        }
        return PermissionCodec.encode(form.getPermissions());
    }
}
