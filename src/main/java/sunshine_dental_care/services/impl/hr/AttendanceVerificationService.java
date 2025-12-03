package sunshine_dental_care.services.impl.hr;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.FaceVerificationFailedException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.WiFiValidationFailedException;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;
import sunshine_dental_care.services.interfaces.hr.WiFiValidationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceVerificationService {

    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final FaceRecognitionService faceRecognitionService;
    private final WiFiValidationService wifiValidationService;
    private final sunshine_dental_care.config.WiFiConfig wifiConfig;

    // Xác thực cả khuôn mặt và WiFi khi thực hiện chấm công
    public VerificationResult verify(Integer userId,
                                     Integer clinicId,
                                     String faceEmbedding,
                                     String ssid,
                                     String bssid) {
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new FaceVerificationFailedException(
                        "No face profile registered for employee. Please register face first."));

        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            throw new FaceVerificationFailedException(
                    "Face embedding not registered for this employee.");
        }

        FaceVerificationResult faceResult;
        try {
            faceResult = faceRecognitionService.verifyFace(faceEmbedding, storedEmbedding);
            log.info("Face verification for user {}: verified={}, similarity={}",
                    userId, faceResult.isVerified(),
                    String.format("%.4f", faceResult.getSimilarityScore()));

            // Chỉ cho phép chấm công nếu độ tương đồng khuôn mặt >= 0.8
            if (!faceResult.isVerified()) {
                log.error("Face verification FAILED for user {}: similarity={} < 0.8. Check-in BLOCKED.",
                        userId, String.format("%.4f", faceResult.getSimilarityScore()));
                throw new FaceVerificationFailedException(
                        String.format(
                                "Face verification failed. Similarity score %.4f is below the required threshold of 0.8 (80%%). Please ensure good lighting and face the camera directly.",
                                faceResult.getSimilarityScore()));
            }
        } catch (FaceVerificationFailedException e) {
            // Ném lại lỗi xác thực khuôn mặt để chặn chấm công
            throw e;
        } catch (Exception e) {
            log.error("Face verification error for user {}: {}", userId, e.getMessage(), e);
            throw new FaceVerificationFailedException("Face verification failed: " + e.getMessage(), e);
        }

        WiFiValidationResult wifiResult = wifiValidationService.validateWiFi(ssid, bssid, clinicId);

        log.info("WiFi validation for user {} at clinic {}: valid={}, SSID={}, BSSID={}, message={}",
                userId, clinicId, wifiResult.isValid(), ssid, bssid, wifiResult.getMessage());

        // Nếu cấu hình yêu cầu xác thực wifi mà kiểm tra wifi không hợp lệ thì không cho chấm công
        if (!wifiResult.isValid()) {
            if (wifiConfig.isEnforce()) {
                log.error("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in blocked.",
                        clinicId, ssid, bssid);
                throw new WiFiValidationFailedException(
                        String.format("WiFi validation failed for clinic %d. SSID '%s' or BSSID '%s' not in whitelist. Please connect to an authorized WiFi network.",
                                clinicId, ssid, bssid));
            } else {
                // Nếu không bắt buộc xác thực wifi, cảnh báo và vẫn cho qua
                log.warn("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in will proceed but verification status may be affected (enforcement disabled).",
                        clinicId, ssid, bssid);
            }
        }

        return new VerificationResult(faceProfile, faceResult, wifiResult);
    }

    // Kết quả xác thực: Bao gồm thông tin khuôn mặt, kết quả xác thực khuôn mặt, kết quả xác thực wifi
    @Getter
    @AllArgsConstructor
    public static class VerificationResult {
        private EmployeeFaceProfile faceProfile;
        private FaceVerificationResult faceResult;
        private WiFiValidationResult wifiResult;
    }
}
