package cn.huancai.erp.module.consult.vo;

import lombok.Data;

import java.util.List;

@Data
public class ConsultSummaryVO {

    private Long total;
    private Long enrolled;
    private Long following;
    private Long thisMonth;
    private Long phoneCount;
    private List<ConsultSourceVO> sources;
}
