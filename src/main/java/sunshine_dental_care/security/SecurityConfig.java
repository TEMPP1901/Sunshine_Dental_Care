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

                // 2. Stateless Session
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Exception Handling
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

                // 4. PHÂN QUYỀN URL
                .authorizeHttpRequests(auth -> auth
                        // --- PREFLIGHT REQUESTS ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- PUBLIC STATIC RESOURCES ---
                        .requestMatchers("/locale").permitAll()
                        .requestMatchers("/uploads_avatar/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/auth/sign-up").permitAll()

                        // Products & inventory
                        .requestMatchers("/api/products/accountant/**").hasAnyRole("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/api/inventory/**").hasAnyRole("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/api/invoices/**").hasAnyRole("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/api/payroll/**").hasAnyRole("ACCOUNTANT", "ADMIN")

                        // Auth Endpoints
                        .requestMatchers(
                                "/api/auth/sign-up",
                                "/api/auth/login",
                                "/api/auth/google",
                                "/api/auth/google-mobile", // Native Mobile Login
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/verify-account",
                                "/api/auth/resend-verification",

                                // API OTP & PHONE LOGIN
                                "/api/auth/login-phone/step1",
                                "/api/auth/login-phone/step2",
                                "/api/auth/login-phone/password",

                                // [MỚI] PUBLIC API CHO QR LOGIN
                                "/api/auth/qr-login" // Mobile gọi API này khi chưa có token chính thức
                        ).permitAll()

                        // Public Shop API
                        .requestMatchers(
                                "/api/products/**",
                                "/api/cart/**",
                                "/api/checkout/**",
                                "/api/public/**"
                        ).permitAll()

                        // --- AUTHENTICATED USER (LOGGED IN) ---
                        // Endpoint "/api/auth/qr-generate" sẽ rơi vào đây (authenticated)
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                        .requestMatchers("/api/booking/**").authenticated()
                        .requestMatchers("/api/hr/employees/doctors").authenticated()

                        // --- ROLES SPECIFIC ---
                        .requestMatchers("/api/doctor/**", "/api/patients/{patientId}/records/**").hasRole("DOCTOR")
                        .requestMatchers("/api/products/accountant/**").hasAnyRole("ACCOUNTANT", "ADMIN")
                        .requestMatchers("/api/hr/management/departments", "/api/hr/management/clinics",
                                "/api/hr/management/roles", "/api/hr/management/rooms").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/management/**").hasRole("HR")
                        .requestMatchers("/api/hr/employees/**").hasRole("HR")

                        // Cho phép mọi user đã login (bao gồm Patient) xem slot và đặt lịch
                        .requestMatchers("/api/booking/**").authenticated()

                        // Cho phép User đã đăng nhập (ROLE_USER) gọi API thanh toán & API Booking
                        .requestMatchers("/api/booking/payment/**").authenticated()
                        .requestMatchers("/api/booking/appointments").authenticated()

                        // --- RECEPTION ROLE (Admin cũng được vào) ---
                        // === only Reception and Admin can access role reception API  ===
                        .requestMatchers("/api/reception/**").hasAnyRole("RECEPTION", "ADMIN")
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