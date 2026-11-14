package cn.huancai.erp.module.wxapp.mapper;

import cn.huancai.erp.module.wxapp.vo.WxChildVO;
import cn.huancai.erp.module.wxapp.vo.WxCommentVO;
import cn.huancai.erp.module.wxapp.vo.WxCourseBalanceVO;
import cn.huancai.erp.module.wxapp.vo.WxGradeVO;
import cn.huancai.erp.module.wxapp.vo.WxLessonTxnVO;
import cn.huancai.erp.module.wxapp.vo.WxParentMessageVO;
import cn.huancai.erp.module.wxapp.vo.WxUpcomingLessonVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 家长端只读查询：只返回与家长已绑定学员相关的数据。 */
public interface WxAppContentMapper {

    List<WxChildVO> selectChildren(@Param("parentId") Long parentId);

    WxChildVO selectChild(@Param("parentId") Long parentId, @Param("studentId") Long studentId);

    List<WxCourseBalanceVO> selectCourses(@Param("studentId") Long studentId);

    List<WxLessonTxnVO> selectRecentTxns(@Param("studentId") Long studentId, @Param("limit") int limit);

    List<WxCommentVO> selectComments(@Param("studentId") Long studentId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countComments(@Param("studentId") Long studentId);

    int countUnreadComments(@Param("studentId") Long studentId);

    int markCommentRead(@Param("id") Long id, @Param("studentId") Long studentId);

    List<WxGradeVO> selectGrades(@Param("studentId") Long studentId);

    List<WxUpcomingLessonVO> selectUpcoming(@Param("studentId") Long studentId);

    List<WxParentMessageVO> selectParentMessages(@Param("parentId") Long parentId,
                                                 @Param("studentId") Long studentId);

    int countUnreadMessages(@Param("parentId") Long parentId, @Param("studentId") Long studentId);

    int markMessageRead(@Param("id") Long id, @Param("parentId") Long parentId);
}
