package cn.huancai.erp.module.teaching.vo;

import java.util.List;

/** 新建 / 编辑班级时需要的下拉数据。 */
public class ClassOptionsVO {

    private List<ClassNameOptionVO> campuses;
    private List<ClassNameOptionVO> courses;
    private List<ClassNameOptionVO> teachers;
    private List<String> rooms;
    private List<String> lessonClasses;

    public List<ClassNameOptionVO> getCampuses() { return campuses; }
    public void setCampuses(List<ClassNameOptionVO> campuses) { this.campuses = campuses; }
    public List<ClassNameOptionVO> getCourses() { return courses; }
    public void setCourses(List<ClassNameOptionVO> courses) { this.courses = courses; }
    public List<ClassNameOptionVO> getTeachers() { return teachers; }
    public void setTeachers(List<ClassNameOptionVO> teachers) { this.teachers = teachers; }
    public List<String> getRooms() { return rooms; }
    public void setRooms(List<String> rooms) { this.rooms = rooms; }
    public List<String> getLessonClasses() { return lessonClasses; }
    public void setLessonClasses(List<String> lessonClasses) { this.lessonClasses = lessonClasses; }
}
