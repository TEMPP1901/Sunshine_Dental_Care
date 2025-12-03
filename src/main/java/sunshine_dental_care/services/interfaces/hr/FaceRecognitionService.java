package sunshine_dental_care.services.interfaces.hr;

import org.springframework.web.multipart.MultipartFile;

// Interface for face recognition using ArcFace model
public interface FaceRecognitionService {

    // Trích xuất embedding từ ảnh truyền lên (tĩnh)
    String extractEmbedding(MultipartFile imageFile) throws Exception;

    // Trích xuất embedding từ ảnh đã lưu trên đĩa
    String extractEmbeddingFromPath(String imagePath) throws Exception;

    // Xác thực khuôn mặt: so sánh embedding đầu vào với embedding đã lưu trong CSDL
    FaceVerificationResult verifyFace(String inputEmbeddingJson, String storedEmbeddingJson) throws Exception;

    // Kết quả xác thực khuôn mặt
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
