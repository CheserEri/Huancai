package cn.huancai.erp.module.finance.mapper;

import cn.huancai.erp.module.finance.vo.FinanceSummaryVO;
import cn.huancai.erp.module.finance.vo.MonthlyRow;
import cn.huancai.erp.module.finance.vo.PaymentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface FinanceMapper {

    IPage<PaymentVO> selectPaymentPage(IPage<PaymentVO> page,
                                       @Param("campusId") Long campusId,
                                       @Param("keyword") String keyword,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    FinanceSummaryVO selectSummary(@Param("campusId") Long campusId,
                                   @Param("keyword") String keyword,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    List<MonthlyRow> selectMonthly(@Param("campusId") Long campusId,
                                   @Param("keyword") String keyword,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
