package cn.huancai.erp.module.schedule.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 重复排课表单：按班级批量生成上课时间。 */
@Data
public class ScheduleBatchForm {

    private Long classId;
    private LocalDate startDate;
    private LocalDate endDate;
    /** week=每周重复 biweek=间隔周重复 altDay=隔天重复 */
    private String repeatType;
    /** 周几上课：1=周一 ... 7=周日 */
    private List<Integer> weekdays;
    /** 节假日不排课（跳过周六、周日） */
    private Boolean skipWeekend;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long teacherId;
    private String assistantName;
    private String room;
}
