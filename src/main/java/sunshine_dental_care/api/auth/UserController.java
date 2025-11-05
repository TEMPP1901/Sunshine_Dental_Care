package sunshine_dental_care.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.AvatarUrlService;
import sunshine_dental_care.services.upload_file.AvatarStorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepo userRepo;
    private final AvatarStorageService avatarStorageService;
    private final AvatarUrlService avatarUrlService;


    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CurrentUser cu) {
        User u = userRepo.findById(cu.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String avatarAbs = avatarUrlService.toAbsolute(u.getAvatarUrl());
        boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("userId", u.getId());
        body.put("email", u.getEmail());
        body.put("fullName", u.getFullName());
        body.put("phone", u.getPhone());
        body.put("username", u.getUsername());
        body.put("avatarUrl", avatarAbs);
        body.put("roles", cu.roles());
        body.put("hasPassword", hasPassword);

        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateProfile(
            @PathVariable Integer id,
            @RequestBody Map<String, String> dto,
            @AuthenticationPrincipal CurrentUser cu
    ) {
        // Chỉ cho phép user tự sửa hồ sơ
        if (!id.equals(cu.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        User u = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.containsKey("fullName"))
            u.setFullName(dto.get("fullName"));
        if (dto.containsKey("email"))
            u.setEmail(dto.get("email"));
        if (dto.containsKey("phone"))
            u.setPhone(dto.get("phone"));

        userRepo.save(u);

        return ResponseEntity.ok(new java.util.LinkedHashMap<String, Object>() {{
            put("userId", u.getId());
            put("fullName", u.getFullName());
            put("email", u.getEmail());
            put("phone", u.getPhone());
            put("username", u.getUsername());
            put("avatarUrl", avatarUrlService.toAbsolute(u.getAvatarUrl()));
        }});
    }

    @PatchMapping("/{id}/avatar")
    public ResponseEntity<?> uploadAvatar(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser cu
    ) throws Exception {
        if (!id.equals(cu.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        User u = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String absolute = avatarStorageService.storeAvatar(file, id);

        u.setAvatarUrl(absolute);
        userRepo.save(u);

        return ResponseEntity.ok(Map.of(
                "userId", u.getId(),
                "avatarUrl", absolute
        ));
    }
}
