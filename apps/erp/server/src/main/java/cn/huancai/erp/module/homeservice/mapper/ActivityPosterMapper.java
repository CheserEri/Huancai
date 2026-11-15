package cn.huancai.erp.module.homeservice.mapper;

import cn.huancai.erp.module.homeservice.entity.ActivityPoster;
import cn.huancai.erp.module.homeservice.vo.MessageStudentVO;
import cn.huancai.erp.module.homeservice.vo.PosterImageVO;
import cn.huancai.erp.module.homeservice.vo.PosterVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ActivityPosterMapper extends BaseMapper<ActivityPoster> {

    IPage<PosterVO> selectPosterPage(IPage<PosterVO> page,
                                     @Param("keyword") String keyword,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    List<PosterImageVO> selectImages(@Param("posterId") Long posterId);

    void insertPosterTargets(@Param("posterId") Long posterId,
                             @Param("students") List<MessageStudentVO> students);

    void deletePosterTargets(@Param("posterId") Long posterId);

    void deletePosterImages(@Param("posterId") Long posterId);

    void deleteMessageTargets(@Param("messageId") Long messageId);

    void deleteMessageReceivers(@Param("messageId") Long messageId);

    void deleteMessage(@Param("messageId") Long messageId);
}
