package sunshine_dental_care.services.auth_service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;

import java.io.IOException;

@Service
public class UserAvatarService {

    private final UserRepo userRepo;
    private final CloudinaryUploadService cloud;

    public UserAvatarService(UserRepo userRepo, CloudinaryUploadService cloud) {
        this.userRepo = userRepo;
        this.cloud = cloud;
    }

    @Transactional
    public User updateAvatar(Integer userId, MultipartFile file) throws IOException {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Delete old image after change new image
        if (user.getAvatarPublicId() != null && !user.getAvatarPublicId().isBlank()) {
            cloud.deleteByPublicId(user.getAvatarPublicId());
        }

        // Upload new images
        var uploaded = cloud.upload(file, "users/avatars");

        // Update DB
        user.setAvatarUrl(uploaded.url());
        user.setAvatarPublicId(uploaded.publicId());
        return userRepo.save(user);
    }

    @Transactional
    public void removeAvatar(Integer userId) throws IOException {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getAvatarPublicId() != null && !user.getAvatarPublicId().isBlank()) {
            cloud.deleteByPublicId(user.getAvatarPublicId());
        }

        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        userRepo.save(user);
    }
}

