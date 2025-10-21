package sunshine_dental_care.config.huybro_enable;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class HuybroTestSecurityConfig {

    @Bean
    SecurityFilterChain huybroTestSecurityFilter(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Bật request
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
