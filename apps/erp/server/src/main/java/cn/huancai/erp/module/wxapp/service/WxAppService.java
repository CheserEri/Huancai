package cn.huancai.erp.module.wxapp.service;

import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplate;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplateRule;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateMapper;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateRuleMapper;
import cn.huancai.erp.module.student.entity.Guardian;
import cn.huancai.erp.module.student.entity.Student;
import cn.huancai.erp.module.student.mapper.GuardianMapper;
import cn.huancai.erp.module.student.mapper.StudentMapper;
import cn.huancai.erp.module.wxapp.entity.WxParent;
import cn.huancai.erp.module.wxapp.entity.WxParentSession;
import cn.huancai.erp.module.wxapp.entity.WxParentStudent;
import cn.huancai.erp.module.wxapp.mapper.WxAppContentMapper;
import cn.huancai.erp.module.wxapp.mapper.WxParentMapper;
import cn.huancai.erp.module.wxapp.mapper.WxParentSessionMapper;
import cn.huancai.erp.module.wxapp.mapper.WxParentStudentMapper;
import cn.huancai.erp.module.wxapp.vo.WxActivityVO;
import cn.huancai.erp.module.wxapp.vo.WxChildVO;
import cn.huancai.erp.module.wxapp.vo.WxCommentVO;
import cn.huancai.erp.module.wxapp.vo.WxCourseBalanceVO;
import cn.huancai.erp.module.wxapp.vo.WxGradeVO;
import cn.huancai.erp.module.wxapp.vo.WxLessonTxnVO;
import cn.huancai.erp.module.wxapp.vo.WxUpcomingLessonVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家长端小程序业务：微信登录、手机号绑定学员、课时/成绩/课堂表现/活动查询。
 * 所有查询先校验家长与学员的绑定关系，避免越权读取其他学员数据。
 */
@Service
public class WxAppService {

    private static final int SESSION_DAYS = 30;
    private static final int COMMENT_PAGE_SIZE = 10;

    private final WxParentMapper parentMapper;
    private final WxParentSessionMapper sessionMapper;
    private final WxParentStudentMapper parentStudentMapper;
    private final WxAppContentMapper contentMapper;
    private final StudentMapper studentMapper;
    private final GuardianMapper guardianMapper;
    private final ActivityTemplateMapper activityTemplateMapper;
    private final ActivityTemplateRuleMapper activityRuleMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${wx.miniapp.appid:}")
    private String appid;
    @Value("${wx.miniapp.secret:}")
    private String secret;

    public WxAppService(WxParentMapper parentMapper,
                        WxParentSessionMapper sessionMapper,
                        WxParentStudentMapper parentStudentMapper,
                        WxAppContentMapper contentMapper,
                        StudentMapper studentMapper,
                        GuardianMapper guardianMapper,
                        ActivityTemplateMapper activityTemplateMapper,
                        ActivityTemplateRuleMapper activityRuleMapper,
                        ObjectMapper objectMapper) {
        this.parentMapper = parentMapper;
        this.sessionMapper = sessionMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.contentMapper = contentMapper;
        this.studentMapper = studentMapper;
        this.guardianMapper = guardianMapper;
        this.activityTemplateMapper = activityTemplateMapper;
        this.activityRuleMapper = activityRuleMapper;
        this.objectMapper = objectMapper;
    }

    /** 微信登录：code2session 换 openid；本地未配置 AppID 时使用开发态 openid。 */
    public Map<String, Object> login(String code, String devOpenid, String nickname, String avatarUrl) {
        String openid = resolveOpenid(code, devOpenid);
        WxParent parent = parentMapper.selectOne(new LambdaQueryWrapper<WxParent>()
                .eq(WxParent::getOpenid, openid).last("LIMIT 1"));
        if (parent == null) {
            parent = new WxParent();
            parent.setOpenid(openid);
            parent.setNickname(nickname);
            parent.setAvatarUrl(avatarUrl);
            parent.setCreatedAt(LocalDateTime.now());
            parent.setUpdatedAt(LocalDateTime.now());
            parentMapper.insert(parent);
        } else if ((nickname != null && !nickname.isBlank()) || (avatarUrl != null && !avatarUrl.isBlank())) {
            if (nickname != null && !nickname.isBlank()) {
                parent.setNickname(nickname);
            }
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                parent.setAvatarUrl(avatarUrl);
            }
            parent.setUpdatedAt(LocalDateTime.now());
            parentMapper.updateById(parent);
        }

