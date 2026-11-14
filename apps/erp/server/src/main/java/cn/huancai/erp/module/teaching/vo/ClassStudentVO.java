package cn.huancai.erp.module.teaching.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ClassStudentVO {

    private Long id;
    private Long studentId;
    private String studentName;
    private String phone;
    private String relation;
    private Integer followStatus;
    private String gender;
    private String status;
    private LocalDateTime joinDate;
    private BigDecimal totalLessons;
    private BigDecimal usedLessons;
    private BigDecimal remainLessons;
    private String studentNo;
    private BigDecimal debt;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public Integer getFollowStatus() { return followStatus; }
    public void setFollowStatus(Integer followStatus) { this.followStatus = followStatus; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDateTime joinDate) { this.joinDate = joinDate; }
    public BigDecimal getTotalLessons() { return totalLessons; }
    public void setTotalLessons(BigDecimal totalLessons) { this.totalLessons = totalLessons; }
    public BigDecimal getUsedLessons() { return usedLessons; }
    public void setUsedLessons(BigDecimal usedLessons) { this.usedLessons = usedLessons; }
    public BigDecimal getRemainLessons() { return remainLessons; }
    public void setRemainLessons(BigDecimal remainLessons) { this.remainLessons = remainLessons; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public BigDecimal getDebt() { return debt; }
    public void setDebt(BigDecimal debt) { this.debt = debt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
