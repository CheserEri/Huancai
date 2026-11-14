package cn.huancai.erp.module.pricetemplate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 价格模板与课程的绑定关系。 */
@Data
@TableName("price_template_course")
public class PriceTemplateCourse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long courseId;
    private LocalDateTime createdAt;
}
