package sunshine_dental_care.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class CurrentUser extends User {

    private final Integer userId;
    private final String fullName;
    private final String email;

    // --- CONSTRUCTOR 1: Chuẩn Security (6 tham số) ---
    public CurrentUser(Integer userId, String username, String password, String fullName, String email, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
    }

    // --- CONSTRUCTOR 2: Hỗ trợ WebSocketAuthInterceptor (4 tham số) ---
    public CurrentUser(Integer userId, String email, String fullName, List<String> roles) {
        // Dùng userId làm username để convertAndSendToUser(userId, ...) định tuyến đúng
        super(String.valueOf(userId), "", roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }

    // --- HÀM TƯƠNG THÍCH 1: Cho CheckoutService (userId()) ---
    public Integer userId() {
        return this.userId;
    }

    // --- HÀM TƯƠNG THÍCH 2: Cho AttendanceController (roles()) ---
    // Chuyển đổi từ GrantedAuthority về List<String> để code cũ không bị lỗi
    public List<String> roles() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "CurrentUser{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", roles=" + getAuthorities() +
                '}';
    }
}