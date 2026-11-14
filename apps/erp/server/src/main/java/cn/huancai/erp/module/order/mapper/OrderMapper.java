package cn.huancai.erp.module.order.mapper;

import cn.huancai.erp.module.order.entity.SalesOrder;
import cn.huancai.erp.module.order.vo.OrderSummaryVO;
import cn.huancai.erp.module.order.vo.OrderVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface OrderMapper extends BaseMapper<SalesOrder> {

    IPage<OrderVO> selectOrderPage(IPage<OrderVO> page,
                                   @Param("keyword") String keyword,
                                   @Param("campusId") Long campusId,
                                   @Param("type") String type,
                                   @Param("status") String status,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    OrderSummaryVO selectSummary(@Param("keyword") String keyword,
                                 @Param("campusId") Long campusId,
                                 @Param("type") String type,
                                 @Param("status") String status,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    /** 现有纯数字订单号的最大值，用于生成与校宝同格式的新订单号。 */
    @Select("SELECT COALESCE(MAX(CAST(order_no AS UNSIGNED)), 0) FROM sales_order WHERE order_no REGEXP '^[0-9]+$'")
    Long selectMaxNumericOrderNo();
}
