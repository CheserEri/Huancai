package cn.huancai.erp.module.student.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 学员新建 / 编辑表单。字段为 null 表示不修改（编辑时）。
 */
@Data
public class StudentForm {

    private String name;
    private String gender;
    private LocalDate birthday;
    private String phone;
    private String primaryRelation;
    private String secondaryRelation;
    private String secondaryPhone;
    private String otherRelation;
    private String otherPhone;
    private Long campusId;
    private String status;
    private String grade;
    private String school;
    private String publicSchoolClass;
    private String address;
    private String wechat;
    private String source;
    private String channelName;
    private String extraInfo;
    private String remark;
    private LocalDate enrollDate;

    /** 家长姓名（主要联系人） */
    private String guardianName;
}
