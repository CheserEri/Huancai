package cn.huancai.erp.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学员操作日志：记录敏感修改的操作人、修改内容与时间。 */
@Data
@TableName("student_operation_log")
public class StudentOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long studentId;
    private String type;
    private String content;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
