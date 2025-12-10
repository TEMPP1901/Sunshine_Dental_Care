package sunshine_dental_care.security;

import java.util.List;

public record CurrentUser(
                Integer userId,
                String email,
                String fullName,
                List<String> roles) implements java.security.Principal {
        @Override
        public String getName() {
                return String.valueOf(userId);
        }
}
