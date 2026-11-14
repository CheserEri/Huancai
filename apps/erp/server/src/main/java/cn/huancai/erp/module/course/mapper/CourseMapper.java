package cn.huancai.erp.module.course.mapper;

import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.vo.CourseCategoryVO;
import cn.huancai.erp.module.course.vo.CourseClassVO;
import cn.huancai.erp.module.course.vo.CoursePriceVO;
import cn.huancai.erp.module.course.vo.CourseSummaryVO;
import cn.huancai.erp.module.course.vo.CourseUpgradeVO;
import cn.huancai.erp.module.course.vo.CourseVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CourseMapper extends BaseMapper<Course> {

    IPage<CourseVO> selectCoursePage(IPage<CourseVO> page,
                                     @Param("campusIds") List<Long> campusIds,
                                     @Param("categories") List<String> categories,
                                     @Param("subjects") List<String> subjects,
                                     @Param("lessonMode") String lessonMode,
                                     @Param("chargeMode") String chargeMode,
                                     @Param("status") Integer status,
                                     @Param("keyword") String keyword,
                                     @Param("sortField") String sortField,
                                     @Param("sortOrder") String sortOrder);

    CourseSummaryVO selectSummary(@Param("campusIds") List<Long> campusIds,
                                  @Param("categories") List<String> categories,
                                  @Param("subjects") List<String> subjects,
                                  @Param("lessonMode") String lessonMode,
                                  @Param("chargeMode") String chargeMode,
                                  @Param("status") Integer status,
                                  @Param("keyword") String keyword);

    List<CourseCategoryVO> selectCategoryRows(@Param("keyword") String keyword);

    List<CourseUpgradeVO> selectUpgrades(@Param("keyword") String keyword);

    List<CoursePriceVO> selectCoursePrices(@Param("courseId") Long courseId);

    List<CourseClassVO> selectCourseClasses(@Param("courseId") Long courseId);
}
