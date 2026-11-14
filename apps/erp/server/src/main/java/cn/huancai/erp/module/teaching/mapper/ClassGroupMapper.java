package cn.huancai.erp.module.teaching.mapper;

import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.vo.ClassAssignStudentVO;
import cn.huancai.erp.module.teaching.vo.ClassAttendanceVO;
import cn.huancai.erp.module.teaching.vo.ClassCommentItemVO;
import cn.huancai.erp.module.teaching.vo.ClassScheduleVO;
import cn.huancai.erp.module.teaching.vo.ClassStudentVO;
import cn.huancai.erp.module.teaching.vo.ClassSummaryVO;
import cn.huancai.erp.module.teaching.vo.ClassVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ClassGroupMapper extends BaseMapper<ClassGroup> {

    IPage<ClassVO> selectClassPage(IPage<ClassVO> page,
                                   @Param("keyword") String keyword,
                                   @Param("campusId") Long campusId,
                                   @Param("teacher") String teacher,
                                   @Param("status") String status,
                                   @Param("source") String source,
                                   @Param("enrolling") Integer enrolling,
                                   @Param("lessonClass") String lessonClass,
                                   @Param("room") String room,
                                   @Param("courseCategory") String courseCategory,
                                   @Param("courseName") String courseName,
                                   @Param("weekday") String weekday,
                                   @Param("timePeriod") String timePeriod,
                                   @Param("includeClosed") Boolean includeClosed,
                                   @Param("sortField") String sortField,
                                   @Param("sortOrder") String sortOrder);

    ClassVO selectClassById(@Param("id") Long id);

    ClassSummaryVO selectSummary(@Param("keyword") String keyword,
                                 @Param("campusId") Long campusId,
                                 @Param("teacher") String teacher,
                                 @Param("status") String status,
                                 @Param("source") String source,
                                 @Param("enrolling") Integer enrolling,
                                 @Param("lessonClass") String lessonClass,
                                 @Param("room") String room,
                                 @Param("courseCategory") String courseCategory,
                                 @Param("courseName") String courseName,
                                 @Param("weekday") String weekday,
                                 @Param("timePeriod") String timePeriod,
                                 @Param("includeClosed") Boolean includeClosed);

    ClassSummaryVO selectStudentSummary(@Param("keyword") String keyword,
                                        @Param("campusId") Long campusId,
                                        @Param("teacher") String teacher,
                                        @Param("status") String status,
                                        @Param("source") String source,
                                        @Param("enrolling") Integer enrolling,
                                        @Param("lessonClass") String lessonClass,
                                        @Param("room") String room,
                                        @Param("courseCategory") String courseCategory,
                                        @Param("courseName") String courseName,
                                        @Param("weekday") String weekday,
                                        @Param("timePeriod") String timePeriod,
                                        @Param("includeClosed") Boolean includeClosed);

    List<ClassStudentVO> selectClassStudents(@Param("classId") Long classId);

    /** 分班弹窗：同课程的可选班级（排除已结班）。 */
    List<ClassVO> selectCourseClasses(@Param("courseId") Long courseId);

    /** 分班弹窗：该课程下的候选学员（含当前所在班级）。 */
    List<ClassAssignStudentVO> selectCourseStudents(@Param("courseId") Long courseId);

    List<ClassScheduleVO> selectClassSchedules(@Param("classId") Long classId,
                                               @Param("doneOnly") Boolean doneOnly);

    List<ClassAttendanceVO> selectClassAttendance(@Param("classId") Long classId,
                                                  @Param("scheduleId") Long scheduleId);

    List<ClassCommentItemVO> selectClassComments(@Param("classId") Long classId);

    BigDecimal selectAttendanceRate(@Param("classId") Long classId);

    List<String> selectTeachers();

    List<String> selectLessonClasses();

    List<String> selectCourseNames();

    List<String> selectCategories();

    List<String> selectRooms();
}
