package cn.huancai.erp.module.material.mapper;

import cn.huancai.erp.module.material.entity.Material;
import cn.huancai.erp.module.material.vo.IncidentalVO;
import cn.huancai.erp.module.material.vo.MaterialChangeSummaryVO;
import cn.huancai.erp.module.material.vo.MaterialSettingVO;
import cn.huancai.erp.module.material.vo.MaterialStockChangeVO;
import cn.huancai.erp.module.material.vo.MaterialStockSummaryVO;
import cn.huancai.erp.module.material.vo.MaterialStockVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MaterialMapper extends BaseMapper<Material> {

    IPage<MaterialStockVO> selectStockPage(IPage<MaterialStockVO> page,
                                           @Param("keyword") String keyword,
                                           @Param("category") String category,
                                           @Param("status") String status,
                                           @Param("lowStock") Boolean lowStock);

    MaterialStockSummaryVO selectStockSummary(@Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("status") String status,
                                              @Param("lowStock") Boolean lowStock);

    IPage<MaterialSettingVO> selectSettingPage(IPage<MaterialSettingVO> page,
                                                @Param("keyword") String keyword,
                                                @Param("category") String category,
                                                @Param("status") String status);

    IPage<IncidentalVO> selectIncidentalPage(IPage<IncidentalVO> page,
                                              @Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("status") String status);

    List<String> selectCategories();

    IPage<MaterialStockChangeVO> selectChangePage(IPage<MaterialStockChangeVO> page,
                                                   @Param("keyword") String keyword,
                                                   @Param("room") String room,
                                                   @Param("bizType") String bizType,
                                                   @Param("materialName") String materialName,
                                                   @Param("start") String start,
                                                   @Param("end") String end);

    MaterialChangeSummaryVO selectChangeSummary(@Param("keyword") String keyword,
                                                 @Param("room") String room,
                                                 @Param("bizType") String bizType,
                                                 @Param("materialName") String materialName,
                                                 @Param("start") String start,
                                                 @Param("end") String end);

    List<String> selectChangeRooms();

    List<String> selectChangeMaterials();
}
