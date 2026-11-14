package cn.huancai.erp.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private String code;
    private String name;
    private String category;
    private String subject;
    private Integer lessonMinutes;
    private BigDecimal totalLessons;
    private BigDecimal listPrice;
    private Integer status;
    private String remark;
    private String extId;
    private String lessonMode;
    private String campusScope;
    private Integer lessonCharge;
    private Integer timeCharge;
    private Integer periodCharge;
    private BigDecimal materialFee;
    private BigDecimal reportDebt;
    /** 默认基础价格模板，报读时自动带价。 */
    private Long defaultPriceTemplateId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
