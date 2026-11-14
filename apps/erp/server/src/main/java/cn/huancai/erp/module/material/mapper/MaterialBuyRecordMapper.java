package cn.huancai.erp.module.material.mapper;

import cn.huancai.erp.module.material.entity.MaterialBuyRecord;
import cn.huancai.erp.module.material.vo.MaterialBuyRecordVO;
import cn.huancai.erp.module.material.vo.MaterialSummaryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

public interface MaterialBuyRecordMapper extends BaseMapper<MaterialBuyRecord> {

    IPage<MaterialBuyRecordVO> selectBuyRecordPage(IPage<MaterialBuyRecordVO> page,
                                                   @Param("orderNo") String orderNo,
                                                   @Param("tradeType") String tradeType,
                                                   @Param("receiveStatus") String receiveStatus);

    MaterialSummaryVO selectSummary(@Param("orderNo") String orderNo,
                                    @Param("tradeType") String tradeType,
                                    @Param("receiveStatus") String receiveStatus);
}
