package sunshine_dental_care.services.jwt_security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {
    private final Key key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:28800000}") long expirationMs // 8h
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    // 1. Tạo Token chính (Access Token)
    public String generateToken(Integer userId, String email, String fullName, List<String> roles) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("name", fullName);
        claims.put("roles", roles == null ? List.of() : roles);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(exp)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. [QUAN TRỌNG] Tạo Token ngắn hạn cho QR Login (Sống 2 phút)
    public String generateShortLivedToken(String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 120000); // 120,000 ms = 2 phút

        return Jwts.builder()
                .setSubject(email) // Lưu email vào subject
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("type", "qr_login") // Đánh dấu đây là token QR
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public String extractEmail(String token) {
        try {
            return parse(token).getBody().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    // 3. [FIX LỖI] Thêm hàm này để AuthServiceImp gọi được
    // Vì subject của token là email, nên extractUsername chính là extractEmail
    public String extractUsername(String token) {
        return extractEmail(token);
    }

    // 4. [QUAN TRỌNG] Kiểm tra token hết hạn (Dùng cho QR Login)
    public boolean isTokenExpired(String token) {
        try {
            Jws<Claims> claims = parse(token);
            return claims.getBody().getExpiration().before(new Date());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true; // Các lỗi parse khác cũng coi như hết hạn/không hợp lệ
        }
    }

    // 5. [FIX LỖI] Kiểm tra tính hợp lệ chung (Optional - nếu code cũ cần)
    // Nếu bạn có dùng userDetails để check thì giữ lại, nếu không thì dùng logic extractUsername ở trên là đủ.
    public boolean isTokenValid(String token, String email) {
        final String username = extractEmail(token);
        return (username.equals(email) && !isTokenExpired(token));
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    // Helper: Tạo Refresh Token (nếu cần dùng sau này)
    public String generateRefreshToken(Integer userId, String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs * 10); // Sống lâu hơn token chính

        return Jwts.builder()
                .setSubject(email)
                .claim("uid", userId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}