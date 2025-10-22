package sunshine_dental_care.config.huybro_config.enable;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({TestOffSecurityConfig.class})
// Tắt cấu hình security springboot và gọi port Client
public @interface EnableTestOffSecurity {
}
