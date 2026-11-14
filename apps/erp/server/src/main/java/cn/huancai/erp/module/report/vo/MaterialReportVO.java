package cn.huancai.erp.module.report.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MaterialReportVO {

    private Long count;
    private BigDecimal receivable;
    private BigDecimal received;
    private List<MaterialRowVO> rows;
}
