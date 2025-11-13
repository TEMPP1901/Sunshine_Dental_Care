package sunshine_dental_care.services.impl.hr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.onnxruntime.OrtException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;
import sunshine_dental_care.utils.ArcFaceOnnx;

/**
 * Implementation của FaceRecognitionService
 * Xử lý ArcFace .onnx model để extract và verify face embeddings
 * Sử dụng ONNX Runtime và OpenCV
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    
    @Value("${app.face-recognition.model-path:models/arcface.onnx}")
    private String modelPath;
    
    @Value("${app.face-recognition.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private ArcFaceOnnx arcFaceOnnx;
    private Path modelFilePath;
    private boolean deleteModelOnCleanup = false;
    
    @PostConstruct
    public void init() {
        try {
            resolveModelFile();
            if (modelFilePath == null) {
                log.warn("ArcFace model path could not be resolved. Face recognition will not work.");
                return;
            }
            
            log.info("Initializing ArcFace ONNX model from: {}", modelFilePath);
            arcFaceOnnx = new ArcFaceOnnx(modelFilePath.toString());
            log.info("ArcFace ONNX model initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize ArcFace ONNX model: {}", e.getMessage(), e);
            // Không throw exception để app vẫn có thể start, nhưng face recognition sẽ fail
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            if (arcFaceOnnx != null) {
                arcFaceOnnx.close();
            }
            if (deleteModelOnCleanup && modelFilePath != null) {
                try {
                    Files.deleteIfExists(modelFilePath);
                    log.debug("Temporary ArcFace model file deleted: {}", modelFilePath);
                } catch (IOException ex) {
                    log.warn("Failed to delete temporary ArcFace model file {}: {}", modelFilePath, ex.getMessage());
                }
            }
            log.info("ArcFace ONNX resources cleaned up");
        } catch (Exception e) {
            log.error("Error cleaning up ArcFace ONNX: {}", e.getMessage(), e);
        }
    }
    
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
        
        // Check if ArcFace model is available
        if (arcFaceOnnx == null) {
            throw new IllegalStateException("ArcFace ONNX model not loaded. Please check model path: " + modelPath);
        }
        
        try {
            // Sử dụng ArcFaceOnnx để extract embedding
            float[] embedding = arcFaceOnnx.getEmbeddingFromImagePath(imagePath);
            
            // Convert to JSON string
            String embeddingJson = ArcFaceOnnx.embeddingToJson(embedding);
            
            // Validate format
            validateEmbeddingFormat(embeddingJson);
            
            log.info("Face embedding extracted successfully. Dimensions: {}", embedding.length);
            
            return embeddingJson;
            
        } catch (OrtException e) {
            log.error("ONNX Runtime error: {}", e.getMessage(), e);
            throw new Exception("Failed to run face recognition inference: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error extracting face embedding: {}", e.getMessage(), e);
            throw e;
        }
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
        
        // Log first few values for debugging
        log.debug("Input embedding sample (first 5): [{}, {}, {}, {}, {}]", 
                inputEmbedding[0], inputEmbedding[1], inputEmbedding[2], inputEmbedding[3], inputEmbedding[4]);
        log.debug("Stored embedding sample (first 5): [{}, {}, {}, {}, {}]", 
                storedEmbedding[0], storedEmbedding[1], storedEmbedding[2], storedEmbedding[3], storedEmbedding[4]);
        
        // Tính cosine similarity
        double similarity = calculateCosineSimilarity(inputEmbedding, storedEmbedding);
        
        // Verify (similarity >= threshold)
        boolean verified = similarity >= similarityThreshold;
        
        String message = verified 
            ? String.format("Face verified with similarity: %.4f", similarity)
            : String.format("Face verification failed. Similarity: %.4f (threshold: %.4f)", 
                similarity, similarityThreshold);
        
        // Log chi tiết để debug (đặc biệt khi chụp bằng laptop camera)
        log.info("Face verification result: verified={}, similarity={}, threshold={}, message={}", 
                verified, String.format("%.4f", similarity), String.format("%.4f", similarityThreshold), message);
        
        // Log warning nếu similarity quá thấp (có thể do điều kiện chụp khác nhau)
        if (similarity < 0.1) {
            log.warn("Very low similarity score ({}). Possible causes: different camera/lighting/angle, or different person.", 
                    String.format("%.4f", similarity));
        }
        
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
     * Resolve model file from configuration string.
     * Supports absolute/relative paths and classpath resources (classpath:models/arcface.onnx).
     */
    private void resolveModelFile() throws IOException {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            log.warn("app.face-recognition.model-path is empty");
            modelFilePath = null;
            return;
        }

        String trimmedPath = modelPath.trim();
        if (trimmedPath.startsWith("classpath:")) {
            String resourcePath = trimmedPath.substring("classpath:".length());
            Resource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                log.warn("ArcFace model resource not found on classpath: {}", resourcePath);
                modelFilePath = null;
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Path tempFile = Files.createTempFile("arcface-", ".onnx");
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                modelFilePath = tempFile;
                deleteModelOnCleanup = true;
                log.debug("ArcFace model loaded from classpath to temporary file: {}", modelFilePath);
            }
        } else {
            Path candidate = Path.of(trimmedPath).toAbsolutePath().normalize();
            if (!Files.exists(candidate)) {
                log.warn("ArcFace model file not found at path: {}", candidate);
                modelFilePath = null;
                return;
            }
            modelFilePath = candidate;
            deleteModelOnCleanup = false;
        }
    }
}
