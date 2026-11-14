package cn.huancai.erp.module.pricetemplate.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.campus.entity.Campus;
import cn.huancai.erp.module.campus.mapper.CampusMapper;
import cn.huancai.erp.module.course.entity.Course;
import cn.huancai.erp.module.course.mapper.CourseMapper;
import cn.huancai.erp.module.order.entity.SpOrderItem;
import cn.huancai.erp.module.order.mapper.SpOrderItemMapper;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplate;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplateCourse;
import cn.huancai.erp.module.pricetemplate.entity.PriceTemplatePrice;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplateCourseMapper;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplateMapper;
import cn.huancai.erp.module.pricetemplate.mapper.PriceTemplatePriceMapper;
import cn.huancai.erp.module.teaching.entity.ClassGroup;
import cn.huancai.erp.module.teaching.mapper.ClassGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基础价格模板：价格与课程绑定，报读时按模板带价。
 * 模板只停用不物理删除；已被订单引用的模板禁止删除。
 */
@RestController
@RequestMapping("/api/price-templates")
public class PriceTemplateController {

    private final PriceTemplateMapper templateMapper;
    private final PriceTemplateCourseMapper courseMapper;
    private final PriceTemplatePriceMapper priceMapper;
    private final CourseMapper baseCourseMapper;
    private final CampusMapper campusMapper;
    private final ClassGroupMapper classGroupMapper;
    private final SpOrderItemMapper orderItemMapper;

    public PriceTemplateController(PriceTemplateMapper templateMapper,
                                   PriceTemplateCourseMapper courseMapper,
                                   PriceTemplatePriceMapper priceMapper,
                                   CourseMapper baseCourseMapper,
                                   CampusMapper campusMapper,
                                   ClassGroupMapper classGroupMapper,
                                   SpOrderItemMapper orderItemMapper) {
        this.templateMapper = templateMapper;
        this.courseMapper = courseMapper;
        this.priceMapper = priceMapper;
        this.baseCourseMapper = baseCourseMapper;
        this.campusMapper = campusMapper;
        this.classGroupMapper = classGroupMapper;
        this.orderItemMapper = orderItemMapper;
    }

    public record PriceTier(BigDecimal lessons, BigDecimal unitPrice, BigDecimal totalPrice) {
    }

    public record PriceTemplateForm(String name,
                                    String campusScope,
                                    List<Long> campusIds,
                                    String classType,
                                    BigDecimal materialFee,
                                    Integer validDays,
                                    List<Long> courseIds,
                                    List<PriceTier> prices,
                                    String remark) {
    }

