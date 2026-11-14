package cn.huancai.erp.module.consult.mapper;

import cn.huancai.erp.module.consult.vo.ConsultSourceVO;
import cn.huancai.erp.module.consult.vo.ConsultSummaryVO;
import cn.huancai.erp.module.consult.vo.ConsultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface ConsultMapper {

    IPage<ConsultVO> selectConsultPage(IPage<ConsultVO> page,
                                       @Param("campusId") Long campusId,
                                       @Param("keyword") String keyword,
                                       @Param("source") String source,
                                       @Param("status") String status,
                                       @Param("enrolled") Integer enrolled,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    ConsultSummaryVO selectSummary(@Param("campusId") Long campusId,
                                   @Param("keyword") String keyword,
                                   @Param("source") String source,
                                   @Param("status") String status,
                                   @Param("enrolled") Integer enrolled,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    List<ConsultSourceVO> selectSources(@Param("campusId") Long campusId,
                                        @Param("keyword") String keyword,
                                        @Param("source") String source,
                                        @Param("status") String status,
                                        @Param("enrolled") Integer enrolled,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);
}
