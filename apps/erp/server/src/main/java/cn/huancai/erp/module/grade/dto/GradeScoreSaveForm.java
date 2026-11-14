package cn.huancai.erp.module.grade.dto;

import lombok.Data;

import java.util.List;

/** 成绩录入提交（草稿或发布）。 */
@Data
public class GradeScoreSaveForm {

    private Long examId;
    private Long classId;
    private List<GradeScoreRow> rows;
}
