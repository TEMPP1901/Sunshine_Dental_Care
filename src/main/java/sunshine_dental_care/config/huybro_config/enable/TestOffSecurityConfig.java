package sunshine_dental_care.config.huybro_config.enable;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class TestOffSecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    public TestOffSecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain productsPublicChain(HttpSecurity http) throws Exception {
        http
                // Áp dụng riêng cho đường dẫn /api/products/**
                .securityMatcher("/api/products/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
