package sunshine_dental_care.services.upload_file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarStorageService {
    
    @Value("${app.upload.base-dir:uploads_avatar}")
    private String baseDir;

    @Value("${app.upload.avatar-subdir:avatars}")
    private String avatarSubdir;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;
    
    private final FaceRecognitionService faceRecognitionService;
    private final EmployeeFaceProfileRepo faceProfileRepo;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Transactional
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
        String avatarUrl = publicBaseUrl + relPath;
        
        // Extract face embedding từ ảnh đã lưu (để tránh đọc MultipartFile stream 2 lần)
        try {
            log.info("Extracting face embedding for user {} from saved image", userId);
            String embedding = faceRecognitionService.extractEmbeddingFromPath(target.toString());
            
            // Validate embedding format (phải là JSON array)
            if (embedding == null || embedding.trim().isEmpty()) {
                throw new IllegalArgumentException("Extracted embedding is empty");
            }
            
            // Validate embedding là JSON array format
            if (!embedding.trim().startsWith("[") || !embedding.trim().endsWith("]")) {
                throw new IllegalArgumentException("Invalid embedding format: must be JSON array");
            }
            
            // Lưu hoặc cập nhật EmployeeFaceProfile (chỉ lưu embedding, ảnh lưu ở User.avatarUrl)
            EmployeeFaceProfile profile = faceProfileRepo.findByUserId(userId)
                .orElse(new EmployeeFaceProfile());
            profile.setUserId(userId);
            profile.setFaceEmbedding(embedding);
            // Không lưu faceImageUrl nữa, ảnh đã lưu ở User.avatarUrl
            faceProfileRepo.save(profile);
            
            log.info("Face embedding saved successfully for user {}", userId);
        } catch (Exception ex) {
            // Log warning nhưng không fail việc upload avatar
            // Vì có thể ảnh không có face hoặc model chưa sẵn sàng
            log.warn("Failed to extract face embedding for user {}: {}. Avatar uploaded but face profile not updated.", 
                userId, ex.getMessage());
        }
        
        return avatarUrl;
    }

    // Upload avatar và extract embedding nhưng KHÔNG lưu vào EmployeeFaceProfile
    // Dùng cho yêu cầu cập nhật face profile (cần HR duyệt)
    // Trả về Map chứa avatarUrl và embedding
    @Transactional
    public java.util.Map<String, String> storeAvatarAndExtractEmbedding(MultipartFile file, Integer userId) throws IOException, Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type");
        }
        
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
        String avatarUrl = publicBaseUrl + relPath;
        
        // Extract embedding từ file đã upload (KHÔNG lưu vào EmployeeFaceProfile)
        String embedding = null;
        try {
            log.info("Extracting face embedding for update request from saved image: {}", target.toString());
            embedding = faceRecognitionService.extractEmbeddingFromPath(target.toString());
            
            if (embedding == null || embedding.trim().isEmpty()) {
                throw new IllegalArgumentException("Extracted embedding is empty");
            }
            
            if (!embedding.trim().startsWith("[") || !embedding.trim().endsWith("]")) {
                throw new IllegalArgumentException("Invalid embedding format: must be JSON array");
            }
        } catch (Exception ex) {
            log.error("Failed to extract face embedding for update request: {}", ex.getMessage(), ex);
            throw new Exception("Failed to extract face embedding: " + ex.getMessage(), ex);
        }
        
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("avatarUrl", avatarUrl);
        result.put("embedding", embedding);
        
        log.info("Avatar uploaded and embedding extracted for update request (not saved to face profile yet): userId={}", userId);
        return result;
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