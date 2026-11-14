package cn.huancai.erp.module.grade.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.grade.dto.GradePartForm;
import cn.huancai.erp.module.grade.dto.GradePartTemplateForm;
import cn.huancai.erp.module.grade.dto.GradeRuleForm;
import cn.huancai.erp.module.grade.entity.GradePartTemplate;
import cn.huancai.erp.module.grade.entity.GradeRule;
import cn.huancai.erp.module.grade.mapper.GradePartTemplateMapper;
import cn.huancai.erp.module.grade.mapper.GradeRuleMapper;
import cn.huancai.erp.module.grade.vo.GradePartTemplateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 成绩设置：等级规则与分项模板。 */
@RestController
@RequestMapping("/api/grades")
public class GradeSettingController {

    private final GradeRuleMapper ruleMapper;
    private final GradePartTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    public GradeSettingController(GradeRuleMapper ruleMapper,
                                  GradePartTemplateMapper templateMapper,
                                  ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/rules")
    public ApiResult<List<GradeRule>> rules() {
        return ApiResult.ok(ruleMapper.selectList(new LambdaQueryWrapper<GradeRule>()
                .orderByAsc(GradeRule::getSort).orderByAsc(GradeRule::getMinScore)));
    }

    @PostMapping("/rules")
    public ApiResult<GradeRule> createRule(@RequestBody GradeRuleForm form) {
        String error = validateRule(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        GradeRule rule = new GradeRule();
        applyRule(rule, form);
        rule.setCreatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        return ApiResult.ok(rule);
    }

    @PutMapping("/rules/{id}")
    public ApiResult<GradeRule> updateRule(@PathVariable Long id, @RequestBody GradeRuleForm form) {
        GradeRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            return ApiResult.fail("等级规则不存在");
        }
        String error = validateRule(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        applyRule(rule, form);
        ruleMapper.updateById(rule);
        return ApiResult.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    public ApiResult<Void> deleteRule(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/part-templates")
    public ApiResult<List<GradePartTemplateVO>> templates() {
        List<GradePartTemplateVO> list = new ArrayList<>();
        for (GradePartTemplate template : templateMapper.selectList(
                new LambdaQueryWrapper<GradePartTemplate>().orderByAsc(GradePartTemplate::getId))) {
            list.add(toVO(template));
        }
        return ApiResult.ok(list);
    }

    @PostMapping("/part-templates")
    @Transactional
    public ApiResult<GradePartTemplateVO> createTemplate(@RequestBody GradePartTemplateForm form) {
        String error = validateTemplate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        GradePartTemplate template = new GradePartTemplate();
        applyTemplate(template, form);
        template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        return ApiResult.ok(toVO(template));
    }

    @PutMapping("/part-templates/{id}")
    @Transactional
    public ApiResult<GradePartTemplateVO> updateTemplate(@PathVariable Long id,
                                                         @RequestBody GradePartTemplateForm form) {
        GradePartTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("分项模板不存在");
        }
        String error = validateTemplate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        applyTemplate(template, form);
        templateMapper.updateById(template);
        return ApiResult.ok(toVO(template));
    }

    @DeleteMapping("/part-templates/{id}")
    public ApiResult<Void> deleteTemplate(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    private String validateRule(GradeRuleForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "请填写等级名称";
        }
        if (form.getMinScore() == null || form.getMaxScore() == null) {
            return "请填写分数区间";
        }
        if (form.getMinScore().compareTo(form.getMaxScore()) > 0) {
            return "最低分不能大于最高分";
        }
        return null;
    }

    private void applyRule(GradeRule rule, GradeRuleForm form) {
        rule.setName(form.getName().trim());
        rule.setMinScore(form.getMinScore());
        rule.setMaxScore(form.getMaxScore());
        rule.setSort(form.getSort() == null ? 0 : form.getSort());
    }

    private String validateTemplate(GradePartTemplateForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return "请填写模板名称";
        }
        if (form.getParts() == null || form.getParts().stream().allMatch(part -> part.getName() == null || part.getName().isBlank())) {
            return "请至少配置一个分项";
        }
        return null;
    }

    private void applyTemplate(GradePartTemplate template, GradePartTemplateForm form) {
        template.setName(form.getName().trim());
        template.setCourseCategory(form.getCourseCategory() == null || form.getCourseCategory().isBlank()
                ? null : form.getCourseCategory().trim());
        try {
            template.setParts(objectMapper.writeValueAsString(form.getParts()));
        } catch (Exception ex) {
            template.setParts("[]");
        }
    }

    private GradePartTemplateVO toVO(GradePartTemplate template) {
        GradePartTemplateVO vo = new GradePartTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setCourseCategory(template.getCourseCategory());
        vo.setCreatedAt(template.getCreatedAt());
        if (template.getParts() != null && !template.getParts().isBlank()) {
            try {
                vo.setParts(objectMapper.readValue(template.getParts(), new TypeReference<List<GradePartForm>>() {
                }));
            } catch (Exception ex) {
                vo.setParts(List.of());
            }
        }
        return vo;
    }
}
