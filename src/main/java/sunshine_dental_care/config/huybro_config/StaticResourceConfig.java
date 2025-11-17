package sunshine_dental_care.config.huybro_config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
@Getter
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.image.original-dir}")
    private String originalDir;

    @Value("${app.image.cache-dir}")
    private String cacheDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}
