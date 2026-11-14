package cn.huancai.erp.module.order.mapper;

import cn.huancai.erp.module.order.dto.SpOrderQuery;
import cn.huancai.erp.module.order.entity.SpOrder;
import cn.huancai.erp.module.order.vo.SpOrderSummaryVO;
import cn.huancai.erp.module.order.vo.SpOrderVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SpOrderMapper extends BaseMapper<SpOrder> {

    long countByQuery(@Param("q") SpOrderQuery query);

    List<SpOrderVO> selectPageByQuery(@Param("q") SpOrderQuery query,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    SpOrderSummaryVO selectSummary(@Param("q") SpOrderQuery query);

    SpOrderVO selectDetail(@Param("id") Long id);

    List<String> selectCreators();

    List<String> selectSalesmen();

    List<String> selectTags();

    @Select("SELECT DISTINCT name FROM course ORDER BY name")
    List<String> selectCourseNames();

    @Select("SELECT DISTINCT name FROM material WHERE status = '正常' ORDER BY name")
    List<String> selectMaterialNames();

    /** 本地闭环订单号：年月日 + 4 位当日流水，例如 202611070001。 */
    @Select("SELECT GREATEST(" +
            "CAST(DATE_FORMAT(NOW(), '%Y%m%d') AS UNSIGNED) * 10000 + 1, " +
            "COALESCE(MAX(CAST(order_no AS UNSIGNED)), 0) + 1" +
            ") FROM sp_order WHERE order_no LIKE CONCAT(DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    long selectNextLocalOrderNo();
}
