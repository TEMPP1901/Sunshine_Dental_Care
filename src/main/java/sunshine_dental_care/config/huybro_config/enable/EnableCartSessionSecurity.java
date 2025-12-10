package sunshine_dental_care.config.huybro_config.enable;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({CartSessionSecurityConfig.class})
// Bật cấu hình security riêng cho giỏ hàng dùng session (không lưu DB)
public @interface EnableCartSessionSecurity {
}
