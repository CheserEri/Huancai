package cn.huancai.erp.module.schedule.vo;

import java.util.List;

/** 新增排课时需要的下拉数据。 */
public class ScheduleOptionsVO {

    private List<Option> campuses;
    private List<ClassOption> classes;
    private List<Option> courses;
    private List<Option> teachers;
    private List<String> rooms;

    public List<Option> getCampuses() { return campuses; }
    public void setCampuses(List<Option> campuses) { this.campuses = campuses; }
    public List<ClassOption> getClasses() { return classes; }
    public void setClasses(List<ClassOption> classes) { this.classes = classes; }
    public List<Option> getCourses() { return courses; }
    public void setCourses(List<Option> courses) { this.courses = courses; }
    public List<Option> getTeachers() { return teachers; }
    public void setTeachers(List<Option> teachers) { this.teachers = teachers; }
    public List<String> getRooms() { return rooms; }
    public void setRooms(List<String> rooms) { this.rooms = rooms; }

    public record Option(Long id, String name) {
    }

    public record ClassOption(Long id, String name, Long campusId, Long courseId, Long teacherId,
                              String teacherText, String room, String assistName, String scheduleText) {
    }
}
