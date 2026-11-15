package cn.huancai.erp.module.staff.config;

import cn.huancai.erp.module.staff.entity.PermissionTemplate;
import cn.huancai.erp.module.staff.mapper.PermissionTemplateMapper;
import cn.huancai.erp.module.staff.support.PermissionCodec;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 首次启动时按职务写入内置权限模板；已存在的角色模板不会被覆盖。 */
@Configuration
public class PermissionTemplateSeeder {

    private record BuiltinTemplate(String role, String name, List<String> permissions) {
    }

    /** 校长的默认范围：除「磁卡考勤」外的全部菜单。 */
    private static final List<BuiltinTemplate> BUILTINS = List.of(
            new BuiltinTemplate("manager", "校长", List.of(
                    "/marketingSolution", "/order", "/priceTemplate", "/teacherHours",
                    "/student", "/class", "/schedule", "/teachingLog", "/lesson",
                    "/gradeExam", "/gradeEntry", "/gradeQuery", "/gradeAnalysis",
                    "/messagePush", "/classSuspend", "/classComment", "/classHomework", "/classAlbum",
                    "/orgStructure", "/employee", "/finance", "/storage", "/pointsMall", "/dataFeeTemplate")),
            new BuiltinTemplate("finance", "财务", List.of(
                    "/order", "/priceTemplate", "/teacherHours", "/gradeQuery",
                    "/finance", "/storage", "/dataFeeTemplate")),
            new BuiltinTemplate("reception", "前台", List.of(
                    "/order", "/student", "/class", "/schedule",
                    "/gradeQuery", "/messagePush", "/pointsMall")),
            new BuiltinTemplate("consultant", "顾问", List.of(
                    "/marketingSolution", "/order", "/student", "/class", "/schedule",
                    "/messagePush", "/pointsMall")),
            new BuiltinTemplate("teacher", "教师", List.of(
                    "/teacherHours", "/student", "/class", "/schedule", "/teachingLog",
                    "/gradeExam", "/gradeEntry", "/gradeQuery", "/gradeAnalysis",
                    "/classSuspend", "/classComment", "/classHomework", "/classAlbum")),
            new BuiltinTemplate("assistant", "助教", List.of(
                    "/teacherHours", "/student", "/class", "/schedule", "/teachingLog",
                    "/gradeQuery", "/classSuspend", "/classComment", "/classHomework", "/classAlbum")));

    @Bean
    public ApplicationRunner seedPermissionTemplates(PermissionTemplateMapper templateMapper) {
        return args -> {
            for (BuiltinTemplate item : BUILTINS) {
                Long exists = templateMapper.selectCount(new LambdaQueryWrapper<PermissionTemplate>()
                        .eq(PermissionTemplate::getBuiltin, 1)
                        .eq(PermissionTemplate::getRole, item.role()));
                if (exists != null && exists > 0) {
                    continue;
                }
                PermissionTemplate template = new PermissionTemplate();
                template.setName(item.name());
                template.setRole(item.role());
                template.setBuiltin(1);
                template.setPermissions(PermissionCodec.encode(item.permissions()));
                templateMapper.insert(template);
            }
        };
    }
}
