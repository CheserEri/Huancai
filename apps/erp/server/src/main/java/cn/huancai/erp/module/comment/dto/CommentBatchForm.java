package cn.huancai.erp.module.comment.dto;

import lombok.Data;

import java.util.List;

/** 按课次批量保存课堂点评。 */
@Data
public class CommentBatchForm {

    private Long classId;
    private Long scheduleId;
    private List<Item> items;

    @Data
    public static class Item {
        private Long studentId;
        private String rating;
        private String content;
    }
}
