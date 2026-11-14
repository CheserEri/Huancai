package cn.huancai.erp.module.grade.service;

import cn.huancai.erp.module.grade.entity.GradeScore;
import cn.huancai.erp.module.grade.mapper.GradeScoreMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 成绩排名计算：按班级、按校区（同考试）分别排名，同分并列。 */
@Component
public class GradeRankService {

    private final GradeScoreMapper scoreMapper;

    public GradeRankService(GradeScoreMapper scoreMapper) {
        this.scoreMapper = scoreMapper;
    }

    public void computeRanks(Long examId) {
        List<GradeScore> scores = scoreMapper.selectList(
                new LambdaQueryWrapper<GradeScore>().eq(GradeScore::getExamId, examId));
        Map<Long, List<GradeScore>> byClass = new LinkedHashMap<>();
        for (GradeScore score : scores) {
            byClass.computeIfAbsent(score.getClassId(), key -> new ArrayList<>()).add(score);
        }
        for (List<GradeScore> list : byClass.values()) {
            applyRank(list, true, false);
        }
        applyRank(new ArrayList<>(scores), false, true);
        for (GradeScore score : scores) {
            scoreMapper.updateById(score);
        }
    }

    private void applyRank(List<GradeScore> scores, boolean classRank, boolean campusRank) {
        List<GradeScore> valid = scores.stream()
                .filter(item -> "正常".equals(item.getStatus()))
                .filter(item -> item.getTotalScore() != null)
                .sorted(Comparator.comparing(GradeScore::getTotalScore).reversed())
                .toList();
        BigDecimal previous = null;
        int rank = 0;
        for (int i = 0; i < valid.size(); i++) {
            GradeScore score = valid.get(i);
            if (previous == null || score.getTotalScore().compareTo(previous) != 0) {
                rank = i + 1;
                previous = score.getTotalScore();
            }
            if (classRank) {
                score.setRankClass(rank);
            }
            if (campusRank) {
                score.setRankCampus(rank);
            }
        }
    }
}
