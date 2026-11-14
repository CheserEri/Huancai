package cn.huancai.erp.module.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("campus")
public class Campus {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String address;
    private String phone;
    private Integer status;
    private String extId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
