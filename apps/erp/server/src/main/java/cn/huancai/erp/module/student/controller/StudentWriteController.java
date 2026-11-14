package cn.huancai.erp.module.student.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.mapper.SpOrderMapper;
import cn.huancai.erp.module.student.dto.StudentForm;
import cn.huancai.erp.module.student.entity.Enrollment;
import cn.huancai.erp.module.student.entity.Guardian;
import cn.huancai.erp.module.student.entity.LessonTxn;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.EnrollmentMapper;
import cn.huancai.erp.module.student.mapper.GuardianMapper;
import cn.huancai.erp.module.student.mapper.LessonTxnMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.entity.ClassStudent;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import cn.huancai.erp.module.teaching.mapper.ClassStudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/students")
public class StudentWriteController {

    private final StudentMapper studentMapper;
    private final GuardianMapper guardianMapper;
    private final CampusMapper campusMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final SpOrderMapper spOrderMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final LessonTxnMapper lessonTxnMapper;

    public StudentWriteController(StudentMapper studentMapper,
                                  GuardianMapper guardianMapper,
                                  CampusMapper campusMapper,
                                  EnrollmentMapper enrollmentMapper,
                                  SpOrderMapper spOrderMapper,
                                  ClassStudentMapper classStudentMapper,
                                  ClassGroupMapper classGroupMapper,
                                  LessonTxnMapper lessonTxnMapper) {
        this.studentMapper = studentMapper;
        this.guardianMapper = guardianMapper;
        this.campusMapper = campusMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.spOrderMapper = spOrderMapper;
        this.classStudentMapper = classStudentMapper;
        this.classGroupMapper = classGroupMapper;
        this.lessonTxnMapper = lessonTxnMapper;
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody StudentForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return ApiResult.fail("请填写学员姓名");
        }
        if (form.getCampusId() == null) {
            return ApiResult.fail("请选择报读校区");
        }
        String campusName = campusName(form.getCampusId());
        if (campusName == null) {
            return ApiResult.fail("报读校区不存在");
        }

        Student student = new Student();
        student.setName(form.getName().trim());
        student.setGender(blankTo(form.getGender(), "未知"));
        student.setBirthday(form.getBirthday());
        student.setPhone(form.getPhone());
        student.setPrimaryRelation(form.getPrimaryRelation());
        student.setSecondaryRelation(form.getSecondaryRelation());
        student.setSecondaryPhone(form.getSecondaryPhone());
        student.setOtherRelation(form.getOtherRelation());
        student.setOtherPhone(form.getOtherPhone());
        student.setCampusId(form.getCampusId());
        student.setCampusText(campusName);
        student.setStatus(blankTo(form.getStatus(), "在读"));
        student.setGrade(form.getGrade());
        student.setSchool(form.getSchool());
        student.setPublicSchoolClass(form.getPublicSchoolClass());
        student.setAddress(form.getAddress());
        student.setWechat(form.getWechat());
        student.setSource(form.getSource());
        student.setChannelName(form.getChannelName());
        student.setExtraInfo(form.getExtraInfo());
        student.setRemark(form.getRemark());
        student.setEnrollDate(form.getEnrollDate() == null ? LocalDate.now() : form.getEnrollDate());
        studentMapper.insert(student);

