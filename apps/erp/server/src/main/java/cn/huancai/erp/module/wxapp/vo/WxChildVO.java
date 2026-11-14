package cn.huancai.erp.module.wxapp.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 家长端「我的孩子」卡片。 */
@Data
public class WxChildVO {

    private Long studentId;
    private String name;
    private String gender;
    private String ageText;
    private String grade;
    private String school;
    private String status;
    private String campusName;
    private String relation;
    private BigDecimal remainLessons;
    private Integer courseCount;
    private Integer unreadComments;
}
