package cn.huancai.erp.module.teacherhour.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单节课酬手工调整（如试听课记 0 元），amount 为空表示按规则计算。 */
@Data
@TableName("teacher_hour_adjust")
public class TeacherHourAdjust {

    @TableId(type = IdType.INPUT)
    private Long scheduleId;
    private Long teacherId;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime updatedAt;
}