        savePrimaryGuardian(student.getId(), form);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", student.getId());
        data.put("name", student.getName());
        return ApiResult.ok(data);
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody StudentForm form) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        if (form.getName() != null) {
            if (form.getName().isBlank()) {
                return ApiResult.fail("请填写学员姓名");
            }
            student.setName(form.getName().trim());
        }
        if (form.getCampusId() != null) {
            String campusName = campusName(form.getCampusId());
            if (campusName == null) {
                return ApiResult.fail("报读校区不存在");
            }
            student.setCampusId(form.getCampusId());
            student.setCampusText(campusName);
        }
        if (form.getGender() != null) student.setGender(form.getGender());
        if (form.getBirthday() != null) student.setBirthday(form.getBirthday());
        if (form.getPhone() != null) student.setPhone(form.getPhone());
        if (form.getPrimaryRelation() != null) student.setPrimaryRelation(form.getPrimaryRelation());
        if (form.getSecondaryRelation() != null) student.setSecondaryRelation(form.getSecondaryRelation());
        if (form.getSecondaryPhone() != null) student.setSecondaryPhone(form.getSecondaryPhone());
        if (form.getOtherRelation() != null) student.setOtherRelation(form.getOtherRelation());
        if (form.getOtherPhone() != null) student.setOtherPhone(form.getOtherPhone());
        if (form.getStatus() != null) student.setStatus(form.getStatus());
        if (form.getGrade() != null) student.setGrade(form.getGrade());
        if (form.getSchool() != null) student.setSchool(form.getSchool());
        if (form.getPublicSchoolClass() != null) student.setPublicSchoolClass(form.getPublicSchoolClass());
        if (form.getAddress() != null) student.setAddress(form.getAddress());
        if (form.getWechat() != null) student.setWechat(form.getWechat());
        if (form.getSource() != null) student.setSource(form.getSource());
        if (form.getChannelName() != null) student.setChannelName(form.getChannelName());
        if (form.getExtraInfo() != null) student.setExtraInfo(form.getExtraInfo());
        if (form.getRemark() != null) student.setRemark(form.getRemark());
        if (form.getEnrollDate() != null) student.setEnrollDate(form.getEnrollDate());
        studentMapper.updateById(student);

        savePrimaryGuardian(id, form);
        return ApiResult.ok(null);
    }

    /** 有家长姓名或电话时，同步主要联系人（已有则更新，没有则新增）。 */
    private void savePrimaryGuardian(Long studentId, StudentForm form) {
        if (isBlank(form.getGuardianName()) && isBlank(form.getPhone())) {
            return;
        }
        Guardian guardian = guardianMapper.selectOne(new LambdaQueryWrapper<Guardian>()
                .eq(Guardian::getStudentId, studentId)
                .eq(Guardian::getIsPrimary, 1)
                .last("LIMIT 1"));
        String name = isBlank(form.getGuardianName())
                ? blankTo(form.getPrimaryRelation(), "家长")
                : form.getGuardianName().trim();
        if (guardian == null) {
            guardian = new Guardian();
            guardian.setStudentId(studentId);
            guardian.setName(name);
            guardian.setRelation(form.getPrimaryRelation());
            guardian.setPhone(form.getPhone());
            guardian.setIsPrimary(1);
            guardianMapper.insert(guardian);
        } else {
            guardian.setName(name);
            if (form.getPrimaryRelation() != null) guardian.setRelation(form.getPrimaryRelation());
            if (form.getPhone() != null) guardian.setPhone(form.getPhone());
            guardianMapper.updateById(guardian);
        }
    }

    /* ---------- 学员详情操作 ---------- */

    private static final Set<String> ENROLLMENT_STATUSES = Set.of("停课", "结课", "生效");

    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/enrollments/{enrollmentId}/status")
    public ApiResult<Enrollment> updateEnrollmentStatus(@PathVariable Long id,
                                                        @PathVariable Long enrollmentId,
                                                        @RequestBody Map<String, String> body) {
        Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || !id.equals(enrollment.getStudentId())) {
            return ApiResult.fail("报读记录不存在");
        }
        String status = body == null ? null : body.get("status");
        if (status == null || !ENROLLMENT_STATUSES.contains(status)) {
            return ApiResult.fail("报读状态只能是「停课」「结课」「生效」");
        }
        if ("结课".equals(status) && "已退课".equals(enrollment.getStatus())) {
            return ApiResult.fail("已退课的报读不能结课");
        }
        String before = enrollment.getStatus();
        // 结课为终态：不可再恢复上课或改成其他状态
        if ("结课".equals(before)) {
            return ApiResult.fail(status.equals("结课") ? "该报读已结课" : "该报读已结课，结课为终态，不能恢复上课");
        }
        enrollment.setStatus(status);
        enrollmentMapper.updateById(enrollment);
        if ("结课".equals(status)) {
            // 结课：剩余学费与失效课时转为非课消收入，并清退班级成员
            settleOnClose(enrollment);
            if (enrollment.getClassId() != null) {
                classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, enrollment.getClassId())
                        .eq(ClassStudent::getStudentId, enrollment.getStudentId()));
                refreshClassEnrolled(enrollment.getClassId());
            }
        }
        return ApiResult.ok(enrollment);
    }

    /** 结课结算：剩余课时失效，剩余学费转入学员账户「剩余未用学费」，后续可退费或抵扣新报读。 */
    private void settleOnClose(Enrollment enrollment) {
        BigDecimal remainLessons = nz(enrollment.getRemainLessons());
        BigDecimal remainFee = nz(enrollment.getPaidAmount())
                .subtract(nz(enrollment.getConsumedAmount())).max(BigDecimal.ZERO);
        if (remainLessons.signum() <= 0 && remainFee.signum() <= 0) {
            return;
        }
        if (remainFee.signum() > 0) {
            // 剩余学费从报读绑定学费中释放，转入学员账户余额，避免两处重复显示
            Student student = studentMapper.selectById(enrollment.getStudentId());
            if (student != null) {
                student.setBalance(nz(student.getBalance()).add(remainFee));
                student.setUpdatedAt(LocalDateTime.now());
                studentMapper.updateById(student);
            }
            enrollment.setPaidAmount(nz(enrollment.getConsumedAmount()));
        }
        LessonTxn txn = new LessonTxn();
        txn.setCampusId(enrollment.getCampusId());
        txn.setEnrollmentId(enrollment.getId());
        txn.setStudentId(enrollment.getStudentId());
        txn.setType("结课");
        txn.setLessons(remainLessons.signum() > 0 ? remainLessons.negate() : BigDecimal.ZERO);
        txn.setUnitPrice(enrollment.getUnitPrice());
        txn.setAmount(remainFee.signum() > 0 ? remainFee : BigDecimal.ZERO);
        txn.setBalanceAfter(BigDecimal.ZERO);
        txn.setBizType("结课转余额");
        txn.setNote("结课：失效课时 " + plain(remainLessons) + "，剩余学费 " + money(remainFee) + " 转入账户剩余未用学费");
        txn.setCreatedAt(LocalDateTime.now());
        lessonTxnMapper.insert(txn);
        enrollment.setRemainLessons(BigDecimal.ZERO);
        enrollmentMapper.updateById(enrollment);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return "￥" + value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 重新统计班级人数与满班率。 */
    private void refreshClassEnrolled(Long classId) {
        ClassGroup clazz = classGroupMapper.selectById(classId);
        if (clazz == null) {
            return;
        }
        Long count = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, classId));
        int enrolled = count == null ? 0 : count.intValue();
        clazz.setEnrolledCount(enrolled);
        Integer capacity = clazz.getCapacity();
        clazz.setStuRate(capacity == null || capacity <= 0
                ? null
                : BigDecimal.valueOf(enrolled * 100.0 / capacity).setScale(2, RoundingMode.HALF_UP));
        classGroupMapper.updateById(clazz);
    }

    @PutMapping("/{id}/enrollments/{enrollmentId}/expire")
    public ApiResult<Enrollment> updateEnrollmentExpire(@PathVariable Long id,
                                                        @PathVariable Long enrollmentId,
                                                        @RequestBody Map<String, String> body) {
        Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || !id.equals(enrollment.getStudentId())) {
            return ApiResult.fail("报读记录不存在");
        }
        String expireDate = body == null ? null : body.get("expireDate");
        if (isBlank(expireDate)) {
            enrollment.setExpireDate(null);
        } else {
            try {
                enrollment.setExpireDate(LocalDate.parse(expireDate));
            } catch (Exception ex) {
                return ApiResult.fail("有效期格式不正确，应为 YYYY-MM-DD");
            }
        }
        enrollmentMapper.updateById(enrollment);
        return ApiResult.ok(enrollment);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> delete(@PathVariable Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        Long enrollments = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, id));
        if (enrollments != null && enrollments > 0) {
            return ApiResult.fail("该学员有 " + enrollments + " 条报读记录，请先退课后再删除");
        }
        Long orders = spOrderMapper.selectCount(new LambdaQueryWrapper<SpOrder>()
                .eq(SpOrder::getStudentId, id));
        if (orders != null && orders > 0) {
            return ApiResult.fail("该学员有 " + orders + " 条订单记录，不能删除");
        }
        guardianMapper.delete(new LambdaQueryWrapper<Guardian>().eq(Guardian::getStudentId, id));
        studentMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    private String campusName(Long campusId) {
        Campus campus = campusMapper.selectById(campusId);
        return campus == null ? null : campus.getName();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankTo(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
