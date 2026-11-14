package cn.huancai.erp.module.grade.mapper;

import cn.huancai.erp.module.grade.entity.GradeScore;
import cn.huancai.erp.module.grade.vo.GradeClassExamVO;
import cn.huancai.erp.module.grade.vo.GradeEntryStudentVO;
import cn.huancai.erp.module.grade.vo.GradeQueryVO;
import cn.huancai.erp.module.grade.vo.GradeStudentItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GradeScoreMapper extends BaseMapper<GradeScore> {

    /** 录入网格：班级在读学员（左连接已有成绩，未录过时为「未考」）。 */
    @Select("""
            SELECT cs.student_id AS studentId,
                   st.name       AS studentName,
                   st.student_no AS studentNo,
                   (SELECT MAX(e.id) FROM enrollment e
                     WHERE e.student_id = cs.student_id AND e.class_id = cs.class_id AND e.status = '生效') AS enrollmentId,
                   gs.id           AS scoreId,
                   gs.total_score  AS totalScore,
                   gs.level        AS level,
                   gs.part_scores  AS partScoresJson,
                   COALESCE(gs.status, '未考') AS status,
                   gs.remark       AS remark,
                   gs.rank_class   AS rankClass
            FROM class_student cs
            JOIN student st ON st.id = cs.student_id
            LEFT JOIN grade_score gs
                   ON gs.exam_id = #{examId} AND gs.class_id = #{classId} AND gs.student_id = cs.student_id
            WHERE cs.class_id = #{classId} AND cs.status = '在读'
            ORDER BY st.name
            """)
    List<GradeEntryStudentVO> selectEntryStudents(@Param("examId") Long examId, @Param("classId") Long classId);

    /** 成绩查询：仅已发布考试。 */
    @Select("""
            <script>
            SELECT gs.id,
                   gs.exam_id    AS examId,
                   ge.name       AS examName,
                   ge.type       AS examType,
                   ge.exam_date  AS examDate,
                   ge.full_score AS fullScore,
                   ge.scoring_mode AS scoringMode,
                   gs.class_id   AS classId,
                   cg.name       AS className,
                   gs.student_id AS studentId,
                   st.name       AS studentName,
                   gs.total_score AS totalScore,
                   gs.level      AS level,
                   gs.status     AS status,
                   gs.rank_class AS rankClass,
                   gs.rank_campus AS rankCampus,
                   gs.remark     AS remark
            FROM grade_score gs
            JOIN grade_exam ge ON ge.id = gs.exam_id
            LEFT JOIN class_group cg ON cg.id = gs.class_id
            JOIN student st ON st.id = gs.student_id
            WHERE ge.status = '已发布'
            <if test="examId != null"> AND gs.exam_id = #{examId}</if>
            <if test="classId != null"> AND gs.class_id = #{classId}</if>
            <if test="studentId != null"> AND gs.student_id = #{studentId}</if>
            <if test="type != null and type != ''"> AND ge.type = #{type}</if>
            <if test="scoreStatus != null and scoreStatus != ''"> AND gs.status = #{scoreStatus}</if>
            <if test="keyword != null and keyword != ''">
              AND (st.name LIKE CONCAT('%', #{keyword}, '%') OR ge.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY ge.exam_date DESC, ge.id DESC, cg.name, gs.rank_class
            </script>
            """)
    List<GradeQueryVO> selectQueryRows(@Param("examId") Long examId,
                                       @Param("classId") Long classId,
                                       @Param("studentId") Long studentId,
                                       @Param("type") String type,
                                       @Param("scoreStatus") String scoreStatus,
                                       @Param("keyword") String keyword);

    /** 学员成绩页签：该学员已发布考试的历次成绩。 */
    @Select("""
            SELECT ge.id           AS examId,
                   ge.name         AS examName,
                   ge.type         AS examType,
                   ge.exam_date    AS examDate,
                   ge.full_score   AS fullScore,
                   ge.scoring_mode AS scoringMode,
                   gs.class_id     AS classId,
                   cg.name         AS className,
                   gs.total_score  AS totalScore,
                   gs.level        AS level,
                   gs.part_scores  AS partScoresJson,
                   gs.status       AS status,
                   gs.rank_class   AS rankClass,
                   gs.rank_campus  AS rankCampus,
                   (SELECT AVG(x.total_score) FROM grade_score x
                     WHERE x.exam_id = gs.exam_id AND x.class_id = gs.class_id
                       AND x.status = '正常' AND x.total_score IS NOT NULL) AS classAvgScore,
                   gs.remark       AS remark
            FROM grade_score gs
            JOIN grade_exam ge ON ge.id = gs.exam_id
            LEFT JOIN class_group cg ON cg.id = gs.class_id
            WHERE gs.student_id = #{studentId} AND ge.status = '已发布'
            ORDER BY ge.exam_date DESC, ge.id DESC
            """)
    List<GradeStudentItemVO> selectStudentGrades(@Param("studentId") Long studentId);

    /** 班级成绩页签：该班级的考试列表与整体表现。 */
    @Select("""
            SELECT ge.id           AS examId,
                   ge.name         AS examName,
                   ge.type         AS examType,
                   ge.exam_date    AS examDate,
                   ge.full_score   AS fullScore,
                   ge.scoring_mode AS scoringMode,
                   ge.status       AS status,
                   COUNT(gs.id)    AS enteredCount,
                   (SELECT COUNT(*) FROM class_student cs
                     WHERE cs.class_id = gec.class_id AND cs.status = '在读') AS studentCount,
                   AVG(CASE WHEN gs.status = '正常' THEN gs.total_score END) AS avgScore,
                   MAX(CASE WHEN gs.status = '正常' THEN gs.total_score END) AS maxScore,
                   MIN(CASE WHEN gs.status = '正常' THEN gs.total_score END) AS minScore
            FROM grade_exam_class gec
            JOIN grade_exam ge ON ge.id = gec.exam_id
            LEFT JOIN grade_score gs ON gs.exam_id = ge.id AND gs.class_id = gec.class_id
            WHERE gec.class_id = #{classId}
            GROUP BY ge.id, ge.name, ge.type, ge.exam_date, ge.full_score, ge.scoring_mode, ge.status, gec.class_id
            ORDER BY ge.exam_date DESC, ge.id DESC
            """)
    List<GradeClassExamVO> selectClassExams(@Param("classId") Long classId);
}
