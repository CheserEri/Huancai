package cn.huancai.erp.module.course.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CoursePriceVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private BigDecimal lessons;
    private BigDecimal totalPrice;
}
