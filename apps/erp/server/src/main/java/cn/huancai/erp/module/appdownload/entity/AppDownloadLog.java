package cn.huancai.erp.module.appdownload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** App 安装包下载记录，用于限制每个账号每日的下载次数。 */
@Data
@TableName("app_download_log")
public class AppDownloadLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long staffId;
    private String staffName;
    private String ip;
    /** 下载日期，按自然日统计。 */
    private LocalDate downloadDate;
    private LocalDateTime createdAt;
}
