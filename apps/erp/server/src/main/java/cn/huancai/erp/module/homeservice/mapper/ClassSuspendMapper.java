package cn.huancai.erp.module.homeservice.mapper;

import cn.huancai.erp.module.homeservice.entity.ClassSuspend;
import cn.huancai.erp.module.homeservice.vo.SuspendCandidateVO;
import cn.huancai.erp.module.homeservice.vo.SuspendVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ClassSuspendMapper extends BaseMapper<ClassSuspend> {

    List<SuspendCandidateVO> selectCandidates(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end,
                                              @Param("classIds") List<Long> classIds,
                                              @Param("keyword") String keyword);

    IPage<SuspendVO> selectSuspendPage(IPage<SuspendVO> page,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("classId") Long classId,
                                       @Param("status") String status);
}
