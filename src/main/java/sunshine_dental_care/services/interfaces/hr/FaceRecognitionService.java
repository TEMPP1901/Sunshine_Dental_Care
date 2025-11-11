package sunshine_dental_care.services.interfaces.hr;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface để xử lý face recognition với ArcFace model
 */
public interface FaceRecognitionService {
    
    /**
     * Extract embedding từ ảnh tĩnh (static image)
     * @param imageFile Ảnh từ MultipartFile
     * @return JSON string chứa embedding array (512 dimensions): "[0.123, 0.456, ...]"
     * @throws Exception nếu không thể extract (ảnh không có face, lỗi model, ...)
     */
    String extractEmbedding(MultipartFile imageFile) throws Exception;
    
    /**
     * Extract embedding từ ảnh đã lưu trên disk
     * @param imagePath Đường dẫn đến file ảnh
     * @return JSON string chứa embedding array
     * @throws Exception nếu không thể extract
     */
    String extractEmbeddingFromPath(String imagePath) throws Exception;
    
    /**
     * Verify face: So sánh embedding từ mobile với embedding trong database
     * @param inputEmbeddingJson Embedding từ mobile (JSON string)
     * @param storedEmbeddingJson Embedding từ EmployeeFaceProfile (JSON string)
     * @return FaceVerificationResult chứa similarity score và verified status
     * @throws Exception nếu không thể verify
     */
    FaceVerificationResult verifyFace(String inputEmbeddingJson, String storedEmbeddingJson) throws Exception;
    
    /**
     * Result class cho face verification
     */
    class FaceVerificationResult {
        private final boolean verified;
        private final double similarityScore;
        private final String message;
        
        public FaceVerificationResult(boolean verified, double similarityScore, String message) {
            this.verified = verified;
            this.similarityScore = similarityScore;
            this.message = message;
        }
        
        public boolean isVerified() {
            return verified;
        }
        
        public double getSimilarityScore() {
            return similarityScore;
        }
        
        public String getMessage() {
            return message;
        }
    }
}

