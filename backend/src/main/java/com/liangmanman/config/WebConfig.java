package com.liangmanman.config;

import com.liangmanman.admin.AdminInterceptor;
import com.liangmanman.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final AppProperties props;

    public WebConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor, AppProperties props) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/admin/**");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/api/admin/**");
    }

    /** 本地存储的上传图片；接入腾讯云 COS 后由 COS 提供访问，不再走这里 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        try {
            // 目录不存在时 toUri() 不带结尾的 “/”，Spring 会把它当成文件而不是目录，导致图片全部 404
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录 " + dir, e);
        }
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }

    /** H5 调试用；小程序请求不受浏览器跨域限制 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 运营后台走同域（nginx 反代），不需要跨域；这里只放开小程序 H5 调试用的接口
        registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*");
    }
}
