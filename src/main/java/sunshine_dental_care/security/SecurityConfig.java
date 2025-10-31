package sunshine_dental_care.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/locale").permitAll()
                        .requestMatchers("/auth/sign-up").permitAll()  // Cho phép đăng ký PATIENT
                        .requestMatchers("/api/hr/employees/**").hasRole("HR")  // Chỉ HR được phép
                        .anyRequest().permitAll());
        return http.build();
    }
}
