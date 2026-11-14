package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 家长端活动 / 福利。 */
@Data
public class WxActivityVO {

    private Long id;
    private String name;
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String remark;
    /** 未开始 / 进行中 / 已结束 / 长期有效 */
    private String phase;
    private List<Rule> rules;

    @Data
    public static class Rule {
        private BigDecimal lessons;
        private String discountMode;
        private BigDecimal discountValue;
    }
}
