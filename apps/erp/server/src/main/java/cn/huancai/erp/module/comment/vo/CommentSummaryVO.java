package cn.huancai.erp.module.comment.vo;

import lombok.Data;

@Data
public class CommentSummaryVO {

    private Long total;
    private Long students;
    private Long classes;
    private Long unread;
    private Long readCount;
}
