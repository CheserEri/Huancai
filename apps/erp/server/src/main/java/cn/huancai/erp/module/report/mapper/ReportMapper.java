package cn.huancai.erp.module.report.mapper;

import cn.huancai.erp.module.report.vo.BusinessReportVO;
import cn.huancai.erp.module.report.vo.CampusCompareVO;
import cn.huancai.erp.module.report.vo.CourseConsumeVO;
import cn.huancai.erp.module.report.vo.DailyFinanceVO;
import cn.huancai.erp.module.report.vo.FinanceReportVO;
import cn.huancai.erp.module.report.vo.MaterialRowVO;
import cn.huancai.erp.module.report.vo.ScreenReportVO;
import cn.huancai.erp.module.report.vo.TeacherWorkloadVO;
import cn.huancai.erp.module.report.vo.TeachingReportVO;
import cn.huancai.erp.module.report.vo.TopCourseVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReportMapper {

    BusinessReportVO selectBusiness(@Param("campusId") Long campusId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

    List<CampusCompareVO> selectCampusCompare(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    TeachingReportVO selectTeaching(@Param("campusId") Long campusId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

    List<TeacherWorkloadVO> selectTeacherWorkload(@Param("campusId") Long campusId,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    List<CourseConsumeVO> selectCourseConsume(@Param("campusId") Long campusId,
                                              @Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    FinanceReportVO selectFinance(@Param("campusId") Long campusId,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    List<DailyFinanceVO> selectDailyFinance(@Param("campusId") Long campusId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end);

    List<MaterialRowVO> selectMaterialRows(@Param("campusId") Long campusId,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    ScreenReportVO selectScreen(@Param("campusId") Long campusId);

    List<TopCourseVO> selectTopCourses(@Param("campusId") Long campusId);
}
