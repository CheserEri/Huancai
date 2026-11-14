package cn.huancai.erp.module.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 员工登录会话，token 为前端持有的登录凭证。 */
@Data
@TableName("staff_session")
public class StaffSession {

    @TableId(type = IdType.INPUT)
    private String token;
    private Long staffId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
