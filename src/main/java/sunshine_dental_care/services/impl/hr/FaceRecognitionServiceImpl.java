package sunshine_dental_care.services.impl.hr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;

/**
 * Implementation của FaceRecognitionService
 * Xử lý ArcFace .onnx model để extract và verify face embeddings
 * 
 * LƯU Ý: Hiện tại implementation này là STUB (mock)
 * Cần tích hợp ONNX Runtime để load và chạy ArcFace model thực tế
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    
    // TODO: Load ONNX model từ modelPath khi app start
    // @Value("${app.face-recognition.model-path:models/arcface.onnx}")
    // private String modelPath;
    
    @Value("${app.face-recognition.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // TODO: Load ONNX model khi app start
    // private OrtEnvironment env;
    // private OrtSession session;
    
    @Override
    public String extractEmbedding(MultipartFile imageFile) throws Exception {
        log.info("Extracting face embedding from image file: {}", imageFile.getOriginalFilename());
        
        // Validate file
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }
        
        // Validate image type
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File is not an image");
        }
        
        // Save temporary file để xử lý
        Path tempFile = Files.createTempFile("face_", ".jpg");
        try {
            // Copy file content (MultipartFile stream chỉ đọc được 1 lần)
            Files.copy(imageFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Extract embedding từ ảnh đã lưu
            String embedding = extractEmbeddingFromPath(tempFile.toString());
            
            // Validate embedding format
            validateEmbeddingFormat(embedding);
            
            return embedding;
        } finally {
            // Cleanup temp file
            Files.deleteIfExists(tempFile);
        }
    }
    
    @Override
    public String extractEmbeddingFromPath(String imagePath) throws Exception {
        log.info("Extracting face embedding from image path: {}", imagePath);
        
        File imageFile = new File(imagePath);
        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IOException("Image file not found: " + imagePath);
        }
        
        // TODO: Implement actual ArcFace inference
        // 1. Load image với OpenCV hoặc Java ImageIO
        // 2. Preprocess: Resize to 112x112, normalize, convert to tensor
        // 3. Run ONNX model inference
        // 4. Get output (512-dim embedding)
        // 5. Convert to JSON string
        
        // STUB: Tạm thời return mock embedding
        // Trong thực tế, cần:
        // - Load ONNX model từ modelPath
        // - Preprocess ảnh (detect face, align, resize to 112x112)
        // - Run inference: session.run(inputTensor)
        // - Extract output[0] (512 float array)
        // - Convert to JSON
        
        log.warn("Face embedding extraction is using STUB implementation. Need to integrate ONNX Runtime.");
        
        // Mock: Generate random embedding for testing
        float[] mockEmbedding = generateMockEmbedding(512);
        String embeddingJson = objectMapper.writeValueAsString(mockEmbedding);
        
        // Validate format
        validateEmbeddingFormat(embeddingJson);
        
        return embeddingJson;
    }
    
    @Override
    public FaceVerificationResult verifyFace(String inputEmbeddingJson, String storedEmbeddingJson) throws Exception {
        log.debug("Verifying face: comparing embeddings");
        
        if (inputEmbeddingJson == null || inputEmbeddingJson.trim().isEmpty()) {
            return new FaceVerificationResult(false, 0.0, "Input embedding is empty");
        }
        
        if (storedEmbeddingJson == null || storedEmbeddingJson.trim().isEmpty()) {
            return new FaceVerificationResult(false, 0.0, "Stored embedding is empty");
        }
        
        // Parse JSON strings thành float arrays
        float[] inputEmbedding = parseEmbeddingJson(inputEmbeddingJson);
        float[] storedEmbedding = parseEmbeddingJson(storedEmbeddingJson);
        
        // Validate dimensions (phải là 512 cho ArcFace)
        if (inputEmbedding.length != 512 || storedEmbedding.length != 512) {
            throw new IllegalArgumentException(
                String.format("Invalid embedding dimensions. Expected 512, got input=%d, stored=%d", 
                    inputEmbedding.length, storedEmbedding.length));
        }
        
        // Tính cosine similarity
        double similarity = calculateCosineSimilarity(inputEmbedding, storedEmbedding);
        
        // Verify (similarity >= threshold)
        boolean verified = similarity >= similarityThreshold;
        
        String message = verified 
            ? String.format("Face verified with similarity: %.4f", similarity)
            : String.format("Face verification failed. Similarity: %.4f (threshold: %.4f)", 
                similarity, similarityThreshold);
        
        log.info("Face verification result: verified={}, similarity={:.4f}", verified, similarity);
        
        return new FaceVerificationResult(verified, similarity, message);
    }
    
    /**
     * Validate embedding format (phải là JSON array)
     */
    private void validateEmbeddingFormat(String embeddingJson) throws IllegalArgumentException {
        if (embeddingJson == null || embeddingJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding is null or empty");
        }
        
        String trimmed = embeddingJson.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Embedding must be a JSON array format: [0.123, 0.456, ...]");
        }
    }
    
    /**
     * Parse JSON string thành float array
     */
    private float[] parseEmbeddingJson(String json) throws Exception {
        // Validate format trước khi parse
        validateEmbeddingFormat(json);
        
        try {
            float[] embedding = objectMapper.readValue(json, float[].class);
            
            // Validate dimensions (phải là 512 cho ArcFace)
            if (embedding.length != 512) {
                throw new IllegalArgumentException(
                    String.format("Invalid embedding dimensions. Expected 512, got %d", embedding.length));
            }
            
            return embedding;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid embedding JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse embedding: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tính cosine similarity giữa 2 embedding vectors
     */
    private double calculateCosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) {
            return 0.0;
        }
        
        return dotProduct / denominator;
    }
    
    /**
     * Generate mock embedding for testing (sẽ được thay thế bằng ONNX inference)
     */
    private float[] generateMockEmbedding(int dimensions) {
        float[] embedding = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = (float) (Math.random() * 2 - 1); // Random values between -1 and 1
        }
        // Normalize
        double norm = 0.0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) (embedding[i] / norm);
            }
        }
        return embedding;
    }
}

