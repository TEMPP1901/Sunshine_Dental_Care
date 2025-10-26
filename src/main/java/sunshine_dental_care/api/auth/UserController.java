package sunshine_dental_care.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.upload_file.AvatarStorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepo userRepo;
    private final AvatarStorageService avatarStorageService;

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
        return Map.of(
                "userId", user.userId(),
                "email", user.email(),
                "fullName", user.fullName(),
                "roles", user.roles()
        );
    }

    @PatchMapping("/{id}/avatar")
    public ResponseEntity<?> uploadAvatar(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        User u = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String url = avatarStorageService.storeAvatar(file, id);
        u.setAvatarUrl(url);
        userRepo.save(u);

        return ResponseEntity.ok().body(
                java.util.Map.of("userId", u.getId(), "avatarUrl", u.getAvatarUrl())
        );
    }
}