        sessionMapper.delete(new LambdaQueryWrapper<WxParentSession>()
                .lt(WxParentSession::getExpiresAt, LocalDateTime.now()));
        WxParentSession session = new WxParentSession();
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setParentId(parent.getId());
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessionMapper.insert(session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", session.getToken());
        data.put("parent", parentView(parent));
        data.put("children", contentMapper.selectChildren(parent.getId()));
        return data;
    }

    public Map<String, Object> profile(Long parentId) {
        WxParent parent = parentMapper.selectById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("账号不存在，请重新登录");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parent", parentView(parent));
        data.put("children", contentMapper.selectChildren(parentId));
        return data;
    }

    public Map<String, Object> updateProfile(Long parentId, String nickname, String avatarUrl) {
        WxParent parent = parentMapper.selectById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("账号不存在，请重新登录");
        }
        if (nickname != null && !nickname.isBlank()) {
            parent.setNickname(nickname.trim());
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            parent.setAvatarUrl(avatarUrl.trim());
        }
        parent.setUpdatedAt(LocalDateTime.now());
        parentMapper.updateById(parent);
        return profile(parentId);
    }

    /** 家长用「学员姓名 + 报名手机号」绑定孩子，手机号必须与学员或监护人档案一致。 */
    public Map<String, Object> bind(Long parentId, String name, String phone) {
        String studentName = name == null ? "" : name.trim();
        String mobile = phone == null ? "" : phone.trim();
        if (studentName.isEmpty() || mobile.isEmpty()) {
            throw new IllegalArgumentException("请填写学员姓名和报名手机号");
        }
        Map<Long, Student> matched = new LinkedHashMap<>();
        for (Guardian guardian : guardianMapper.selectList(new LambdaQueryWrapper<Guardian>()
                .eq(Guardian::getPhone, mobile))) {
            Student student = studentMapper.selectById(guardian.getStudentId());
            if (student != null && studentName.equals(student.getName())) {
                matched.put(student.getId(), student);
            }
        }
        for (Student student : studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getPhone, mobile))) {
            if (studentName.equals(student.getName())) {
                matched.put(student.getId(), student);
            }
        }
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("未找到匹配的学员，请核对姓名与报名手机号");
        }
        for (Long studentId : matched.keySet()) {
            Long exists = parentStudentMapper.selectCount(new LambdaQueryWrapper<WxParentStudent>()
                    .eq(WxParentStudent::getParentId, parentId)
                    .eq(WxParentStudent::getStudentId, studentId));
            if (exists != null && exists > 0) {
                continue;
            }
            WxParentStudent relation = new WxParentStudent();
            relation.setParentId(parentId);
            relation.setStudentId(studentId);
            Student student = matched.get(studentId);
            relation.setRelation(student.getPrimaryRelation());
            relation.setCreatedAt(LocalDateTime.now());
            parentStudentMapper.insert(relation);
        }
        WxParent parent = parentMapper.selectById(parentId);
        if (parent != null && (parent.getPhone() == null || parent.getPhone().isBlank())) {
            parent.setPhone(mobile);
            parent.setUpdatedAt(LocalDateTime.now());
            parentMapper.updateById(parent);
        }
        return profile(parentId);
    }

    public Map<String, Object> unbind(Long parentId, Long studentId) {
        parentStudentMapper.delete(new LambdaQueryWrapper<WxParentStudent>()
                .eq(WxParentStudent::getParentId, parentId)
                .eq(WxParentStudent::getStudentId, studentId));
        return profile(parentId);
    }

    public List<WxChildVO> children(Long parentId) {
        return contentMapper.selectChildren(parentId);
    }

    /** 首页聚合：课时、最近点评、最近成绩、待上课、活动。 */
    public Map<String, Object> home(Long parentId, Long studentId) {
        WxChildVO child = requireChild(parentId, studentId);
        List<WxCourseBalanceVO> courses = contentMapper.selectCourses(studentId);
        BigDecimal remain = courses.stream()
                .map(WxCourseBalanceVO::getRemainLessons)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<WxCommentVO> comments = contentMapper.selectComments(studentId, 0, 1);
        List<WxGradeVO> grades = decorateGrades(contentMapper.selectGrades(studentId));
        List<WxUpcomingLessonVO> upcoming = contentMapper.selectUpcoming(studentId);
        List<WxActivityVO> activities = activities();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("child", child);
        data.put("totalRemainLessons", remain);
        data.put("courses", courses);
        data.put("recentTxns", contentMapper.selectRecentTxns(studentId, 5));
        data.put("latestComment", comments.isEmpty() ? null : comments.get(0));
        data.put("latestGrade", grades.isEmpty() ? null : grades.get(0));
        data.put("gradeCount", grades.size());
        data.put("upcoming", upcoming);
        data.put("activities", activities.size() > 3 ? activities.subList(0, 3) : activities);
        data.put("unreadComments", contentMapper.countUnreadComments(studentId));
        return data;
    }

    public Map<String, Object> lessons(Long parentId, Long studentId) {
        requireChild(parentId, studentId);
        List<WxCourseBalanceVO> courses = contentMapper.selectCourses(studentId);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal remain = BigDecimal.ZERO;
        for (WxCourseBalanceVO course : courses) {
            total = add(total, course.getTotalLessons(), course.getGiftLessons());
            used = add(used, course.getUsedLessons());
            remain = add(remain, course.getRemainLessons());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("courses", courses.size());
        summary.put("totalLessons", total);
        summary.put("usedLessons", used);
        summary.put("remainLessons", remain);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("courses", courses);
        data.put("recentTxns", contentMapper.selectRecentTxns(studentId, 20));
        data.put("upcoming", contentMapper.selectUpcoming(studentId));
        return data;
    }

    public Map<String, Object> comments(Long parentId, Long studentId, int page) {
        requireChild(parentId, studentId);
        int current = Math.max(page, 1);
        List<WxCommentVO> list = contentMapper.selectComments(studentId,
                (current - 1) * COMMENT_PAGE_SIZE, COMMENT_PAGE_SIZE);
        long total = contentMapper.countComments(studentId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", current);
        data.put("size", COMMENT_PAGE_SIZE);
        data.put("hasMore", (long) current * COMMENT_PAGE_SIZE < total);
        data.put("unread", contentMapper.countUnreadComments(studentId));
        return data;
    }

    public void markCommentRead(Long parentId, Long studentId, Long commentId) {
        requireChild(parentId, studentId);
        contentMapper.markCommentRead(commentId, studentId);
    }

    public List<WxGradeVO> grades(Long parentId, Long studentId) {
        requireChild(parentId, studentId);
        return decorateGrades(contentMapper.selectGrades(studentId));
    }

    /** 启用的活动模板，按进行中 > 未开始 > 已结束 排序，附带优惠规则。 */
    public List<WxActivityVO> activities() {
        List<ActivityTemplate> templates = activityTemplateMapper.selectList(
                new LambdaQueryWrapper<ActivityTemplate>()
                        .eq(ActivityTemplate::getStatus, 1)
                        .orderByDesc(ActivityTemplate::getId));
        List<WxActivityVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (ActivityTemplate template : templates) {
            WxActivityVO vo = new WxActivityVO();
            vo.setId(template.getId());
            vo.setName(template.getName());
            vo.setType(template.getType());
            vo.setStartDate(template.getStartDate());
            vo.setEndDate(template.getEndDate());
            vo.setStatus(template.getStatus());
            vo.setRemark(template.getRemark());
            vo.setPhase(phase(template, today));
            List<ActivityTemplateRule> rules = activityRuleMapper.selectList(
                    new LambdaQueryWrapper<ActivityTemplateRule>()
                            .eq(ActivityTemplateRule::getTemplateId, template.getId())
                            .orderByAsc(ActivityTemplateRule::getSortNo)
                            .orderByAsc(ActivityTemplateRule::getId));
            List<WxActivityVO.Rule> ruleVOs = new ArrayList<>();
            for (ActivityTemplateRule rule : rules) {
                WxActivityVO.Rule item = new WxActivityVO.Rule();
                item.setLessons(rule.getLessons());
                item.setDiscountMode(rule.getDiscountMode());
                item.setDiscountValue(rule.getDiscountValue());
                ruleVOs.add(item);
            }
            vo.setRules(ruleVOs);
            result.add(vo);
        }
        result.sort((a, b) -> Integer.compare(order(a.getPhase()), order(b.getPhase())));
        return result;
    }

    private int order(String phase) {
        return switch (phase) {
            case "进行中" -> 0;
            case "未开始" -> 1;
            case "长期有效" -> 2;
            default -> 3;
        };
    }

    private String phase(ActivityTemplate template, LocalDate today) {
        LocalDate start = template.getStartDate();
        LocalDate end = template.getEndDate();
        if (start == null && end == null) {
            return "长期有效";
        }
        if (start != null && today.isBefore(start)) {
            return "未开始";
        }
        if (end != null && today.isAfter(end)) {
            return "已结束";
        }
        return "进行中";
    }

    private List<WxGradeVO> decorateGrades(List<WxGradeVO> grades) {
        for (int i = 0; i < grades.size(); i++) {
            WxGradeVO item = grades.get(i);
            item.setPartScores(parseParts(item.getPartScoresJson()));
            WxGradeVO previous = null;
            for (int j = i + 1; j < grades.size(); j++) {
                WxGradeVO candidate = grades.get(j);
                boolean sameCourse = item.getCourseName() == null
                        ? candidate.getCourseName() == null
                        : item.getCourseName().equals(candidate.getCourseName());
                if (sameCourse) {
                    previous = candidate;
                    break;
                }
            }
            if (previous != null && item.getTotalScore() != null && previous.getTotalScore() != null) {
                item.setDeltaScore(item.getTotalScore().subtract(previous.getTotalScore()));
            }
        }
        return grades;
    }

    private Map<String, BigDecimal> parseParts(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, BigDecimal>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private WxChildVO requireChild(Long parentId, Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("请先选择孩子");
        }
        WxChildVO child = contentMapper.selectChild(parentId, studentId);
        if (child == null) {
            throw new IllegalArgumentException("未绑定的学员，无法查看");
        }
        return child;
    }

    private BigDecimal add(BigDecimal base, BigDecimal... values) {
        BigDecimal result = base == null ? BigDecimal.ZERO : base;
        for (BigDecimal value : values) {
            if (value != null) {
                result = result.add(value);
            }
        }
        return result;
    }

    private Map<String, Object> parentView(WxParent parent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", parent.getId());
        data.put("nickname", parent.getNickname());
        data.put("avatarUrl", parent.getAvatarUrl());
        data.put("phone", maskPhone(parent.getPhone()));
        return data;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String resolveOpenid(String code, String devOpenid) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            // 本地开发：开发者工具每次 wx.login 返回的 code 都不同，用持久化的 devOpenid 保持账号稳定
            String dev = devOpenid == null || devOpenid.isBlank() ? "dev-local" : devOpenid.trim();
            return "dev:" + dev;
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("缺少微信登录凭证");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}"
                                    + "&js_code={code}&grant_type=authorization_code",
                            appid, secret, code)
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("openid") == null) {
                String message = body == null ? "微信登录失败" : String.valueOf(body.get("errmsg"));
                throw new IllegalArgumentException("微信登录失败：" + message);
            }
            return String.valueOf(body.get("openid"));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("微信登录服务暂不可用，请稍后重试");
        }
    }
}
