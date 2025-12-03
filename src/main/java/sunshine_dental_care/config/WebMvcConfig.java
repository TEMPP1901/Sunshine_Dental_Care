// Cấu hình Spring MVC cho phép truy cập trực tiếp file ảnh từ thư mục uploads_avatar qua URL /uploads_avatar/**

package sunshine_dental_care.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Value("${app.upload.base-dir:uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Path.of(baseDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads_avatar/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");
    }
}