    @GetMapping
    public ApiResult<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<PriceTemplate> q = new LambdaQueryWrapper<PriceTemplate>()
                .like(keyword != null && !keyword.isBlank(), PriceTemplate::getName, keyword)
                .eq(status != null, PriceTemplate::getStatus, status)
                .orderByDesc(PriceTemplate::getId);
        List<PriceTemplate> templates = templateMapper.selectList(q);
        if (courseId != null) {
            List<Long> ids = courseMapper.selectList(new LambdaQueryWrapper<PriceTemplateCourse>()
                            .eq(PriceTemplateCourse::getCourseId, courseId))
                    .stream().map(PriceTemplateCourse::getTemplateId).toList();
            templates = templates.stream().filter(item -> ids.contains(item.getId())).toList();
        }
        Map<Long, String> courseNames = baseCourseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
        Map<Long, String> campusNames = campusMapper.selectList(null).stream()
                .collect(Collectors.toMap(Campus::getId, Campus::getName, (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PriceTemplate template : templates) {
            rows.add(toRow(template, courseNames, campusNames));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("rows", rows);
        return ApiResult.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        PriceTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("价格模板不存在");
        }
        Map<Long, String> courseNames = baseCourseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
        Map<Long, String> campusNames = campusMapper.selectList(null).stream()
                .collect(Collectors.toMap(Campus::getId, Campus::getName, (a, b) -> a));
        return ApiResult.ok(toRow(template, courseNames, campusNames));
    }

    @GetMapping("/options")
    public ApiResult<Map<String, Object>> options() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campuses", campusMapper.selectList(new LambdaQueryWrapper<Campus>()
                        .eq(Campus::getStatus, 1).orderByAsc(Campus::getId))
                .stream().map(item -> Map.of("id", item.getId(), "name", item.getName())).toList());
        data.put("courses", baseCourseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1).orderByAsc(Course::getName))
                .stream().map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("name", item.getName());
                    row.put("campusId", item.getCampusId());
                    row.put("defaultPriceTemplateId", item.getDefaultPriceTemplateId());
                    return row;
                }).toList());
        List<String> classTypes = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroup>()
                        .isNotNull(ClassGroup::getLessonClassName)
                        .select(ClassGroup::getLessonClassName))
                .stream().map(ClassGroup::getLessonClassName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .sorted()
                .toList();
        data.put("classTypes", classTypes);
        return ApiResult.ok(data);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Long> create(@RequestBody PriceTemplateForm form) {
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        PriceTemplate template = new PriceTemplate();
        template.setStatus(1);
        apply(template, form);
        templateMapper.insert(template);
        saveRelations(template.getId(), form);
        return ApiResult.ok(template.getId());
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody PriceTemplateForm form) {
        PriceTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("价格模板不存在");
        }
        String error = validate(form);
        if (error != null) {
            return ApiResult.fail(error);
        }
        apply(template, form);
        templateMapper.updateById(template);
        saveRelations(id, form);
        return ApiResult.ok(null);
    }

    @PostMapping("/{id}/status")
    public ApiResult<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        PriceTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("价格模板不存在");
        }
        template.setStatus(status != null && status == 1 ? 1 : 0);
        templateMapper.updateById(template);
        return ApiResult.ok(null);
    }

    /** 把模板设为所绑定课程的默认价格模板。 */
    @PostMapping("/{id}/default")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Integer> setDefault(@PathVariable Long id) {
        PriceTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ApiResult.fail("价格模板不存在");
        }
        if (template.getStatus() != null && template.getStatus() == 0) {
            return ApiResult.fail("模板已停用，不能设为默认");
        }
        List<Long> courseIds = courseMapper.selectList(new LambdaQueryWrapper<PriceTemplateCourse>()
                        .eq(PriceTemplateCourse::getTemplateId, id))
                .stream().map(PriceTemplateCourse::getCourseId).toList();
        if (courseIds.isEmpty()) {
            return ApiResult.fail("请先为模板绑定课程");
        }
        Course update = new Course();
        update.setDefaultPriceTemplateId(id);
        baseCourseMapper.update(update, new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
        return ApiResult.ok(courseIds.size());
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> delete(@PathVariable Long id) {
        long used = orderItemMapper.selectCount(new LambdaQueryWrapper<SpOrderItem>()
                .eq(SpOrderItem::getPriceTemplateId, id));
        if (used > 0) {
            return ApiResult.fail("该模板已被 " + used + " 条订单使用，只能停用，不能删除");
        }
        courseMapper.delete(new LambdaQueryWrapper<PriceTemplateCourse>()
                .eq(PriceTemplateCourse::getTemplateId, id));
        priceMapper.delete(new LambdaQueryWrapper<PriceTemplatePrice>()
                .eq(PriceTemplatePrice::getTemplateId, id));
        templateMapper.deleteById(id);
        return ApiResult.ok(null);
    }

    private String validate(PriceTemplateForm form) {
        if (form.name() == null || form.name().isBlank()) {
            return "请填写模板名称";
        }
        if (form.courseIds() == null || form.courseIds().isEmpty()) {
            return "请至少绑定一门课程";
        }
        if (form.prices() == null || form.prices().isEmpty()) {
            return "请至少设置一个课时档位";
        }
        for (PriceTier tier : form.prices()) {
            if (tier.lessons() == null || tier.lessons().compareTo(BigDecimal.ZERO) <= 0) {
                return "课时档位必须大于 0";
            }
            if (tier.unitPrice() == null || tier.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return "课时单价必须大于 0";
            }
        }
        return null;
    }

    private void apply(PriceTemplate template, PriceTemplateForm form) {
        template.setName(form.name().trim());
        template.setCampusScope("指定".equals(form.campusScope()) ? "指定" : "全部");
        template.setCampusIds("指定".equals(form.campusScope()) ? joinIds(form.campusIds()) : null);
        template.setClassType(blankToNull(form.classType()));
        template.setMaterialFee(money(form.materialFee()));
        template.setValidDays(form.validDays() != null && form.validDays() > 0 ? form.validDays() : null);
        template.setRemark(blankToNull(form.remark()));
    }

    private void saveRelations(Long templateId, PriceTemplateForm form) {
        courseMapper.delete(new LambdaQueryWrapper<PriceTemplateCourse>()
                .eq(PriceTemplateCourse::getTemplateId, templateId));
        for (Long courseId : form.courseIds()) {
            if (courseId == null) {
                continue;
            }
            PriceTemplateCourse relation = new PriceTemplateCourse();
            relation.setTemplateId(templateId);
            relation.setCourseId(courseId);
            courseMapper.insert(relation);
        }
        priceMapper.delete(new LambdaQueryWrapper<PriceTemplatePrice>()
                .eq(PriceTemplatePrice::getTemplateId, templateId));
        int sort = 0;
        for (PriceTier tier : form.prices()) {
            PriceTemplatePrice row = new PriceTemplatePrice();
            row.setTemplateId(templateId);
            row.setLessons(tier.lessons().setScale(1, RoundingMode.HALF_UP));
            row.setUnitPrice(money(tier.unitPrice()));
            row.setTotalPrice(tier.totalPrice() == null
                    ? money(tier.lessons().multiply(tier.unitPrice()))
                    : money(tier.totalPrice()));
            row.setSortNo(sort++);
            priceMapper.insert(row);
        }
    }

    private Map<String, Object> toRow(PriceTemplate template,
                                      Map<Long, String> courseNames,
                                      Map<Long, String> campusNames) {
        List<PriceTemplateCourse> relations = courseMapper.selectList(
                new LambdaQueryWrapper<PriceTemplateCourse>()
                        .eq(PriceTemplateCourse::getTemplateId, template.getId())
                        .orderByAsc(PriceTemplateCourse::getId));
        List<Long> courseIds = relations.stream().map(PriceTemplateCourse::getCourseId).toList();
        List<PriceTemplatePrice> prices = priceMapper.selectList(
                new LambdaQueryWrapper<PriceTemplatePrice>()
                        .eq(PriceTemplatePrice::getTemplateId, template.getId())
                        .orderByAsc(PriceTemplatePrice::getSortNo)
                        .orderByAsc(PriceTemplatePrice::getId));
        List<Long> campusIds = splitIds(template.getCampusIds());
        long usedCount = orderItemMapper.selectCount(new LambdaQueryWrapper<SpOrderItem>()
                .eq(SpOrderItem::getPriceTemplateId, template.getId()));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", template.getId());
        row.put("name", template.getName());
        row.put("campusScope", template.getCampusScope());
        row.put("campusIds", campusIds);
        row.put("campusNames", campusIds.stream().map(id -> campusNames.getOrDefault(id, "#" + id)).toList());
        row.put("classType", template.getClassType());
        row.put("materialFee", template.getMaterialFee());
        row.put("validDays", template.getValidDays());
        row.put("status", template.getStatus());
        row.put("remark", template.getRemark());
        row.put("courseIds", courseIds);
        row.put("courseNames", courseIds.stream().map(id -> courseNames.getOrDefault(id, "#" + id)).toList());
        row.put("prices", prices.stream().map(item -> {
            Map<String, Object> tier = new LinkedHashMap<>();
            tier.put("lessons", item.getLessons());
            tier.put("unitPrice", item.getUnitPrice());
            tier.put("totalPrice", item.getTotalPrice());
            return tier;
        }).toList());
        row.put("usedCount", usedCount);
        row.put("updatedAt", template.getUpdatedAt());
        return row;
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private static List<Long> splitIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String item : value.split(",")) {
            try {
                ids.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException ignored) {
                // 跳过历史脏数据
            }
        }
        return ids;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
