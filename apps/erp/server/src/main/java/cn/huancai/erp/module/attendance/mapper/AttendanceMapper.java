package cn.huancai.erp.module.attendance.mapper;

import cn.huancai.erp.module.attendance.vo.AttendanceSummaryVO;
import cn.huancai.erp.module.attendance.vo.AttendanceVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceMapper {

    IPage<AttendanceVO> selectAttendancePage(IPage<AttendanceVO> page,
                                             @Param("campusId") Long campusId,
                                             @Param("keyword") String keyword,
                                             @Param("teacher") String teacher,
                                             @Param("status") String status,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    AttendanceSummaryVO selectSummary(@Param("campusId") Long campusId,
                                      @Param("keyword") String keyword,
                                      @Param("teacher") String teacher,
                                      @Param("status") String status,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    List<String> selectTeachers();
}
