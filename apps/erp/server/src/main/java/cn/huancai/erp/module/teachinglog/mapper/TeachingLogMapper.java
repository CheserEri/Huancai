package cn.huancai.erp.module.teachinglog.mapper;

import cn.huancai.erp.module.teachinglog.vo.TeachingLogRowVO;
import cn.huancai.erp.module.teachinglog.vo.TeachingLogStudentVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TeachingLogMapper {

    List<TeachingLogRowVO> selectRows(@Param("date") LocalDate date,
                                      @Param("today") LocalDate today,
                                      @Param("supplement") boolean supplement,
                                      @Param("campusId") Long campusId,
                                      @Param("room") String room,
                                      @Param("lessonMode") Integer lessonMode,
                                      @Param("courseId") Long courseId,
                                      @Param("teacher") String teacher,
                                      @Param("keyword") String keyword,
                                      @Param("recordStatus") String recordStatus);

    List<TeachingLogStudentVO> selectRoster(@Param("scheduleId") Long scheduleId,
                                            @Param("classId") Long classId);

    long countAttendance(@Param("scheduleId") Long scheduleId);

    int insertAttendance(@Param("campusId") Long campusId,
                         @Param("scheduleId") Long scheduleId,
                         @Param("classId") Long classId,
                         @Param("studentId") Long studentId,
                         @Param("enrollmentId") Long enrollmentId,
                         @Param("teacherId") Long teacherId,
                         @Param("lessonDate") LocalDate lessonDate,
                         @Param("lessons") BigDecimal lessons,
                         @Param("status") String status,
                         @Param("remark") String remark,
                         @Param("checkedAt") LocalDateTime checkedAt);
}
