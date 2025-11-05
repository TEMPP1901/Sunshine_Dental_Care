package sunshine_dental_care.services.auth_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AvatarUrlService {
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public String toAbsolute(String path) {
        if (path == null || path.isBlank()) {
            return normalizeBase(publicBaseUrl) + "/uploads_avatar/default-avatar.png";
        }
        String p = path.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (!p.startsWith("/")) p = "/" + p;
        return normalizeBase(publicBaseUrl) + p;
    }

    public String defaultAvatar() {
        return normalizeBase(publicBaseUrl) + "/uploads_avatar/default-avatar.png";
    }

    private String normalizeBase(String base) {
        return (base != null && base.endsWith("/")) ? base.substring(0, base.length() - 1) : base;
    }
}
