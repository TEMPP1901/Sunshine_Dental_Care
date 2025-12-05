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
        // Validate embedding đầu vào trước khi xử lý
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
        
        // Log để debug: kiểm tra userId và embedding
        log.info("=== FACE VERIFICATION FOR USER {} ===", userId);
        log.info("Stored embedding length: {} chars, starts with: {}...", 
                storedEmbedding.length(), 
                storedEmbedding.length() > 50 ? storedEmbedding.substring(0, 50) : storedEmbedding);
        log.info("Input embedding length: {} chars, starts with: {}...", 
                faceEmbedding.length(), 
                faceEmbedding.length() > 50 ? faceEmbedding.substring(0, 50) : faceEmbedding);
        
        // Kiểm tra nếu embedding giống hệt nhau (có thể bị lỗi)
        if (faceEmbedding.trim().equals(storedEmbedding.trim())) {
            log.error("CRITICAL: Input embedding and stored embedding are IDENTICAL! This should not happen for different faces.");
        }
        
        // Validate format của embedding đầu vào
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

            // Chỉ cho phép chấm công nếu độ tương đồng khuôn mặt >= 0.8 (80%)
            // Kiểm tra kép: cả isVerified() và similarity score để đảm bảo an toàn
            double similarityScore = faceResult.getSimilarityScore();
            double requiredThreshold = 0.8; // Ngưỡng 80% để chấp nhận khuôn mặt
            
            // Validation chặt chẽ: phải đạt cả 2 điều kiện
            // 1. isVerified() phải là true (từ FaceRecognitionService với threshold 0.8)
            // 2. similarityScore phải >= 0.8 (double check để đảm bảo)
            if (!faceResult.isVerified() || similarityScore < requiredThreshold) {
                log.error("Face verification FAILED for user {}: verified={}, similarity={} < {}. Check-in BLOCKED.",
                        userId, faceResult.isVerified(), String.format("%.4f", similarityScore), String.format("%.2f", requiredThreshold));
                
                // Thông báo lỗi rõ ràng dựa trên similarity score
                String errorMessage;
                if (similarityScore < 0.5) {
                    // Similarity rất thấp - có thể là người hoàn toàn khác
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Đây không phải khuôn mặt đã đăng ký. Vui lòng sử dụng khuôn mặt của chính bạn để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else if (similarityScore < 0.7) {
                    // Similarity thấp - có thể là người khác
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Khuôn mặt này không phải của bạn. Vui lòng sử dụng khuôn mặt đã đăng ký để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else {
                    // Similarity gần threshold nhưng vẫn không đạt
                    errorMessage = String.format(
                            "Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). Khuôn mặt này không khớp với khuôn mặt đã đăng ký. Vui lòng đảm bảo ánh sáng tốt, nhìn thẳng vào camera và sử dụng khuôn mặt của chính bạn.",
                            similarityScore * 100, requiredThreshold * 100);
                }
                
                throw new FaceVerificationFailedException(errorMessage);
            }
            
            // Log cảnh báo nếu similarity thấp nhưng vẫn pass (trong khoảng 0.8-0.85)
            // Điều này giúp phát hiện các trường hợp đáng ngờ
            if (similarityScore >= requiredThreshold && similarityScore < 0.85) {
                log.warn("Face verification PASSED for user {} but with relatively low similarity: {}. Please ensure you are using the correct registered face.",
                        userId, String.format("%.4f", similarityScore));
            }
            
            // Log thành công với similarity cao để tracking
            if (similarityScore >= 0.85) {
                log.info("Face verification PASSED for user {} with high similarity: {} (>= 0.85). Strong match confirmed.",
                        userId, String.format("%.4f", similarityScore));
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
