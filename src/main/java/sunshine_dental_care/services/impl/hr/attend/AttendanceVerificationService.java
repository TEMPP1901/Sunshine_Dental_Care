package sunshine_dental_care.services.impl.hr.attend;

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

    // Xác thực khuôn mặt và WiFi khi chấm công
    public VerificationResult verify(Integer userId,
                                     Integer clinicId,
                                     String faceEmbedding,
                                     String ssid,
                                     String bssid) {
        if (faceEmbedding == null || faceEmbedding.trim().isEmpty()) {
            log.error("Face verification failed for user {}: input embedding is null or empty", userId);
            throw new FaceVerificationFailedException(
                    "Face embedding is required for check-in. Please capture your face image.");
        }
        
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new FaceVerificationFailedException(
                        "No face profile registered for employee. Please register face first."));

        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            throw new FaceVerificationFailedException(
                    "Face embedding not registered for this employee.");
        }

        // log để debug kiểm tra embedding
        log.info("=== FACE VERIFICATION FOR USER {} ===", userId);
        log.info("Stored embedding length: {} chars, starts with: {}...", 
                storedEmbedding.length(), 
                storedEmbedding.length() > 50 ? storedEmbedding.substring(0, 50) : storedEmbedding);
        log.info("Input embedding length: {} chars, starts with: {}...", 
                faceEmbedding.length(), 
                faceEmbedding.length() > 50 ? faceEmbedding.substring(0, 50) : faceEmbedding);

        // Phát hiện nếu input embedding giống hệt với db
        if (faceEmbedding.trim().equals(storedEmbedding.trim())) {
            log.error("CRITICAL: Input embedding and stored embedding are IDENTICAL! This should not happen for different faces.");
        }

        // Kiểm tra format embedding phải là mảng dạng JSON
        try {
            if (!faceEmbedding.trim().startsWith("[") || !faceEmbedding.trim().endsWith("]")) {
                log.error("Face verification failed for user {}: invalid embedding format (not JSON array)", userId);
                throw new FaceVerificationFailedException(
                        "Invalid face embedding format. Please capture your face image again.");
            }
        } catch (Exception e) {
            if (e instanceof FaceVerificationFailedException) {
                throw e;
            }
            log.error("Face verification failed for user {}: error validating embedding format: {}", userId, e.getMessage());
            throw new FaceVerificationFailedException("Invalid face embedding format: " + e.getMessage());
        }

        FaceVerificationResult faceResult;
        try {
            faceResult = faceRecognitionService.verifyFace(faceEmbedding, storedEmbedding);
            log.info("Face verification for user {}: verified={}, similarity={}",
                    userId, faceResult.isVerified(),
                    String.format("%.4f", faceResult.getSimilarityScore()));

            double similarityScore = faceResult.getSimilarityScore();
            double requiredThreshold = 0.8; // cần thỏa mãn >=80% và isVerified=true

            // Validate cả hai: isVerified và similarityScore
            if (!faceResult.isVerified() || similarityScore < requiredThreshold) {
                log.error("Face verification FAILED for user {}: verified={}, similarity={} < {}. Check-in BLOCKED.",
                        userId, faceResult.isVerified(), String.format("%.4f", similarityScore), String.format("%.2f", requiredThreshold));
                
                String errorMessage;
                // comment rõ xử lý lỗi theo từng mức độ tương đồng
                if (similarityScore < 0.5) {
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Đây không phải khuôn mặt đã đăng ký. Vui lòng sử dụng khuôn mặt của chính bạn để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else if (similarityScore < 0.7) {
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Khuôn mặt này không phải của bạn. Vui lòng sử dụng khuôn mặt đã đăng ký để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else {
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Khuôn mặt này không khớp với khuôn mặt đã đăng ký. Vui lòng đảm bảo ánh sáng tốt, nhìn thẳng vào camera và sử dụng khuôn mặt của chính bạn.",
                            similarityScore * 100, requiredThreshold * 100);
                }
                throw new FaceVerificationFailedException(errorMessage);
            }

            // Cảnh báo nếu pass nhưng similarity thấp, cảnh báo log nhưng vẫn cho qua
            if (similarityScore >= requiredThreshold && similarityScore < 0.85) {
                log.warn("Face verification PASSED for user {} but with relatively low similarity: {}. Please ensure you are using the correct registered face.",
                        userId, String.format("%.4f", similarityScore));
            }
            // Log thông tin khi similarity rất cao để tracking
            if (similarityScore >= 0.85) {
                log.info("Face verification PASSED for user {} with high similarity: {} (>= 0.85). Strong match confirmed.",
                        userId, String.format("%.4f", similarityScore));
            }
        } catch (FaceVerificationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Face verification error for user {}: {}", userId, e.getMessage(), e);
            throw new FaceVerificationFailedException("Face verification failed: " + e.getMessage(), e);
        }

        WiFiValidationResult wifiResult = wifiValidationService.validateWiFi(ssid, bssid, clinicId);

        log.info("WiFi validation for user {} at clinic {}: valid={}, SSID={}, BSSID={}, message={}",
                userId, clinicId, wifiResult.isValid(), ssid, bssid, wifiResult.getMessage());

        // Xử lý khi WiFi không hợp lệ
        if (!wifiResult.isValid()) {
            if (wifiConfig.isEnforce()) {
                log.error("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in blocked.",
                        clinicId, ssid, bssid);
                throw new WiFiValidationFailedException(
                        String.format("WiFi validation failed for clinic %d. SSID '%s' or BSSID '%s' not in whitelist. Please connect to an authorized WiFi network.",
                                clinicId, ssid, bssid));
            } else {
                log.warn("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in will proceed but verification status may be affected (enforcement disabled).",
                        clinicId, ssid, bssid);
            }
        }

        return new VerificationResult(faceProfile, faceResult, wifiResult);
    }

    // class trả về kết quả xác thực
    @Getter
    @AllArgsConstructor
    public static class VerificationResult {
        private EmployeeFaceProfile faceProfile;
        private FaceVerificationResult faceResult;
        private WiFiValidationResult wifiResult;
    }
}
