package cn.huancai.erp.module.comment.dto;

import lombok.Data;

/** 保存某节课的整体点评。 */
@Data
public class ClassLessonCommentForm {

    private Long classId;
    private Long scheduleId;
    private String rating;
    private String content;
}
