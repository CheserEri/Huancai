package cn.huancai.erp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ApiPermissionInterceptor apiPermissionInterceptor;
    private final WxParentAuthInterceptor wxParentAuthInterceptor;
    private final String uploadDir;

    public WebConfig(AuthInterceptor authInterceptor,
                     ApiPermissionInterceptor apiPermissionInterceptor,
                     WxParentAuthInterceptor wxParentAuthInterceptor,
                     @Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.authInterceptor = authInterceptor;
        this.apiPermissionInterceptor = apiPermissionInterceptor;
        this.wxParentAuthInterceptor = wxParentAuthInterceptor;
        this.uploadDir = uploadDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 家长端小程序使用独立会话，不走员工登录与菜单权限
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/wxapp/**");
        // 先校验登录态，再校验接口对应的菜单权限
        registry.addInterceptor(apiPermissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/wxapp/**");
        registry.addInterceptor(wxParentAuthInterceptor)
                .addPathPatterns("/api/wxapp/**")
                .excludePathPatterns("/api/wxapp/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 开发环境允许任意来源（含局域网 IP、主机名），否则跨设备访问时 POST 会被 CORS 拦成 403
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 班级相册等上传文件按本地目录对外提供访问
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
