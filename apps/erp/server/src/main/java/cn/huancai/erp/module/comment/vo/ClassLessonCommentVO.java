package cn.huancai.erp.module.comment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 课次整体点评，未填写时各字段为空。 */
@Data
public class ClassLessonCommentVO {

    private Long id;
    private Long classId;
    private Long scheduleId;
    private Long teacherId;
    private String rating;
    private String content;
    private LocalDateTime updatedAt;
}
