package cn.huancai.erp.module.comment.vo;

import lombok.Data;

/** 某课次的点评录入行（学员 + 已有点评）。 */
@Data
public class CommentRosterVO {

    private Long studentId;
    private String studentName;
    private Long commentId;
    private String rating;
    private String content;
}
