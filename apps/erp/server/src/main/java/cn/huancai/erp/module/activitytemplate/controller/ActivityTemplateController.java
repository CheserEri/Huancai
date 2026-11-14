package cn.huancai.erp.module.activitytemplate.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.activitytemplate.entity.ActivityCategory;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplate;
import cn.huancai.erp.module.activitytemplate.entity.ActivityTemplateRule;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityCategoryMapper;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateMapper;
import cn.huancai.erp.module.activitytemplate.mapper.ActivityTemplateRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动模板：节日活动（618/双11）与报课优惠（报10/20节），按分类层级组织。
 * 仅落本地库，不调用校宝任何接口。
 */
@RestController
@RequestMapping("/api/activity-templates")
public class ActivityTemplateController {

    private static final Set<String> TYPES = Set.of("节日活动", "报课优惠");
    private static final Set<String> DISCOUNT_MODES = Set.of("折扣", "立减", "赠送课时");

    private final ActivityCategoryMapper categoryMapper;
    private final ActivityTemplateMapper templateMapper;
    private final ActivityTemplateRuleMapper ruleMapper;

    public ActivityTemplateController(ActivityCategoryMapper categoryMapper,
                                      ActivityTemplateMapper templateMapper,
                                      ActivityTemplateRuleMapper ruleMapper) {
        this.categoryMapper = categoryMapper;
        this.templateMapper = templateMapper;
        this.ruleMapper = ruleMapper;
    }

    public record CategoryForm(String name, Long parentId) {
    }

    public record RuleForm(BigDecimal lessons, String discountMode, BigDecimal discountValue) {
    }

