package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 分项模板：parts 为分项配置 JSON 数组。 */
@Data
@TableName("grade_part_template")
public class GradePartTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String courseCategory;
    private String parts;
    private LocalDateTime createdAt;
}
