package cn.huancai.erp.module.comment.vo;

import lombok.Data;

@Data
public class CommentTeacherStatVO {

    private String teacherName;
    private Long comments;
    private Long students;
    private Long lessons;
    private Long readComments;
}
