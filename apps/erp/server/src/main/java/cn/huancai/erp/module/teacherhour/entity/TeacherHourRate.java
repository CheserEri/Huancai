package cn.huancai.erp.module.teacherhour.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 教师课时费规则：某位教师在一段适用年级区间内的课酬标准。 */
@Data
@TableName("teacher_hour_rate")
public class TeacherHourRate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String teacherName;
    /** 适用年级起始（1=一年级，9=九年级） */
    private Integer gradeStart;
    /** 适用年级结束（含） */
    private Integer gradeEnd;
    /** 人数单价（元/人） */
    private BigDecimal unitPrice;
    /** 人数额度：未达到按人数计费，达到后按固定金额 */
    private Integer thresholdStudents;
    /** 达到人数额度后的单节固定金额 */
    private BigDecimal fixedAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
