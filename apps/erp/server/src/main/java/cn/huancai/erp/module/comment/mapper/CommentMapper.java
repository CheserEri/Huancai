package cn.huancai.erp.module.comment.mapper;

import cn.huancai.erp.module.comment.entity.ClassComment;
import cn.huancai.erp.module.comment.vo.CommentLessonSummaryVO;
import cn.huancai.erp.module.comment.vo.CommentLessonVO;
import cn.huancai.erp.module.comment.vo.ClassCommentRowVO;
import cn.huancai.erp.module.comment.vo.CommentRosterVO;
import cn.huancai.erp.module.comment.vo.CommentSummaryVO;
import cn.huancai.erp.module.comment.vo.CommentTeacherStatVO;
import cn.huancai.erp.module.comment.vo.CommentVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CommentMapper extends BaseMapper<ClassComment> {

    IPage<CommentVO> selectCommentPage(IPage<CommentVO> page,
                                       @Param("campusId") Long campusId,
                                       @Param("keyword") String keyword,
                                       @Param("classId") Long classId,
                                       @Param("courseId") Long courseId,
                                       @Param("teacher") String teacher,
                                       @Param("rating") String rating,
                                       @Param("readStatus") String readStatus,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    CommentSummaryVO selectSummary(@Param("campusId") Long campusId,
                                   @Param("keyword") String keyword,
                                   @Param("classId") Long classId,
                                   @Param("courseId") Long courseId,
                                   @Param("teacher") String teacher,
                                   @Param("rating") String rating,
                                   @Param("readStatus") String readStatus,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    IPage<CommentLessonVO> selectLessonPage(IPage<CommentLessonVO> page,
                                            @Param("campusId") Long campusId,
                                            @Param("keyword") String keyword,
                                            @Param("classId") Long classId,
                                            @Param("courseId") Long courseId,
                                            @Param("teacher") String teacher,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("commented") String commented);

    CommentLessonSummaryVO selectLessonSummary(@Param("campusId") Long campusId,
                                               @Param("keyword") String keyword,
                                               @Param("classId") Long classId,
                                               @Param("courseId") Long courseId,
                                               @Param("teacher") String teacher,
                                               @Param("start") LocalDate start,
                                               @Param("end") LocalDate end,
                                               @Param("commented") String commented);

    List<CommentTeacherStatVO> selectTeacherStats(@Param("campusId") Long campusId,
                                                  @Param("keyword") String keyword,
                                                  @Param("classId") Long classId,
                                                  @Param("courseId") Long courseId,
                                                  @Param("teacher") String teacher,
                                                  @Param("rating") String rating,
                                                  @Param("readStatus") String readStatus,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);

    List<String> selectTeachers();

    List<ClassCommentRowVO> selectClassCommentRows(@Param("classId") Long classId,
                                                   @Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);

    List<CommentRosterVO> selectRoster(@Param("classId") Long classId,
                                       @Param("scheduleId") Long scheduleId);
}
