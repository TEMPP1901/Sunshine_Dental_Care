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
                // CORS + CSRF
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless (JWT)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable default error pages (trả về HTML) - để GlobalExceptionHandler xử lý JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )

                // Authorize
                // Trả JSON cho 401 & 403 để FE dễ xử lý
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"message\":\"Unauthorized\"}");
                            } else {
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            }
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            String uri = req.getRequestURI();
                            if (uri != null && uri.startsWith("/api/")) {
                                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"message\":\"Forbidden\"}");
                            } else {
                                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                            }
                        })
                )

                .authorizeHttpRequests(auth -> auth
                        // Preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints
                        .requestMatchers("/locale").permitAll()
                        .requestMatchers("/uploads_avatar/**").permitAll()
                        .requestMatchers("/api/auth/sign-up", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/google", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers( "/api/products/**").permitAll() // Public endpoints products *huybro

                        .requestMatchers( "/api/products/**").permitAll() // Public endpoints products *huybro

                        .requestMatchers("/api/doctor/**","/api/patients/{patientId}/records/**").hasRole("DOCTOR") // Chỉ bác sĩ được phép,


                        // Authenticated endpoints
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()

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
                        .requestMatchers("/api/hr/management/**").hasRole("HR")  // HR Management endpoints
                        .anyRequest().authenticated()
                )

                // OAuth2 login
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                // JWT filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
