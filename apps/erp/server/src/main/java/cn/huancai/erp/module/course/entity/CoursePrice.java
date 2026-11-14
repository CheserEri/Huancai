package cn.huancai.erp.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course_price")
public class CoursePrice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long campusId;
    private BigDecimal lessons;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
}
