package sunshine_dental_care.services.upload_file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {
    @Value("${app.upload.base-dir:uploads_avatar}")
    private String baseDir;

    @Value("${app.upload.avatar-subdir:avatars}")
    private String avatarSubdir;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    public String storeAvatar(MultipartFile file, Integer userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type");
        }
        // Folder datetime , avoid name conflict
        String ymd = LocalDate.now().toString();
        String ext = getExt(file.getOriginalFilename());
        String safeName = "u" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + ext;

        Path root = Path.of(baseDir).toAbsolutePath().normalize();
        Path dir = root.resolve(avatarSubdir).resolve(ymd);
        Files.createDirectories(dir);

        Path target = dir.resolve(safeName).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String relPath = "/" + baseDir.replaceAll("^/+", "").replaceAll("/+$", "")
                + "/" + avatarSubdir + "/" + ymd + "/" + safeName;
        return publicBaseUrl + relPath;
    }

    private String getExt(String original) {
        if (original == null) return ".jpg";
        int dot = original.lastIndexOf('.');
        if (dot < 0) return ".jpg";
        String e = original.substring(dot).toLowerCase();
        return switch (e) {
            case ".jpeg", ".jpg" -> ".jpg";
            case ".png" -> ".png";
            case ".webp" -> ".webp";
            case ".gif" -> ".gif";
            default -> ".jpg";
        };
    }
}