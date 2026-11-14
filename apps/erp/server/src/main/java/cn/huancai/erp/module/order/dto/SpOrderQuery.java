package cn.huancai.erp.module.order.dto;

import lombok.Data;

import java.util.List;

/** 校宝订单列表查询条件。 */
@Data
public class SpOrderQuery {

    private Integer pageIndex = 1;
    private Integer pageSize = 50;
    /** 按学员过滤（学员详情页订单列表使用）。 */
    private Long studentId;
    /** 经办日期起止，格式 yyyy-MM-dd，含首尾。 */
    private String dealStart;
    private String dealEnd;
    private List<Integer> statuses;
    private List<Integer> types;
    private Integer sourceType;
    private Integer contractStatus;
    private List<Long> campusIds;
    private List<String> creators;
    private List<String> salesmen;
    private List<String> tags;
    private List<String> courses;
    private List<String> items;
    /** 1=欠费，0=未欠费。 */
    private String isArrearage;
    private String followUpType;
    private String keyword;
    /** studentName / orderNo / content。 */
    private String keywordType;
    /** orderNo / stuName / dealDate。 */
    private String sortField = "orderNo";
    /** asc / desc。 */
    private String sortOrder = "desc";
}
