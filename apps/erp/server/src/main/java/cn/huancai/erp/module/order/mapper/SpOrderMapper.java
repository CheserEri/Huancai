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

    /** 旧系统停用时的最新订单号，本地新订单号从它之后继续递增。 */
    String LEGACY_LATEST_ORDER_NO = "33752";

    /**
     * 本地闭环订单号：接续旧系统最新订单号递增，例如 33753、33754。
     *
     * <p>历史导入订单用的是日期型长号（202611160030），只统计纯数字短号，
     * 避免被日期型订单号覆盖。</p>
     */
    @Select("SELECT GREATEST(COALESCE(MAX(CAST(order_no AS UNSIGNED)), 0), " + LEGACY_LATEST_ORDER_NO + ") + 1 "
            + "FROM sp_order WHERE CHAR_LENGTH(order_no) <= 8 AND order_no REGEXP '^[0-9]+'")
    long selectNextLocalOrderNo();
}
