package sunshine_dental_care.config;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.jwt_security.JwtService;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    // Hàm xác thực người dùng khi kết nối websocket
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Kiểm tra CONNECT frame từ client
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("[WebSocket] Received CONNECT command");

            String authHeader = accessor.getFirstNativeHeader("Authorization");
            log.debug("[WebSocket] Authorization header present: {}", authHeader != null);

            // Kiểm tra header Authorization
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    var jws = jwtService.parse(token);
                    var claims = jws.getBody();

                    Integer userId = claims.get("uid", Integer.class);
                    String email = claims.getSubject();
                    String name = claims.get("name", String.class);

                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) claims.getOrDefault("roles", List.of());

                    CurrentUser principal = new CurrentUser(userId, email, name, roles);
                    var authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    accessor.setUser(auth);

                    // In ra thông tin user sau khi xác thực thành công
                    log.info("[WebSocket] Authenticated user: {} (userId: {})", email, userId);
                } catch (ExpiredJwtException e) {
                    // JWT hết hạn, cần refresh token từ client
                    log.warn("[WebSocket] JWT token expired. User needs to refresh token. Expired at: {}, Current time: {}",
                            e.getClaims().getExpiration(), new java.util.Date());
                } catch (Exception e) {
                    // Lỗi xác thực khác (token sai, định dạng lỗi...)
                    log.error("[WebSocket] Failed to authenticate WebSocket connection: {}", e.getMessage(), e);
                }
            } else {
                // Không có Authorization header (không truyền token)
                log.warn("[WebSocket] No Authorization header found in CONNECT frame. Connection may be rejected by client.");
            }
        }

        return message;
    }
}
