package cn.huancai.erp.module.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("class_student")
public class ClassStudent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long classId;
    private Long studentId;
    private String status;
    private LocalDateTime joinDate;
    private BigDecimal totalLessons;
    private BigDecimal usedLessons;
    private BigDecimal remainLessons;
    private String remark;
    private String extId;
    private LocalDateTime createdAt;
}
