package cn.huancai.erp.module.student.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StudentListVO {

    private Long id;
    private String name;
    private String gender;
    private String phone;
    private String primaryRelation;
    private String secondaryRelation;
    private String secondaryPhone;
    private String otherRelation;
    private String otherPhone;
    private Integer followStatus;
    private String cardNo;
    private String faceStatus;
    private Integer isStar;
    private String status;
    private Long campusId;
    private String campusText;
    private String campusName;
    private BigDecimal balance;
    private BigDecimal debt;
    /** 欠费折算课时：各报读待补金额 ÷ 课耗单价之和。 */
    private BigDecimal debtLessons;
    private Integer courseCount;
    private Integer points;
    private String channelName;
    private String ageText;
    private String grade;
    private String wechat;
    private LocalDate birthday;
    private String school;
    private String publicSchoolClass;
    private String address;
    private String extraInfo;
    private String source;
    private LocalDate enrollDate;
}