    public record TemplateForm(Long categoryId,
                               String name,
                               String type,
                               String startDate,
                               String endDate,
                               List<RuleForm> rules,
                               String remark) {
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------

    @GetMapping("/categories")
    public ApiResult<List<Map<String, Object>>> categories() {
        List<ActivityCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<ActivityCategory>()
                .orderByAsc(ActivityCategory::getSortNo)
                .orderByAsc(ActivityCategory::getId));
        Map<Long, Long> counts = templateMapper.selectList(null).stream()
                .filter(item -> item.getCategoryId() != null)
                .collect(Collectors.groupingBy(ActivityTemplate::getCategoryId, Collectors.counting()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityCategory category : categories) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.getId());
            row.put("name", category.getName());
            row.put("parentId", category.getParentId());
            row.put("sortNo", category.getSortNo());
            row.put("templateCount", counts.getOrDefault(category.getId(), 0L));
            rows.add(row);
        }
        return ApiResult.ok(rows);
    }

    @PostMapping("/categories")
    public ApiResult<Long> createCategory(@RequestBody CategoryForm form) {
        String error = validateCategory(form, null);
        if (error != null) {
            return ApiResult.fail(error);
        }
        ActivityCategory category = new ActivityCategory();
        category.setName(form.name().trim());
        category.setParentId(form.parentId());
        category.setSortNo(nextCategorySortNo(form.parentId()));
        categoryMapper.insert(category);
        return ApiResult.ok(category.getId());
    }

    @PutMapping("/categories/{id}")
    public ApiResult<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryForm form) {
        ActivityCategory category = categoryMapper.selectById(id);
        if (category == null) {
            return ApiResult.fail("分类不存在");
        }
        String error = validateCategory(form, id);
        if (error != null) {
            return ApiResult.fail(error);
        }
        category.setName(form.name().trim());
        category.setParentId(form.parentId());
        categoryMapper.updateById(category);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResult<Void> deleteCategory(@PathVariable Long id) {
        if (categoryMapper.selectById(id) == null) {
            return ApiResult.fail("分类不存在");
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<ActivityCategory>()
                .eq(ActivityCategory::getParentId, id));
        if (children != null && children > 0) {
            return ApiResult.fail("该分类下存在子分类，请先处理子分类");
        }
        Long templates = templateMapper.selectCount(new LambdaQueryWrapper<ActivityTemplate>()
                .eq(ActivityTemplate::getCategoryId, id));
        if (templates != null && templates > 0) {
            return ApiResult.fail("该分类下存在活动模板，请先处理模板");
        }
        categoryMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    // ------------------------------------------------------------------
    // 活动模板
    // ------------------------------------------------------------------

    @GetMapping("/templates")
    public ApiResult<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Long categoryId) {
        LambdaQueryWrapper<ActivityTemplate> wrapper = new LambdaQueryWrapper<ActivityTemplate>()
                .like(keyword != null && !keyword.isBlank(), ActivityTemplate::getName, keyword)
                .eq(type != null && !type.isBlank(), ActivityTemplate::getType, type)
                .eq(status != null, ActivityTemplate::getStatus, status)
                .orderByDesc(ActivityTemplate::getId);
        if (categoryId != null) {
            wrapper.in(ActivityTemplate::getCategoryId, categoryWithChildren(categoryId));
        }
        List<ActivityTemplate> templates = templateMapper.selectList(wrapper);
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ActivityCategory::getId, ActivityCategory::getName, (a, b) -> a));
        Map<Long, List<ActivityTemplateRule>> rules = ruleMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ActivityTemplateRule::getTemplateId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityTemplate template : templates) {
            rows.add(toRow(template, categoryNames, rules.getOrDefault(template.getId(), List.of())));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("rows", rows);
        return ApiResult.ok(data);
    }

    @PostMapping("/templates")
    public ApiResult<Long> createTemplate(@RequestBody TemplateForm form) {
        String error = validateTemplate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        ActivityTemplate template = new ActivityTemplate();
        applyForm(template, form);
        template.setStatus(1);
        templateMapper.insert(template);
        saveRules(template.getId(), form.type(), form.rules());
        return ApiResult.ok(template.getId());
    }

    @PutMapping("/templates/{id}")
    public ApiResult<Void> updateTemplate(@PathVariable Long id, @RequestBody TemplateForm form) {
        ActivityTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("活动模板不存在");
        }
        String error = validateTemplate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        applyForm(template, form);
        templateMapper.updateById(template);
        ruleMapper.delete(new LambdaQueryWrapper<ActivityTemplateRule>()
                .eq(ActivityTemplateRule::getTemplateId, id));
        saveRules(id, form.type(), form.rules());
        return ApiResult.ok(null);
    }

    @PostMapping("/templates/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        ActivityTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("活动模板不存在");
        }
        template.setStatus(status != null && status == 1 ? 1 : 0);
        templateMapper.updateById(template);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/templates/{id}")
    @Transactional
    public ApiResult<Void> deleteTemplate(@PathVariable Long id) {
        if (templateMapper.selectById(id) == null) {
            return ApiResult.fail("活动模板不存在");
        }
        ruleMapper.delete(new LambdaQueryWrapper<ActivityTemplateRule>()
                .eq(ActivityTemplateRule::getTemplateId, id));
        templateMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private String validateCategory(CategoryForm form, Long selfId) {
        if (form.name() == null || form.name().isBlank()) {
            return "请填写分类名称";
        }
        Long parentId = form.parentId();
        if (parentId != null) {
            if (parentId.equals(selfId)) {
                return "上级分类不能是自己";
            }
            if (categoryMapper.selectById(parentId) == null) {
                return "上级分类不存在";
            }
            if (selfId != null && isDescendant(parentId, selfId)) {
                return "上级分类不能是自己的子分类";
            }
        }
        LambdaQueryWrapper<ActivityCategory> duplicate = new LambdaQueryWrapper<ActivityCategory>()
                .eq(ActivityCategory::getName, form.name().trim());
        if (parentId == null) {
            duplicate.isNull(ActivityCategory::getParentId);
        } else {
            duplicate.eq(ActivityCategory::getParentId, parentId);
        }
        if (selfId != null) {
            duplicate.ne(ActivityCategory::getId, selfId);
        }
        Long count = categoryMapper.selectCount(duplicate);
        if (count != null && count > 0) {
            return "同一层级下已存在同名分类";
        }
        return null;
    }

    /** 判断 candidate 是否为 ancestorId 的子孙分类，避免把父级挂到自己的子级下面形成环。 */
    private boolean isDescendant(Long candidateId, Long ancestorId) {
        Set<Long> visited = new LinkedHashSet<>();
        Long current = candidateId;
        while (current != null && visited.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            ActivityCategory category = categoryMapper.selectById(current);
            current = category == null ? null : category.getParentId();
        }
        return false;
    }

    private Set<Long> categoryWithChildren(Long categoryId) {
        List<ActivityCategory> all = categoryMapper.selectList(null);
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(categoryId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ActivityCategory category : all) {
                if (category.getParentId() != null && ids.contains(category.getParentId()) && ids.add(category.getId())) {
                    changed = true;
                }
            }
        }
        return ids;
    }

    private Integer nextCategorySortNo(Long parentId) {
        LambdaQueryWrapper<ActivityCategory> wrapper = new LambdaQueryWrapper<>();
        if (parentId == null) {
            wrapper.isNull(ActivityCategory::getParentId);
        } else {
            wrapper.eq(ActivityCategory::getParentId, parentId);
        }
        Long count = categoryMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue() + 1;
    }

    private String validateTemplate(TemplateForm form) {
        if (form.name() == null || form.name().isBlank()) {
            return "请填写活动模板名称";
        }
        if (!TYPES.contains(form.type())) {
            return "活动类型只能是节日活动或报课优惠";
        }
        if (form.categoryId() != null && categoryMapper.selectById(form.categoryId()) == null) {
            return "所属分类不存在";
        }
        List<RuleForm> rules = form.rules() == null ? List.of() : form.rules();
        if (rules.isEmpty()) {
            return "请至少设置一条优惠规则";
        }
        if (rules.size() > 6) {
            return "最多设置 6 档优惠";
        }
        for (RuleForm rule : rules) {
            if (!DISCOUNT_MODES.contains(rule.discountMode())) {
                return "优惠方式只能是折扣、立减或赠送课时";
            }
            if (rule.discountValue() == null || rule.discountValue().signum() <= 0) {
                return "优惠值必须大于 0";
            }
            if ("节日活动".equals(form.type())) {
                if (rules.size() != 1) {
                    return "节日活动只能设置一条优惠规则";
                }
            } else if (rule.lessons() == null || rule.lessons().signum() <= 0) {
                return "报课优惠需填写大于 0 的课时门槛";
            }
            if ("折扣".equals(rule.discountMode())
                    && rule.discountValue().compareTo(BigDecimal.TEN) >= 0) {
                return "折扣力度需小于 10 折";
            }
        }
        return null;
    }

    private void applyForm(ActivityTemplate template, TemplateForm form) {
        template.setCategoryId(form.categoryId());
        template.setName(form.name().trim());
        template.setType(form.type());
        template.setStartDate(parseDate(form.startDate()));
        template.setEndDate(parseDate(form.endDate()));
        template.setRemark(form.remark());
    }

    private void saveRules(Long templateId, String type, List<RuleForm> rules) {
        int index = 0;
        for (RuleForm form : rules) {
            ActivityTemplateRule rule = new ActivityTemplateRule();
            rule.setTemplateId(templateId);
            rule.setLessons("节日活动".equals(type) || form.lessons() == null
                    ? BigDecimal.ZERO : form.lessons());
            rule.setDiscountMode(form.discountMode());
            rule.setDiscountValue(form.discountValue());
            rule.setSortNo(index++);
            ruleMapper.insert(rule);
        }
    }

    private Map<String, Object> toRow(ActivityTemplate template,
                                      Map<Long, String> categoryNames,
                                      List<ActivityTemplateRule> rules) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", template.getId());
        row.put("categoryId", template.getCategoryId());
        row.put("categoryName", template.getCategoryId() == null
                ? "未分类" : categoryNames.getOrDefault(template.getCategoryId(), "未分类"));
        row.put("name", template.getName());
        row.put("type", template.getType());
        row.put("startDate", template.getStartDate() == null ? null : template.getStartDate().toString());
        row.put("endDate", template.getEndDate() == null ? null : template.getEndDate().toString());
        row.put("status", template.getStatus());
        row.put("remark", template.getRemark());
        row.put("updatedAt", template.getUpdatedAt());
        List<Map<String, Object>> ruleRows = new ArrayList<>();
        for (ActivityTemplateRule rule : rules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lessons", rule.getLessons());
            item.put("discountMode", rule.getDiscountMode());
            item.put("discountValue", rule.getDiscountValue());
            ruleRows.add(item);
        }
        row.put("rules", ruleRows);
        row.put("discountText", discountText(template.getType(), rules));
        return row;
    }

    private String discountText(String type, List<ActivityTemplateRule> rules) {
        List<String> parts = new ArrayList<>();
        for (ActivityTemplateRule rule : rules) {
            String value = plain(rule.getDiscountValue());
            String text = switch (rule.getDiscountMode()) {
                case "折扣" -> value + "折";
                case "立减" -> "立减" + value + "元";
                default -> "赠" + value + "课时";
            };
            if ("报课优惠".equals(type)) {
                parts.add("报" + plain(rule.getLessons()) + "节"
                        + ("折扣".equals(rule.getDiscountMode()) ? "享" : "") + text);
            } else {
                parts.add(text);
            }
        }
        String result = String.join("；", parts);
        return result.length() > 120 ? result.substring(0, 120) : result;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static java.time.LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        return java.time.LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }
}
