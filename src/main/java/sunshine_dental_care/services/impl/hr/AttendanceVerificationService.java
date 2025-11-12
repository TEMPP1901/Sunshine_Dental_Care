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

    public VerificationResult verify(Integer userId,
                                     Integer clinicId,
                                     String faceEmbedding,
                                     String ssid,
                                     String bssid) {
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(userId)
                .orElseThrow(() -> new FaceVerificationFailedException(
                        "Employee face profile not found. Please register face first."));

        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            throw new FaceVerificationFailedException(
                    "Face embedding not registered for this employee");
        }

        FaceVerificationResult faceResult;
        try {
            faceResult = faceRecognitionService.verifyFace(faceEmbedding, storedEmbedding);
            log.info("Face verification for user {}: verified={}, similarity={}", 
                    userId, faceResult.isVerified(), 
                    String.format("%.4f", faceResult.getSimilarityScore()));
            
            // ENFORCE: Chỉ cho check-in khi face similarity >= 0.8 (80%)
            if (!faceResult.isVerified()) {
                log.error("Face verification FAILED for user {}: similarity={} < 0.8. Check-in BLOCKED.", 
                        userId, String.format("%.4f", faceResult.getSimilarityScore()));
                throw new FaceVerificationFailedException(
                        String.format("Face verification failed. Similarity score %.4f is below the required threshold of 0.8 (80%%). Please ensure good lighting and face the camera directly.", 
                                faceResult.getSimilarityScore()));
            }
        } catch (FaceVerificationFailedException e) {
            // Re-throw FaceVerificationFailedException để block check-in
            throw e;
        } catch (Exception e) {
            log.error("Face verification error for user {}: {}", userId, e.getMessage(), e);
            // Block check-in nếu có lỗi trong quá trình verify
            throw new FaceVerificationFailedException("Face verification failed: " + e.getMessage(), e);
        }

        WiFiValidationResult wifiResult = wifiValidationService.validateWiFi(ssid, bssid, clinicId);
        
        // Log WiFi validation result for debugging
        log.info("WiFi validation for user {} at clinic {}: valid={}, SSID={}, BSSID={}, message={}", 
                userId, clinicId, wifiResult.isValid(), ssid, bssid, wifiResult.getMessage());
        
        if (!wifiResult.isValid()) {
            // Check if WiFi validation enforcement is enabled
            if (wifiConfig.isEnforce()) {
                // Enforce WiFi validation: throw exception to block check-in
                log.error("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in blocked.", 
                        clinicId, ssid, bssid);
                throw new WiFiValidationFailedException(
                        String.format("WiFi validation failed for clinic %d. SSID '%s' or BSSID '%s' not in whitelist. Please connect to an authorized WiFi network.", 
                                clinicId, ssid, bssid));
            } else {
                // Log warning but don't throw exception - allow check-in to proceed (development mode)
                log.warn("WiFi validation failed for clinic {}: SSID={}, BSSID={}. Check-in will proceed but verification status may be affected (enforcement disabled).", 
                        clinicId, ssid, bssid);
            }
        }

        return new VerificationResult(faceProfile, faceResult, wifiResult);
    }

    @Getter
    @AllArgsConstructor
    public static class VerificationResult {
        private EmployeeFaceProfile faceProfile;
        private FaceVerificationResult faceResult;
        private WiFiValidationResult wifiResult;
    }
}


