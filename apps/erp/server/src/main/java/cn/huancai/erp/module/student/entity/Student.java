package cn.huancai.erp.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private String campusText;
    private String extId;
    private String studentNo;
    private String name;
    private String gender;
    private LocalDate birthday;
    private String ageText;
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
    private java.math.BigDecimal balance;
    private java.math.BigDecimal debt;
    private Integer points;
    private Integer courseCount;
    private String status;
    private String source;
    private String channelName;
    private String school;
    private String publicSchoolClass;
    private String grade;
    private String wechat;
    private String address;
    private String extraInfo;
    private LocalDate enrollDate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
