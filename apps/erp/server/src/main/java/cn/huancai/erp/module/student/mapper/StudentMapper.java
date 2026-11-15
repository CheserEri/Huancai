package cn.huancai.erp.module.student.mapper;

import cn.huancai.erp.module.student.dto.StudentQuery;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.vo.EnrollmentVO;
import cn.huancai.erp.module.student.vo.StudentListVO;
import cn.huancai.erp.module.student.vo.StudentSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StudentMapper extends BaseMapper<Student> {

    IPage<StudentListVO> selectStudentPage(IPage<StudentListVO> page, @Param("q") StudentQuery query);

    StudentSummaryVO selectStudentSummary(@Param("q") StudentQuery query);

    List<EnrollmentVO> selectEnrollments(@Param("studentId") Long studentId);

    List<String> selectChannels();

    List<String> selectGrades();
}
