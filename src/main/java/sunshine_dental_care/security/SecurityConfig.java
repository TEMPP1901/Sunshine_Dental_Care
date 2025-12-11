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

                // 3. Xử lý lỗi 401 & 403 trả về JSON
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                            } else {
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

                // 4. PHÂN QUYỀN URL (MERGED TUAN & LONG)
                .authorizeHttpRequests(auth -> auth
                        // --- PREFLIGHT REQUESTS ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- PUBLIC STATIC RESOURCES ---
                        .requestMatchers("/locale").permitAll()
                        .requestMatchers("/uploads_avatar/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll() // Product images
                        .requestMatchers("/ws/**").permitAll() // WebSocket

                        // --- PUBLIC API (AUTH, PRODUCTS, ETC.) ---
                        .requestMatchers(
                                "/api/auth/sign-up",
                                "/api/auth/login",
                                "/api/auth/google",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/verify-account", // Của Tuấn
                                "/api/auth/resend-verification", // Của Tuấn

                                // [MỚI CỦA TUẤN] API OTP & PHONE LOGIN
                                "/api/auth/login-phone/step1",
                                "/api/auth/login-phone/step2",
                                "/api/auth/login-phone/password",

                                // Public Shop API
                                "/api/products/**",
                                "/api/cart/**",
                                "/api/checkout/**",
                                "/api/public/**"
                        ).permitAll()

                        // --- AUTHENTICATED USER (LOGGED IN) ---
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                        .requestMatchers("/api/booking/**").authenticated() // Mọi user đều được đặt lịch
                        .requestMatchers("/api/hr/employees/doctors").authenticated() // Xem danh sách bác sĩ

                        // --- DOCTOR ROLE ---
                        .requestMatchers("/api/doctor/**", "/api/patients/{patientId}/records/**").hasRole("DOCTOR")

                        // --- ACCOUNTANT ROLE (Admin cũng được vào) ---
                        .requestMatchers("/api/products/accountant/**").hasAnyRole("ACCOUNTANT", "ADMIN")

                        // --- HR ROLE (Admin cũng được vào một số mục cấu hình) ---
                        .requestMatchers("/api/hr/management/departments", "/api/hr/management/clinics",
                                "/api/hr/management/roles", "/api/hr/management/rooms").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/**").hasRole("HR")
                        .requestMatchers("/api/hr/employees/**").hasRole("HR")

                        // --- RECEPTION ROLE (Admin cũng được vào) ---
                        .requestMatchers("/api/reception/**").hasAnyRole("RECEPTION", "ADMIN")

                        // --- ADMIN ROLE ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // --- MẶC ĐỊNH CÒN LẠI ---
                        .anyRequest().authenticated()
                )

                // 5. OAuth2 Login
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                // 6. JWT Filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}