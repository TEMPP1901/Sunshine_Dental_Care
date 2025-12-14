package sunshine_dental_care.services.impl.hr.face;

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
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;
import sunshine_dental_care.utils.ArcFaceOnnx;

@Service
@Slf4j
public class FaceRecognitionServiceImpl implements FaceRecognitionService {

    @Value("${app.face-recognition.model-path:models/arcface.onnx}")
    private String modelPath;

    // Tăng threshold mặc định lên 0.85 để đảm bảo chỉ mặt đúng mới pass
    // Threshold 0.70 quá thấp, có thể cho phép false positive
    @Value("${app.face-recognition.similarity-threshold:0.85}")
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
            log.info("=== ArcFace Model Initialization ===");
            log.info("Model path from config: {}", modelPath);
            log.info("Resolved model file path: {}", modelFilePath);
            log.info("Model file exists: {}", modelFilePath != null && java.nio.file.Files.exists(modelFilePath));
            if (modelFilePath != null && java.nio.file.Files.exists(modelFilePath)) {
                long fileSize = java.nio.file.Files.size(modelFilePath);
                log.info("Model file size: {} bytes ({} MB)", fileSize,
                        String.format("%.2f", fileSize / (1024.0 * 1024.0)));
            }
            log.info("Initializing ArcFace ONNX model from: {}", modelFilePath);
            arcFaceOnnx = new ArcFaceOnnx(modelFilePath.toString());
            log.info("=== ArcFace Model Initialization Complete ===");
        } catch (Exception e) {
            log.error("Failed to initialize ArcFace ONNX model: {}", e.getMessage(), e);
            // Chỉ cảnh báo khi model chưa được load
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
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File is not an image");
        }
        Path tempFile = Files.createTempFile("face_", ".jpg");
        try {
            Files.copy(imageFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            String embedding = extractEmbeddingFromPath(tempFile.toString());
            // Chỉ cần validate format JSON ở đây (extractEmbeddingFromPath đã validate
            // trước đó)
            validateEmbeddingFormat(embedding);
            log.info("Face embedding extracted and validated from uploaded file: {}", imageFile.getOriginalFilename());
            return embedding;
        } finally {
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
        if (arcFaceOnnx == null) {
            throw new IllegalStateException("ArcFace ONNX model not loaded. Please check model path: " + modelPath);
        }
        try {
            // Debug thông tin file ảnh và embedding đầu ra
            File imgFile = new File(imagePath);
            long fileSize = imgFile.length();
            log.info("Extracting embedding from image: {} (size: {} bytes)", imagePath, fileSize);
            float[] embedding = arcFaceOnnx.getEmbeddingFromImagePath(imagePath);
            validateEmbeddingArray(embedding, "extracted from image");
            log.debug("Embedding sample (first 5): [{}, {}, {}, {}, {}]",
                    embedding[0], embedding[1], embedding[2], embedding[3], embedding[4]);
            log.debug("Embedding sample (last 5): [{}, {}, {}, {}, {}]",
                    embedding[507], embedding[508], embedding[509], embedding[510], embedding[511]);
            String embeddingJson = ArcFaceOnnx.embeddingToJson(embedding);
            validateEmbeddingFormat(embeddingJson);
            log.info("Face embedding extracted successfully. Dimensions: {}, JSON length: {} chars, validated: OK",
                    embedding.length, embeddingJson.length());
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
        float[] inputEmbedding = parseEmbeddingJson(inputEmbeddingJson);
        float[] storedEmbedding = parseEmbeddingJson(storedEmbeddingJson);
        validateEmbeddingArray(inputEmbedding, "input embedding from check-in");
        validateEmbeddingArray(storedEmbedding, "stored embedding from registration");
        log.info("=== FACE VERIFICATION DEBUG ===");
        log.info("Input embedding sample (first 10): [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
                inputEmbedding[0], inputEmbedding[1], inputEmbedding[2], inputEmbedding[3], inputEmbedding[4],
                inputEmbedding[5], inputEmbedding[6], inputEmbedding[7], inputEmbedding[8], inputEmbedding[9]);
        log.info("Stored embedding sample (first 10): [{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]",
                storedEmbedding[0], storedEmbedding[1], storedEmbedding[2], storedEmbedding[3], storedEmbedding[4],
                storedEmbedding[5], storedEmbedding[6], storedEmbedding[7], storedEmbedding[8], storedEmbedding[9]);
        double inputNorm = 0.0;
        double storedNorm = 0.0;
        double diffSum = 0.0;
        int diffCount = 0;
        for (int i = 0; i < inputEmbedding.length; i++) {
            inputNorm += inputEmbedding[i] * inputEmbedding[i];
            storedNorm += storedEmbedding[i] * storedEmbedding[i];
            double diff = Math.abs(inputEmbedding[i] - storedEmbedding[i]);
            diffSum += diff;
            if (diff > 0.01) {
                diffCount++;
            }
        }
        inputNorm = Math.sqrt(inputNorm);
        storedNorm = Math.sqrt(storedNorm);
        double avgDiff = diffSum / inputEmbedding.length;
        log.info("Input embedding norm: {}, Stored embedding norm: {}",
                String.format("%.4f", inputNorm), String.format("%.4f", storedNorm));
        log.info("Average absolute difference: {}, Different values (>0.01): {}/512",
                String.format("%.4f", avgDiff), diffCount);
        if (avgDiff < 0.001) {
            log.error(
                    "WARNING: Embeddings are almost identical! Average diff: {}. This may indicate the same embedding is being compared.",
                    avgDiff);
        }
        double similarity = calculateCosineSimilarity(inputEmbedding, storedEmbedding);
        log.info("Calculated cosine similarity: {}", String.format("%.4f", similarity));
        if (Double.isNaN(similarity) || Double.isInfinite(similarity)) {
            log.error("Invalid similarity calculation result: {}", similarity);
            throw new IllegalArgumentException("Similarity calculation resulted in invalid value: " + similarity);
        }
        // Kiểm tra vùng giá trị similarity
        if (similarity < 0.0 || similarity > 1.0) {
            log.error("Invalid similarity score: {}. Expected range: 0.0 - 1.0", similarity);
            throw new IllegalArgumentException("Invalid similarity score: " + similarity);
        }
        // Cảnh báo nghi vấn khi similarity và avgDiff xung đột nhau
        if (similarity >= 0.85 && similarity < 0.95 && avgDiff > 0.15) {
            log.warn(
                    "Suspicious pattern: Similarity ({}) is high but average difference ({}) is also large. This may indicate model issue, but allowing verification to proceed.",
                    String.format("%.4f", similarity), String.format("%.4f", avgDiff));
        }
        if (similarity >= 0.80 && similarity < 0.95 && avgDiff < 0.03) {
            log.info(
                    "Similarity ({}) with avgDiff ({}) is within acceptable range for same person with different capture conditions.",
                    String.format("%.4f", similarity), String.format("%.4f", avgDiff));
        }
        if (similarity > 0.95 && avgDiff > 0.1) {
            log.error(
                    "SUSPICIOUS: Very high similarity ({}) but large average difference ({})! This may indicate model issue or invalid embeddings being compared.",
                    String.format("%.4f", similarity), String.format("%.4f", avgDiff));
        }
        if (similarity > 0.98 && avgDiff < 0.01) {
            log.warn("Very high similarity ({}) with very small difference ({})! Embeddings are nearly identical.",
                    String.format("%.4f", similarity), String.format("%.4f", avgDiff));
        }
        boolean verified = similarity >= similarityThreshold;
        String message = verified
                ? String.format("Face verified with similarity: %.4f", similarity)
                : String.format("Face verification failed. Similarity: %.4f (threshold: %.4f)", similarity,
                        similarityThreshold);
        log.info("Face verification result: verified={}, similarity={}, threshold={}, message={}",
                verified, String.format("%.4f", similarity), String.format("%.4f", similarityThreshold), message);
        // Cảnh báo các trường hợp similarity thấp hoặc khó xác thực
        if (similarity < 0.1) {
            log.warn(
                    "Very low similarity score ({}). This indicates a completely different person or invalid face data.",
                    String.format("%.4f", similarity));
        } else if (similarity >= 0.1 && similarity < 0.5) {
            log.warn("Low similarity score ({}). This is likely a different person, not just lighting/angle issues.",
                    String.format("%.4f", similarity));
        } else if (similarity >= 0.5 && similarity < similarityThreshold) {
            log.warn("Similarity score ({}) is below threshold ({}). Face does not match registered face.",
                    String.format("%.4f", similarity), String.format("%.4f", similarityThreshold));
        }
        return new FaceVerificationResult(verified, similarity, message);
    }

    // Kiểm tra định dạng embedding phải là JSON array
    private void validateEmbeddingFormat(String embeddingJson) throws IllegalArgumentException {
        if (embeddingJson == null || embeddingJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding is null or empty");
        }
        String trimmed = embeddingJson.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Embedding must be a JSON array format: [0.123, 0.456, ...]");
        }
    }

    // Validate embedding array: check dimensions, giá trị NaN/Infinity, toàn số 0
    private void validateEmbeddingArray(float[] embedding, String context) throws IllegalArgumentException {
        if (embedding == null) {
            throw new IllegalArgumentException("Embedding array is null (" + context + ")");
        }
        if (embedding.length != 512) {
            throw new IllegalArgumentException(
                    String.format("Invalid embedding dimensions (%s). Expected 512, got %d", context,
                            embedding.length));
        }
        boolean allZeros = true;
        boolean hasNaN = false;
        boolean hasInfinity = false;
        double sumSquared = 0.0;
        for (int i = 0; i < embedding.length; i++) {
            float value = embedding[i];
            if (Float.isNaN(value)) {
                hasNaN = true;
                allZeros = false;
            } else if (Float.isInfinite(value)) {
                hasInfinity = true;
                allZeros = false;
            } else if (Math.abs(value) > 1e-6) {
                allZeros = false;
                sumSquared += value * value;
            }
        }
        if (allZeros) {
            log.error(
                    "Invalid embedding (%s): array contains only zeros. This indicates no face was detected or extraction failed.",
                    context);
            throw new IllegalArgumentException(
                    "Embedding array contains only zeros (" + context + "). No face detected or extraction failed.");
        }
        if (hasNaN) {
            log.error("Invalid embedding (%s): array contains NaN values.", context);
            throw new IllegalArgumentException("Embedding array contains NaN values (" + context + ")");
        }
        if (hasInfinity) {
            log.error("Invalid embedding (%s): array contains Infinity values.", context);
            throw new IllegalArgumentException("Embedding array contains Infinity values (" + context + ")");
        }
        // Kiểm tra norm bất thường
        double norm = Math.sqrt(sumSquared);
        if (norm < 0.1) {
            log.warn("Embedding (%s) has very low norm: {}. This may indicate poor quality face detection.", context,
                    norm);
        }
        if (norm > 10.0) {
            log.warn("Embedding (%s) has very high norm: {}. This may indicate normalization issue.", context, norm);
        }
        log.debug("Embedding validation passed (%s): dimensions=512, norm={}, hasValidValues=true", context,
                String.format("%.4f", norm));
    }

    // Parse JSON array sang float[] và kiểm tra độ dài
    private float[] parseEmbeddingJson(String json) throws Exception {
        validateEmbeddingFormat(json);
        try {
            float[] embedding = objectMapper.readValue(json, float[].class);
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

    // Tính cosine similarity hai vector embedding
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

    // Tải model từ file hệ thống hoặc classpath, copy ra file tạm nếu cần
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
