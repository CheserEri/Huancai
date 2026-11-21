package cn.huancai.erp.module.student.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.student.dto.StudentQuery;
import cn.huancai.erp.module.student.entity.Guardian;
import cn.huancai.erp.module.student.entity.LessonTxn;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.entity.StudentOperationLog;
import cn.huancai.erp.module.student.mapper.GuardianMapper;
import cn.huancai.erp.module.student.mapper.LessonTxnMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.student.mapper.StudentOperationLogMapper;
import cn.huancai.erp.module.student.vo.StudentSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentProfileController {

    private final StudentMapper studentMapper;
    private final GuardianMapper guardianMapper;
    private final LessonTxnMapper lessonTxnMapper;
    private final StudentOperationLogMapper studentOperationLogMapper;

    public StudentProfileController(StudentMapper studentMapper,
                                    GuardianMapper guardianMapper,
                                    LessonTxnMapper lessonTxnMapper,
                                    StudentOperationLogMapper studentOperationLogMapper) {
        this.studentMapper = studentMapper;
        this.guardianMapper = guardianMapper;
        this.lessonTxnMapper = lessonTxnMapper;
        this.studentOperationLogMapper = studentOperationLogMapper;
    }

    @GetMapping("/summary")
    public ApiResult<StudentSummaryVO> summary(StudentQuery query) {
        return ApiResult.ok(studentMapper.selectStudentSummary(query));
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            return ApiResult.fail("学员不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("student", student);
        data.put("guardians", guardianMapper.selectList(
                new LambdaQueryWrapper<Guardian>()
                        .eq(Guardian::getStudentId, id)
                        .orderByDesc(Guardian::getIsPrimary)));
        data.put("enrollments", studentMapper.selectEnrollments(id));

        List<LessonTxn> txns = lessonTxnMapper.selectList(
                new LambdaQueryWrapper<LessonTxn>()
                        .eq(LessonTxn::getStudentId, id)
                        .orderByDesc(LessonTxn::getId)
                        .last("LIMIT 50"));
        data.put("txns", txns);
        data.put("operationLogs", studentOperationLogMapper.selectList(
                new LambdaQueryWrapper<StudentOperationLog>()
                        .eq(StudentOperationLog::getStudentId, id)
                        .orderByDesc(StudentOperationLog::getId)
                        .last("LIMIT 100")));
        return ApiResult.ok(data);
    }

    @GetMapping("/{id}/enrollments")
    public ApiResult<List<?>> enrollments(@PathVariable Long id) {
        return ApiResult.ok(studentMapper.selectEnrollments(id));
    }

    @GetMapping("/{id}/lesson-txns")
    public ApiResult<List<LessonTxn>> lessonTxns(@PathVariable Long id,
                                                 @RequestParam(required = false) Long enrollmentId) {
        return ApiResult.ok(lessonTxnMapper.selectList(
                new LambdaQueryWrapper<LessonTxn>()
                        .eq(LessonTxn::getStudentId, id)
                        .eq(enrollmentId != null, LessonTxn::getEnrollmentId, enrollmentId)
                        .orderByDesc(LessonTxn::getId)
                        .last("LIMIT 100")));
    }
}
