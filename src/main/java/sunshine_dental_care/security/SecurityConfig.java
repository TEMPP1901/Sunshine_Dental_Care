package sunshine_dental_care.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)

public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService;
    private final AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS & CSRF
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Stateless Session (Vì dùng JWT)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Xử lý lỗi 401 (Unauthorized) & 403 (Forbidden) trả về JSON
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            // Trả về JSON nếu là API request
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                            } else {
                                // Redirect login nếu là trang thường (hoặc mặc định)
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
                            }
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                            } else {
                                res.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
                            }
                        })
                )

                // 4. Phân quyền URL
                .authorizeHttpRequests(auth -> auth
                        // Preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- CÁC API CÔNG KHAI (KHÔNG CẦN LOGIN) ---
                        .requestMatchers("/locale").permitAll()
                        .requestMatchers("/uploads_avatar/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll() // Public product images *huybro
                        .requestMatchers("/api/auth/sign-up", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/google", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers( "/api/products/**").permitAll() // Public endpoints products *huybro
                        .requestMatchers("/ws/**").permitAll() // WebSocket endpoint (authentication handled in WebSocket layer)

                        .requestMatchers("/api/doctor/**","/api/patients/{patientId}/records/**").hasRole("DOCTOR") // Chỉ bác sĩ được phép,
                        .requestMatchers("/auth/sign-up").permitAll()  // Cho phép đăng ký PATIENT * đổi vị trí vì đây là public

                        // Products - Cho phép Admin truy cập endpoint Accountant
                        .requestMatchers("/api/products/accountant/**").hasAnyRole("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/api/products/**").permitAll()
                        .requestMatchers("/api/cart/**").permitAll()
                        .requestMatchers("/api/checkout/**").permitAll()
                        // Auth Endpoints
                        .requestMatchers(
                                "/api/auth/sign-up",
                                "/api/auth/login",
                                "/api/auth/google",
                                "/oauth2/**",
                                "/login/oauth2/**",

                                // === THÊM 2 DÒNG NÀY ĐỂ FIX LỖI 401 ===
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()


                        // --- CÁC API CẦN LOGIN ---
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()

                        // Phân quyền theo Role
                        // Cho phép Admin truy cập một số endpoint HR Management (departments, clinics, roles)
                        .requestMatchers("/api/hr/management/departments").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/clinics").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/roles").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/rooms").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/**").hasRole("HR")
                        .requestMatchers("/api/hr/employees/**").hasRole("HR")
                        .requestMatchers("/api/public/**").permitAll()

                        // Cho phép mọi user đã login (bao gồm Patient) xem slot và đặt lịch
                        .requestMatchers("/api/booking/**").authenticated()

                        // === only Reception and Admin can access role reception API  ===
                        .requestMatchers("/api/reception/**").hasAnyRole("RECEPTION", "ADMIN")

                        // Additional auth endpoints
                        .requestMatchers("/auth/sign-up").permitAll()  // Cho phép đăng ký PATIENT
                        .requestMatchers("/api/hr/employees/doctors").authenticated()  // Cho phép authenticated users xem danh sách bác sĩ
                        .requestMatchers("/api/hr/employees/**").hasRole("HR")  // Chỉ HR được phép các endpoint khác
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/hr/management/**").hasRole("HR")  // HR Management endpoints

                        .requestMatchers("/api/hr/employees/**").hasRole("HR")  // Chỉ HR được phép
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Tất cả request còn lại phải đăng nhập
                        .anyRequest().authenticated()
                )

                // 5. Cấu hình OAuth2 (Google Login)
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                // 6. Thêm Filter JWT
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}