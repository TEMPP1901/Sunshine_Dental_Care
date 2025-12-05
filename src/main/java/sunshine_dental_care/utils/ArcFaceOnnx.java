package sunshine_dental_care.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_objdetect;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;

import com.google.gson.Gson;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ArcFaceOnnx {

    private OrtEnvironment env;
    private OrtSession session;
    
    // Face detector flag (using feature-based detection)
    // Tạm thời tắt edge detection vì có thể crop sai vùng
    // Sử dụng smart center crop thay thế (hoạt động tốt hơn với ảnh từ mobile)
    private boolean useFeatureDetection = false;

    private static final int INPUT_WIDTH = 112;
    private static final int INPUT_HEIGHT = 112;
    private static final int CHANNELS = 3;

    // static initializer: load OpenCV native libs
    static {
        try {
            Loader.load(opencv_core.class);
            Loader.load(opencv_imgcodecs.class);
            Loader.load(opencv_imgproc.class);
            Loader.load(opencv_objdetect.class);
            log.info("OpenCV native libraries loaded successfully");
        } catch (Exception e) {
            log.warn("Failed to load OpenCV native libraries: {}", e.getMessage());
        }
    }

    public ArcFaceOnnx(String modelPath) throws OrtException, IOException {
        log.info("Initializing ArcFace ONNX model from: {}", modelPath);

        // Kiểm tra file mô hình có tồn tại không
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new FileNotFoundException("ONNX model file not found: " + modelPath);
        }

        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        session = env.createSession(modelPath, opts);

        // Verify model file
        log.info("ArcFace Model Verification");
        log.info("Model file path: {}", modelPath);
        log.info("Model file exists: {}", modelFile.exists());
        log.info("Model file size: {} bytes ({} MB)", 
                modelFile.length(), String.format("%.2f", modelFile.length() / (1024.0 * 1024.0)));
        
        // Get model input/output names
        try {
            var inputNames = session.getInputNames();
            var outputNames = session.getOutputNames();
            
            log.info("Model input names: {}", inputNames);
            log.info("Model output names: {}", outputNames);
        } catch (Exception e) {
            log.warn("Could not retrieve model input/output names: {}", e.getMessage());
        }
        
        // Initialize face detector (using feature-based detection with edge detection)
        log.info("Face detector: Using feature-based detection (edge detection + feature density)");
        
        log.info("ArcFace ONNX model loaded successfully.");
        log.info("Expected input shape: {}x{}x{} (Height x Width x Channels)", INPUT_HEIGHT, INPUT_WIDTH, CHANNELS);
        log.info("Expected output: 512-dimensional embedding vector (L2 normalized)");
        log.info("=== Model Verification Complete ===");
    }

    // Trích xuất face embedding từ đường dẫn ảnh, trả về float[512] đã normalize
    public float[] getEmbeddingFromImagePath(String imagePath) throws Exception {
        Mat img = opencv_imgcodecs.imread(imagePath, opencv_imgcodecs.IMREAD_COLOR);
        if (img == null || img.empty()) {
            throw new IOException("Cannot read image: " + imagePath);
        }

        try {
            // Log thông tin ảnh để debug
            int originalWidth = img.cols();
            int originalHeight = img.rows();
            log.info("Image loaded: {}x{} pixels from {}", originalWidth, originalHeight, imagePath);
            
            // Detect và crop face (sử dụng smart center crop)
            Mat faceCrop = detectAndCropFace(img);
            
            // Resize face crop về kích thước model yêu cầu
            Mat resized = new Mat();
            opencv_imgproc.resize(faceCrop, resized, new Size(INPUT_WIDTH, INPUT_HEIGHT), 0, 0, opencv_imgproc.INTER_LINEAR);
            
            log.info("Face crop resized to {}x{} for model input", INPUT_WIDTH, INPUT_HEIGHT);
            
            // Cleanup faceCrop
            if (faceCrop != null) {
                faceCrop.close();
            }

            try {
                opencv_imgproc.cvtColor(resized, resized, opencv_imgproc.COLOR_BGR2RGB);

                float[] inputData = matToFloatArray(resized);

                OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData),
                        new long[]{1, INPUT_HEIGHT, INPUT_WIDTH, CHANNELS});

                try (OrtSession.Result result = session.run(
                        Collections.singletonMap(session.getInputNames().iterator().next(), tensor))) {

                    OnnxValue v = result.get(0);
                    float[][] outArr = (float[][]) v.getValue();
                    
                    // Verify output shape
                    if (outArr == null || outArr.length == 0) {
                        throw new IllegalStateException("Model returned empty output");
                    }
                    
                    float[] embedding = outArr[0];
                    
                    // Verify embedding dimensions
                    if (embedding.length != 512) {
                        log.error("CRITICAL: Model output dimension is {} but expected 512! This may be the wrong model.", embedding.length);
                        throw new IllegalStateException(
                            String.format("Invalid embedding dimension: expected 512, got %d. This may not be an ArcFace model.", embedding.length));
                    }
                    
                    log.debug("Model output: {} dimensions, sample values: [{}, {}, {}, {}, {}]", 
                            embedding.length, embedding[0], embedding[1], embedding[2], embedding[3], embedding[4]);

                    l2Normalize(embedding); // Chuẩn hóa vector
                    
                    // Verify normalization
                    double norm = 0.0;
                    for (float val : embedding) {
                        norm += val * val;
                    }
                    norm = Math.sqrt(norm);
                    log.debug("Embedding norm after L2 normalization: {} (should be ~1.0)", String.format("%.4f", norm));
                    
                    if (Math.abs(norm - 1.0) > 0.01) {
                        log.warn("WARNING: Embedding norm is {} (expected ~1.0). Normalization may have failed.", String.format("%.4f", norm));
                    }

                    return embedding;
                } finally {
                    tensor.close();
                }
            } finally {
                resized.close();
            }
        } finally {
            img.close();
        }
    }

    // Chuyển Mat ảnh (RGB) thành mảng float chuẩn hóa [-1,1], thứ tự NHWC
    private static float[] matToFloatArray(Mat mat) {
        int h = mat.rows();
        int w = mat.cols();
        int c = mat.channels();

        byte[] data = new byte[h * w * c];
        mat.data().get(data);

        float[] out = new float[h * w * c];
        int idx = 0;

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int base = (row * w + col) * c;
                int r = data[base] & 0xFF;
                int g = data[base + 1] & 0xFF;
                int b = data[base + 2] & 0xFF;

                out[idx++] = (r - 127.5f) / 127.5f;
                out[idx++] = (g - 127.5f) / 127.5f;
                out[idx++] = (b - 127.5f) / 127.5f;
            }
        }

        return out;
    }

    // Detect và crop face từ ảnh sử dụng feature-based detection
    // Fallback về center crop nếu không detect được face
    private Mat detectAndCropFace(Mat img) {
        // Thử detect face bằng feature-based detection (edge detection)
        if (useFeatureDetection) {
            try {
                Mat faceCrop = detectFaceWithCascade(img);
                if (faceCrop != null && !faceCrop.empty()) {
                    return faceCrop;
                }
            } catch (Exception e) {
                log.debug("Feature-based face detection failed: {}. Falling back to center crop.", e.getMessage());
            }
        }
        
        // Fallback: sử dụng center crop thông minh
        return centerCropImage(img);
    }
    
    // Detect face sử dụng OpenCV để tìm vùng có nhiều features (edge detection + feature detection)
    // Đây là một heuristic đơn giản để tìm vùng có thể chứa face
    // Sử dụng Canny edge detection để tìm vùng có nhiều edges (thường là face)
    private Mat detectFaceWithCascade(Mat img) {
        try {
            int imgWidth = img.cols();
            int imgHeight = img.rows();
            
            // Convert to grayscale
            Mat gray = new Mat();
            if (img.channels() == 3) {
                opencv_imgproc.cvtColor(img, gray, opencv_imgproc.COLOR_BGR2GRAY);
            } else {
                gray = img.clone();
            }
            
            try {
                // Use Canny edge detection to find regions with many edges (likely face)
                Mat edges = new Mat();
                try {
                    opencv_imgproc.Canny(gray, edges, 50, 150);
                    
                    // Find region with most edges in upper-center part (where face usually is)
                    int searchWidth = Math.min(imgWidth, imgHeight);
                    int searchHeight = (int) (imgHeight * 0.6); // Focus on upper 60% of image
                    int startX = (imgWidth - searchWidth) / 2;
                    int startY = (int) (imgHeight * 0.1); // Start from 10% from top
                    
                    // Find region with maximum edge density
                    int bestX = startX;
                    int bestY = startY;
                    double maxDensity = 0.0;
                    int windowSize = Math.min(searchWidth, searchHeight);
                    
                    // Slide window to find best region
                    for (int y = startY; y <= startY + searchHeight - windowSize; y += windowSize / 4) {
                        for (int x = startX; x <= startX + searchWidth - windowSize; x += windowSize / 4) {
                            Rect window = new Rect(x, y, windowSize, windowSize);
                            Mat roi = new Mat(edges, window);
                            try {
                                // Count non-zero pixels (edges)
                                int edgeCount = opencv_core.countNonZero(roi);
                                double density = (double) edgeCount / (windowSize * windowSize);
                                
                                if (density > maxDensity) {
                                    maxDensity = density;
                                    bestX = x;
                                    bestY = y;
                                }
                            } finally {
                                roi.close();
                            }
                        }
                    }
                    
                    // If we found a good region, crop it
                    if (maxDensity > 0.05) { // At least 5% edge density
                        Rect faceRect = new Rect(bestX, bestY, windowSize, windowSize);
                        Mat faceCrop = new Mat(img, faceRect);
                        Mat result = faceCrop.clone();
                        faceCrop.close();
                        
                        log.info("Face region detected using edge detection: density={:.2f}, crop={}x{} from position ({}, {})", 
                                String.format("%.2f", maxDensity), windowSize, windowSize, bestX, bestY);
                        return result;
                    }
                } finally {
                    edges.close();
                }
            } finally {
                if (gray != img) {
                    gray.close();
                }
            }
        } catch (Exception e) {
            log.warn("Error in feature-based face detection: {}", e.getMessage());
        }
        
        return null;
    }
    
    // Crop center của ảnh với logic thông minh
    // Cải thiện để crop chính xác hơn vùng face
    private Mat centerCropImage(Mat img) {
        int width = img.cols();
        int height = img.rows();
        
        // Với ảnh từ mobile camera, face thường ở:
        // - Portrait: center-upper (từ 5% đến 45% từ trên xuống)
        // - Landscape: center (50% từ trên xuống)
        // - Square: center-upper (từ 10% đến 40% từ trên xuống)
        
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y;
        
        if (height > width) {
            // Portrait (chiều cao > chiều rộng): face ở upper-center
            // Crop từ 15% từ trên xuống (để bao gồm cả đầu và một phần cổ)
            y = (int) (height * 0.15);
            // Đảm bảo không vượt quá bounds
            if (y + size > height) {
                y = height - size;
            }
            if (y < 0) {
                y = 0;
            }
            log.debug("Portrait image: cropping from {}% from top", 15);
        } else if (width > height) {
            // Landscape: face ở center
            y = (height - size) / 2;
            log.debug("Landscape image: cropping center");
        } else {
            // Square: face ở upper-center
            y = (int) (height * 0.15);
            if (y + size > height) {
                y = height - size;
            }
            if (y < 0) {
                y = 0;
            }
            log.debug("Square image: cropping from {}% from top", 15);
        }
        
        Rect cropRect = new Rect(x, y, size, size);
        Mat cropped = new Mat(img, cropRect);
        Mat result = cropped.clone();
        cropped.close();
        
        log.info("Smart center crop: {}x{} from position ({}, {}) in original {}x{} image ({}% from top)", 
                size, size, x, y, width, height, String.format("%.1f", (y * 100.0 / height)));
        return result;
    }

    // Chuẩn hóa L2 cho vector embedding
    private static void l2Normalize(float[] x) {
        double sum = 0.0;
        for (float v : x) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum) + 1e-10;
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (x[i] / norm);
        }
    }

    // Đóng tài nguyên ONNX, giải phóng
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (env != null) {
            env.close();
        }
        log.info("ArcFace ONNX resources closed");
    }

    // Chuyển float[] embedding sang JSON string (dạng lưu phổ biến)
    public static String embeddingToJson(float[] emb) {
        return new Gson().toJson(emb);
    }

    // Chuyển float[] thành byte[] để lưu dạng BLOB nhị phân
    public static byte[] floatArrayToBytes(float[] arr) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            for (float f : arr) {
                dos.writeFloat(f);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bos.toByteArray();
    }

    // Chuyển ngược byte[] về float[]
    public static float[] bytesToFloatArray(byte[] bytes) {
        float[] arr = new float[bytes.length / 4];
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            for (int i = 0; i < arr.length; i++) {
                arr[i] = dis.readFloat();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return arr;
    }
}
