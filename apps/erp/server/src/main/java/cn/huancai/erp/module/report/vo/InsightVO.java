package cn.huancai.erp.module.report.vo;

import lombok.Data;

@Data
public class InsightVO {

    private String key;
    private String tag;
    private String title;
    private String detail;
    private String value;
    /** up / down / flat */
    private String trend;
}
