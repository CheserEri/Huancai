package cn.huancai.erp.module.teaching.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ClassVO {

    private Long id;
    private Long campusId;
    private String campusName;
    private Long courseId;
    private String courseName;
    private String courseCategory;
    private Long teacherId;
    private String teacherName;
    private String assistName;
    private String name;
    private String lessonClassName;
    private String room;
    private String scheduleText;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer capacity;
    private Integer enrolledCount;
    private BigDecimal stuRate;
    private String upRate;
    private Integer scheduleCount;
    private Integer enrolling;
    private String createBy;
    private LocalDateTime createdAt;
    private String feeStandard;
    private String source;
    private Long spClassId;
    private Long studentCount;
    private Long lessonCount;
    private LocalDate lastLessonDate;
    private BigDecimal attendanceRate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampusId() { return campusId; }
    public void setCampusId(Long campusId) { this.campusId = campusId; }
    public String getCampusName() { return campusName; }
    public void setCampusName(String campusName) { this.campusName = campusName; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getCourseCategory() { return courseCategory; }
    public void setCourseCategory(String courseCategory) { this.courseCategory = courseCategory; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public String getAssistName() { return assistName; }
    public void setAssistName(String assistName) { this.assistName = assistName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLessonClassName() { return lessonClassName; }
    public void setLessonClassName(String lessonClassName) { this.lessonClassName = lessonClassName; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public String getScheduleText() { return scheduleText; }
    public void setScheduleText(String scheduleText) { this.scheduleText = scheduleText; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getEnrolledCount() { return enrolledCount; }
    public void setEnrolledCount(Integer enrolledCount) { this.enrolledCount = enrolledCount; }
    public BigDecimal getStuRate() { return stuRate; }
    public void setStuRate(BigDecimal stuRate) { this.stuRate = stuRate; }
    public String getUpRate() { return upRate; }
    public void setUpRate(String upRate) { this.upRate = upRate; }
    public Integer getScheduleCount() { return scheduleCount; }
    public void setScheduleCount(Integer scheduleCount) { this.scheduleCount = scheduleCount; }
    public Integer getEnrolling() { return enrolling; }
    public void setEnrolling(Integer enrolling) { this.enrolling = enrolling; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getFeeStandard() { return feeStandard; }
    public void setFeeStandard(String feeStandard) { this.feeStandard = feeStandard; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getSpClassId() { return spClassId; }
    public void setSpClassId(Long spClassId) { this.spClassId = spClassId; }
    public Long getStudentCount() { return studentCount; }
    public void setStudentCount(Long studentCount) { this.studentCount = studentCount; }
    public Long getLessonCount() { return lessonCount; }
    public void setLessonCount(Long lessonCount) { this.lessonCount = lessonCount; }
    public LocalDate getLastLessonDate() { return lastLessonDate; }
    public void setLastLessonDate(LocalDate lastLessonDate) { this.lastLessonDate = lastLessonDate; }
    public BigDecimal getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(BigDecimal attendanceRate) { this.attendanceRate = attendanceRate; }
}
