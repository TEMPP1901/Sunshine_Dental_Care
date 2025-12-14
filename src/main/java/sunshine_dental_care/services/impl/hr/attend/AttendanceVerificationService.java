package sunshine_dental_care.services.impl.hr.attend;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        
            // BẢO MẬT: Đảm bảo face profile của userId trong request tồn tại
        // Đảm bảo chỉ có người A quét đúng mặt A mới được check-in
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("SECURITY ALERT: User {} attempted check-in but has no registered face profile.", userId);
                    return new FaceVerificationFailedException(
                            "Bảo mật: Bạn chưa đăng ký khuôn mặt. Vui lòng đăng ký khuôn mặt trước khi chấm công.");
                });

        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            log.error("SECURITY ALERT: User {} has face profile but embedding is empty.", userId);
            throw new FaceVerificationFailedException(
                    "Bảo mật: Khuôn mặt chưa được đăng ký đầy đủ. Vui lòng đăng ký lại khuôn mặt.");
        }
        
        // BẢO MẬT: Đảm bảo face embedding được verify cho đúng userId
        // Face embedding phải khớp với face profile của userId trong request
        // Không cho phép dùng face embedding của người khác để check-in

        // log để debug kiểm tra embedding
        log.info("=== FACE VERIFICATION FOR USER {} ===", userId);
        log.info("Stored embedding length: {} chars, starts with: {}...", 
                storedEmbedding.length(), 
                storedEmbedding.length() > 50 ? storedEmbedding.substring(0, 50) : storedEmbedding);
        log.info("Input embedding length: {} chars, starts with: {}...", 
                faceEmbedding.length(), 
                faceEmbedding.length() > 50 ? faceEmbedding.substring(0, 50) : faceEmbedding);

        // Phát hiện nếu input embedding giống hệt với db - Đây là dấu hiệu của check-in hộ (replay attack)
        // Khi check-in thật, embedding sẽ khác nhau một chút do ánh sáng, góc chụp, v.v.
        if (faceEmbedding.trim().equals(storedEmbedding.trim())) {
            log.error("CRITICAL SECURITY ALERT: Input embedding and stored embedding are IDENTICAL for user {}! " +
                    "This indicates a potential replay attack or check-in fraud. Check-in BLOCKED.", userId);
            throw new FaceVerificationFailedException(
                    "Bảo mật: Phát hiện khuôn mặt không hợp lệ. Vui lòng chụp lại khuôn mặt trực tiếp từ camera để chấm công.");
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
            
            // Với ArcFace model, threshold 0.85-0.9 là an toàn để đảm bảo chỉ mặt đúng
            double requiredThreshold = 0.85; // cần thỏa mãn >=85% và isVerified=true

            
            // Tăng threshold lên 0.002 để chặn cả trường hợp modify nhẹ embedding
            // Nếu avgDiff quá cao (> 0.2) nhưng similarity cao, có thể là model issue
            double avgDiff = calculateAverageDifference(faceEmbedding, storedEmbedding);
            log.info("Average difference between embeddings for user {}: {}", userId, String.format("%.6f", avgDiff));
            
            // Phát hiện replay attack: embedding quá giống nhau (avgDiff < 0.002)
            // Khi check-in thật, embedding sẽ khác nhau do ánh sáng, góc chụp, v.v.
            // Threshold 0.002 đảm bảo chặn cả trường hợp modify nhẹ embedding để bypass
            if (avgDiff < 0.002) {
                log.error("CRITICAL SECURITY ALERT: Average difference ({}) is too low for user {}! " +
                        "This indicates potential replay attack - embeddings are nearly identical (possibly modified). Check-in BLOCKED.",
                        String.format("%.6f", avgDiff), userId);
                throw new FaceVerificationFailedException(
                        "Bảo mật: Phát hiện khuôn mặt không hợp lệ. Embedding quá giống với embedding đã lưu. " +
                        "Vui lòng chụp lại khuôn mặt trực tiếp từ camera để chấm công.");
            }
            
            // Phát hiện trường hợp đáng nghi: similarity cao nhưng avgDiff quá thấp (0.002-0.01)
            // Đây có thể là dấu hiệu của embedding được modify để có similarity cao nhưng vẫn giống stored embedding
            if (similarityScore >= requiredThreshold && avgDiff >= 0.002 && avgDiff < 0.01) {
                log.warn("SUSPICIOUS: User {} has high similarity ({}) but very low avgDiff ({})! " +
                        "This may indicate modified embedding. Additional validation required.",
                        userId, String.format("%.4f", similarityScore), String.format("%.6f", avgDiff));
                // Vẫn cho qua nhưng log cảnh báo - có thể cần thêm validation sau
            }

            // Kiểm tra thêm: similarity quá cao (> 0.98) nhưng avgDiff quá thấp (< 0.01)
            // Đây là dấu hiệu embedding có thể đã được modify để có similarity cao
            // Khi check-in thật, similarity thường 0.85-0.95, không phải > 0.98
            if (similarityScore > 0.98 && avgDiff < 0.01) {
                log.error("CRITICAL SECURITY ALERT: User {} has suspiciously high similarity ({}) with very low avgDiff ({})! " +
                        "This pattern suggests modified embedding. Check-in BLOCKED.",
                        userId, String.format("%.4f", similarityScore), String.format("%.6f", avgDiff));
                throw new FaceVerificationFailedException(
                        "Bảo mật: Phát hiện embedding không hợp lệ. Độ tương đồng quá cao không tự nhiên. " +
                        "Vui lòng chụp lại khuôn mặt trực tiếp từ camera để chấm công.");
            }
            
            // Kiểm tra: similarity cao nhưng avgDiff quá cao (> 0.2) - đây là pattern bất thường
            // Khi similarity cao, avgDiff thường thấp (< 0.15)
            if (similarityScore >= requiredThreshold && avgDiff > 0.2) {
                log.error("SUSPICIOUS PATTERN: User {} has high similarity ({}) but very high avgDiff ({})! " +
                        "This may indicate model issue or tampered embedding. Check-in BLOCKED.",
                        userId, String.format("%.4f", similarityScore), String.format("%.6f", avgDiff));
                throw new FaceVerificationFailedException(
                        "Bảo mật: Phát hiện pattern bất thường trong embedding. Vui lòng chụp lại khuôn mặt trực tiếp từ camera.");
            }

            // BẢO MẬT: Validate cả hai: isVerified và similarityScore
            // Đảm bảo chỉ mặt đúng (có similarity >= 85%) mới được check-in
            // Đảm bảo chỉ có người A quét đúng mặt A mới được check-in
            if (!faceResult.isVerified() || similarityScore < requiredThreshold) {
                log.error("SECURITY ALERT: Face verification FAILED for user {}: verified={}, similarity={} < {}. Check-in BLOCKED. " +
                        "This ensures only the correct registered face can check-in. User {} attempted check-in but face does not match registered face.",
                        userId, faceResult.isVerified(), String.format("%.4f", similarityScore), String.format("%.2f", requiredThreshold), userId);
                
                String errorMessage;
                // Xử lý lỗi theo từng mức độ tương đồng để người dùng hiểu rõ vấn đề
                if (similarityScore < 0.5) {
                    errorMessage = String.format(
                            "Bảo mật: Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). " +
                            "Đây không phải khuôn mặt đã đăng ký của bạn. Vui lòng sử dụng khuôn mặt của chính bạn để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else if (similarityScore < 0.7) {
                    errorMessage = String.format(
                            "Bảo mật: Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). " +
                            "Khuôn mặt này không phải của bạn. Vui lòng sử dụng khuôn mặt đã đăng ký để chấm công.",
                            similarityScore * 100, requiredThreshold * 100);
                } else {
                    errorMessage = String.format(
                            "Bảo mật: Khuôn mặt không khớp! Độ tương đồng: %.1f%% (yêu cầu: %.0f%%). " +
                            "Khuôn mặt này không khớp với khuôn mặt đã đăng ký của bạn. " +
                            "Vui lòng đảm bảo ánh sáng tốt, nhìn thẳng vào camera và sử dụng khuôn mặt của chính bạn.",
                            similarityScore * 100, requiredThreshold * 100);
                }
                throw new FaceVerificationFailedException(errorMessage);
            }
            
            // Log thành công để audit
            log.info("SECURITY: Face verification PASSED for user {}. " +
                    "User {} successfully verified their own face (similarity: {}). Check-in allowed.",
                    userId, userId, String.format("%.4f", similarityScore));

            // Cảnh báo nếu pass nhưng similarity ở mức trung bình (85-90%)
            if (similarityScore >= requiredThreshold && similarityScore < 0.90) {
                log.warn("Face verification PASSED for user {} with moderate similarity: {}. " +
                        "Please ensure you are using the correct registered face and good lighting conditions.",
                        userId, String.format("%.4f", similarityScore));
            }
            // Log thông tin khi similarity rất cao (>= 90%) - đây là match chắc chắn
            if (similarityScore >= 0.90) {
                log.info("Face verification PASSED for user {} with very high similarity: {} (>= 0.90). Strong match confirmed - correct face verified.",
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

    // Tính average difference giữa hai embedding để phát hiện replay attack
    // Nếu avgDiff quá thấp, có thể là embedding giống hệt (replay attack)
    // Đồng thời validate norm của cả hai embedding để phát hiện embedding bị modify
    private double calculateAverageDifference(String embeddingJson1, String embeddingJson2) {
        try {
            float[] emb1 = parseEmbeddingJson(embeddingJson1);
            float[] emb2 = parseEmbeddingJson(embeddingJson2);
            
            if (emb1.length != emb2.length) {
                log.warn("Embedding dimensions mismatch: {} vs {}", emb1.length, emb2.length);
                return Double.MAX_VALUE; // Return high value to indicate mismatch
            }
            
            // Validate norm của cả hai embedding
            // Embedding từ ArcFace model sau khi L2 normalize sẽ có norm ≈ 1.0
            double norm1 = calculateNorm(emb1);
            double norm2 = calculateNorm(emb2);
            
            // Norm quá khác 1.0 có thể là dấu hiệu embedding bị modify
            if (Math.abs(norm1 - 1.0) > 0.5 || Math.abs(norm2 - 1.0) > 0.5) {
                log.warn("Suspicious embedding norms: input={}, stored={}. Expected ~1.0 for normalized embeddings.",
                        String.format("%.4f", norm1), String.format("%.4f", norm2));
            }
            
            double diffSum = 0.0;
            for (int i = 0; i < emb1.length; i++) {
                diffSum += Math.abs(emb1[i] - emb2[i]);
            }
            return diffSum / emb1.length;
        } catch (Exception e) {
            log.error("Error calculating average difference: {}", e.getMessage());
            // Return high value to be safe (fail secure)
            return Double.MAX_VALUE;
        }
    }
    
    // Tính norm (L2 norm) của embedding vector
    private double calculateNorm(float[] embedding) {
        double sumSquared = 0.0;
        for (float value : embedding) {
            sumSquared += value * value;
        }
        return Math.sqrt(sumSquared);
    }

    // Parse JSON array sang float[] và validate embedding
    private float[] parseEmbeddingJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding JSON is null or empty");
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Embedding must be a JSON array format: [0.123, 0.456, ...]");
        }
        try {
            float[] embedding = objectMapper.readValue(json, float[].class);
            if (embedding.length != 512) {
                throw new IllegalArgumentException(
                        String.format("Invalid embedding dimensions. Expected 512, got %d", embedding.length));
            }
            
            // Validate embedding values: kiểm tra NaN, Infinity, và range bất thường
            // Embedding từ ArcFace model thường có giá trị trong range [-1, 1] sau khi normalize
            boolean hasNaN = false;
            boolean hasInfinity = false;
            boolean hasOutOfRange = false;
            double sumSquared = 0.0;
            int zeroCount = 0;
            
            for (float value : embedding) {
                if (Float.isNaN(value)) {
                    hasNaN = true;
                } else if (Float.isInfinite(value)) {
                    hasInfinity = true;
                } else if (Math.abs(value) > 10.0) {
                    // Giá trị quá lớn (> 10) là bất thường cho embedding đã normalize
                    hasOutOfRange = true;
                } else if (Math.abs(value) < 1e-6) {
                    zeroCount++;
                } else {
                    sumSquared += value * value;
                }
            }
            
            if (hasNaN) {
                throw new IllegalArgumentException("Invalid embedding: contains NaN values");
            }
            if (hasInfinity) {
                throw new IllegalArgumentException("Invalid embedding: contains Infinity values");
            }
            if (hasOutOfRange) {
                log.warn("Embedding contains values out of normal range (> 10). This may indicate tampering.");
            }
            
            // Kiểm tra norm: embedding đã normalize nên norm phải gần 1.0
            double norm = Math.sqrt(sumSquared);
            if (norm < 0.1) {
                throw new IllegalArgumentException(
                        String.format("Invalid embedding: norm too low (%.4f). Embedding may be all zeros or invalid.", norm));
            }
            if (norm > 10.0) {
                log.warn("Embedding norm (%.4f) is unusually high. This may indicate tampering or invalid embedding.", norm);
            }
            
            // Kiểm tra quá nhiều giá trị 0 (> 50% là 0) - đây là dấu hiệu embedding không hợp lệ
            if (zeroCount > embedding.length / 2) {
                throw new IllegalArgumentException(
                        String.format("Invalid embedding: too many zero values (%d/512). Embedding appears to be invalid.", zeroCount));
            }
            
            return embedding;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid embedding JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse embedding: " + e.getMessage(), e);
        }
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
