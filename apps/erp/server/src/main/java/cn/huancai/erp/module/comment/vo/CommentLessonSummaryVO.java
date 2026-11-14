package cn.huancai.erp.module.comment.vo;

import lombok.Data;

@Data
public class CommentLessonSummaryVO {

    private Long lessons;
    private Long commentedLessons;
    private Long comments;
    private Long readComments;
    private Long shouldStudents;
    private Long actualStudents;
}
