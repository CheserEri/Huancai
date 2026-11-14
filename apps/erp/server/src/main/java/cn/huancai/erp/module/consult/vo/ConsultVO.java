package cn.huancai.erp.module.consult.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ConsultVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private String name;
    private String phone;
    private String childName;
    private String sex;
    private String parentName;
    private String motherTel;
    private String fatherTel;
    private String otherTel;
    private String source;
    private String channel;
    private String scene;
    private String method;
    private String salesway;
    private String salesmanName;
    private String status;
    private String followStateText;
    private Integer enrolled;
    private String interest;
    private String intentionCourse;
    private String schoolName;
    private String grade;
    private String pubSchool;
    private String homeAddress;
    private LocalDate birthday;
    private BigDecimal arrearage;
    private BigDecimal remainLessons;
    private String remark;
    private String content;
    private LocalDateTime nextFollowAt;
    private LocalDateTime lastFollowAt;
    private LocalDateTime createdAt;
}
