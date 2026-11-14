package cn.huancai.erp.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("guardian")
public class Guardian {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String name;
    private String relation;
    private String phone;
    private Integer isPrimary;
    private String extId;
    private LocalDateTime createdAt;
}
