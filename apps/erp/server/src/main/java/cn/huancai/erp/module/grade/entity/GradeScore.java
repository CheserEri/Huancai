package cn.huancai.erp.module.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成绩明细：partScores 为分项成绩 JSON 字符串（{"分项id":"得分"}）。 */
@Data
@TableName("grade_score")
public class GradeScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long classId;
    private Long studentId;
    private Long enrollmentId;
    private BigDecimal totalScore;
    private String level;
    private String partScores;
    private String status;
    private Integer rankClass;
    private Integer rankCampus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
