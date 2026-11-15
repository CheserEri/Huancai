package cn.huancai.erp.module.appdownload.dto;

/** App 安装包下载信息：当日额度与安装包元数据。 */
public record AppDownloadInfoVO(
        int limit,
        int used,
        int remaining,
        String version,
        String fileName,
        long sizeBytes,
        String updatedAt) {
}
