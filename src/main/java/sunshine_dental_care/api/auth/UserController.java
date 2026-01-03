package sunshine_dental_care.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.UserAvatarService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepo userRepo;
    // 1. [MỚI] Inject thêm PatientRepo để thao tác với bảng Patients
    private final PatientRepo patientRepo;
    private final UserAvatarService userAvatarService;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CurrentUser cu) {
        User u = userRepo.findById(cu.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();

        var body = new LinkedHashMap<String, Object>();
        body.put("userId", u.getId());
        body.put("email", u.getEmail());
        body.put("fullName", u.getFullName());
        body.put("phone", u.getPhone());
        body.put("username", u.getUsername());
        body.put("avatarUrl", u.getAvatarUrl());
        body.put("roles", cu.roles());
        body.put("hasPassword", hasPassword);

        return ResponseEntity.ok(body);
    }

    // =================================================================
    // API CẬP NHẬT THÔNG TIN CÁ NHÂN (ĐÃ THÊM LOGIC ĐỒNG BỘ PATIENT)
    // =================================================================
    @PatchMapping("/{id}")
    @Transactional // [MỚI] Đảm bảo tính toàn vẹn dữ liệu (User & Patient cùng update)
    public ResponseEntity<?> updateProfile(
            @PathVariable Integer id,
            @RequestBody Map<String, String> dto,
            @AuthenticationPrincipal CurrentUser cu
    ) {
        // Kiểm tra quyền: Chỉ chính chủ mới được sửa
        if (!id.equals(cu.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        User u = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean isDataChanged = false;

        // 1. Cập nhật thông tin vào object User
        if (dto.containsKey("fullName")) {
            u.setFullName(dto.get("fullName"));
            isDataChanged = true;
        }
        if (dto.containsKey("email")) {
            u.setEmail(dto.get("email"));
            isDataChanged = true;
        }
        if (dto.containsKey("phone")) {
            u.setPhone(dto.get("phone"));
            isDataChanged = true;
        }

        // Lưu bảng User trước
        userRepo.save(u);

        // 2. [QUAN TRỌNG] Đồng bộ sang bảng Patients
        // Nếu User update thành công, ta tìm Patient tương ứng để update theo
        if (isDataChanged) {
            Patient p = patientRepo.findByUserId(u.getId()).orElse(null);

            if (p != null) {
                // Nếu User có Patient profile, cập nhật các trường tương ứng
                if (dto.containsKey("fullName")) p.setFullName(u.getFullName());
                if (dto.containsKey("email"))    p.setEmail(u.getEmail());
                if (dto.containsKey("phone"))    p.setPhone(u.getPhone()); // <--- Đây là cái bạn cần nhất

                patientRepo.save(p);
            }
        }

        // Trả về dữ liệu User đã cập nhật để Frontend hiển thị lại
        return ResponseEntity.ok(new LinkedHashMap<String, Object>() {{
            put("userId", u.getId());
            put("fullName", u.getFullName());
            put("email", u.getEmail());
            put("phone", u.getPhone());
            put("username", u.getUsername());
            put("avatarUrl", u.getAvatarUrl());
        }});
    }

    // upload avatar to cloud
    @PatchMapping("/{id}/avatar")
    public ResponseEntity<?> uploadAvatar(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser cu
    ) throws IOException {
        if (!id.equals(cu.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        User u = userAvatarService.updateAvatar(id, file);

        return ResponseEntity.ok(Map.of(
                "userId", u.getId(),
                "avatarUrl", u.getAvatarUrl(),
                "avatarPublicId", u.getAvatarPublicId()
        ));
    }

    /**
     * Delete Avatar
     * - Hard delete  Cloudinary if have publicId.
     * - Null avatarUrl + avatarPublicId in DB.
     */
    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<?> deleteAvatar(
            @PathVariable Integer id,
            @AuthenticationPrincipal CurrentUser cu
    ) throws IOException {
        if (!id.equals(cu.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        userAvatarService.removeAvatar(id);
        return ResponseEntity.noContent().build();
    }
}