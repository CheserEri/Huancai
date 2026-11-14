package cn.huancai.erp.module.student.dto;

import lombok.Data;

@Data
public class StudentQuery {

    private long page = 1;
    private long size = 20;
    private String keyword;
    private Long campusId;
    private String status;
    private Integer isStar;
    private Integer followStatus;
    private String grade;
    private String gender;
    private Integer cardBound;
    private Integer faceEntered;
    private Integer hasDebt;
    private String studentType;
    private String channel;
    private String sort;
}
