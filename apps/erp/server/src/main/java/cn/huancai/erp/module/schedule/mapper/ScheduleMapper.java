package cn.huancai.erp.module.schedule.mapper;

import cn.huancai.erp.module.schedule.entity.Schedule;
import cn.huancai.erp.module.schedule.vo.ScheduleOptionVO;
import cn.huancai.erp.module.schedule.vo.ScheduleSummaryVO;
import cn.huancai.erp.module.schedule.vo.ScheduleVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleMapper extends BaseMapper<Schedule> {

    IPage<ScheduleVO> selectSchedulePage(IPage<ScheduleVO> page,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end,
                                         @Param("campusId") Long campusId,
                                         @Param("teacher") String teacher,
                                         @Param("classId") Long classId,
                                         @Param("courseId") Long courseId,
                                         @Param("room") String room,
                                         @Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("lessonMode") Integer lessonMode);

    ScheduleSummaryVO selectSummary(@Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    @Param("campusId") Long campusId,
                                    @Param("teacher") String teacher,
                                    @Param("classId") Long classId,
                                    @Param("courseId") Long courseId,
                                    @Param("room") String room,
                                    @Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("lessonMode") Integer lessonMode);

    List<ScheduleOptionVO> selectClassOptions();

    List<ScheduleOptionVO> selectCourseOptions();

    List<String> selectTeachers();

    List<String> selectRooms();
}
