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
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import com.google.gson.Gson;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class để xử lý ArcFace ONNX model
 * Extract face embedding từ ảnh sử dụng ONNX Runtime và OpenCV
 */
@Slf4j
public class ArcFaceOnnx {

    private OrtEnvironment env;
    private OrtSession session;
    
    private static final int INPUT_WIDTH = 112;
    private static final int INPUT_HEIGHT = 112;
    private static final int CHANNELS = 3;
    
    // Load OpenCV native libraries
    static {
        try {
            Loader.load(opencv_core.class);
            Loader.load(opencv_imgcodecs.class);
            Loader.load(opencv_imgproc.class);
            log.info("OpenCV native libraries loaded successfully");
        } catch (Exception e) {
            log.warn("Failed to load OpenCV native libraries: {}", e.getMessage());
        }
    }

    public ArcFaceOnnx(String modelPath) throws OrtException, IOException {
        log.info("Initializing ArcFace ONNX model from: {}", modelPath);
        
        // Check if model file exists
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new FileNotFoundException("ONNX model file not found: " + modelPath);
        }
        
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        // opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL); // optional
        session = env.createSession(modelPath, opts);
        
        log.info("ArcFace ONNX model loaded successfully");
    }

    /**
     * Extract face embedding từ ảnh
     * @param imagePath Đường dẫn đến file ảnh
     * @return float[] embedding (512 dimensions, L2-normalized)
     * @throws Exception nếu có lỗi
     */
    public float[] getEmbeddingFromImagePath(String imagePath) throws Exception {
        // Read image with OpenCV
        Mat img = opencv_imgcodecs.imread(imagePath, opencv_imgcodecs.IMREAD_COLOR);
        if (img == null || img.empty()) {
            throw new IOException("Cannot read image: " + imagePath);
        }

        try {
            // If image contains full photo, you should detect & crop face here.
            // For demo assume input image already tightly cropped face.
            Mat resized = new Mat();
            opencv_imgproc.resize(img, resized, new Size(INPUT_WIDTH, INPUT_HEIGHT), 0, 0, opencv_imgproc.INTER_LINEAR);

            try {
                // Convert BGR (OpenCV default) to RGB
                opencv_imgproc.cvtColor(resized, resized, opencv_imgproc.COLOR_BGR2RGB);

                // Convert Mat to float array in NHWC (1,112,112,3); normalize to [-1,1] using (pixel - 127.5)/127.5
                float[] inputData = matToFloatArray(resized);

                // Create tensor: shape [1,112,112,3]
                OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), 
                    new long[]{1, INPUT_HEIGHT, INPUT_WIDTH, CHANNELS});

                // Run session
                try (OrtSession.Result result = session.run(
                        Collections.singletonMap(session.getInputNames().iterator().next(), tensor))) {
                    
                    // Typically output name could be "fc1" or similar; get first output
                    OnnxValue v = result.get(0);
                    float[][] outArr = (float[][]) v.getValue(); // shape [1,512]
                    float[] embedding = outArr[0];

                    // L2 normalize
                    l2Normalize(embedding);

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

    /**
     * Convert Mat to float array in NHWC format
     * Normalize: (pixel - 127.5) / 127.5 -> range [-1,1]
     */
    private static float[] matToFloatArray(Mat mat) {
        // mat is H x W x C (RGB)
        int h = mat.rows();
        int w = mat.cols();
        int c = mat.channels();

        // get raw bytes as unsigned
        byte[] data = new byte[h * w * c];
        mat.data().get(data);

        // Convert to floats NHWC order
        float[] out = new float[h * w * c];

        int idx = 0;
        // OpenCV stores row-major RGB packed (interleaved) after cvtColor
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int base = (row * w + col) * c;

                // values as unsigned
                int r = data[base] & 0xFF;
                int g = data[base + 1] & 0xFF;
                int b = data[base + 2] & 0xFF;

                // normalize: (pixel - 127.5) / 127.5 -> range [-1,1]
                out[idx++] = (r - 127.5f) / 127.5f;
                out[idx++] = (g - 127.5f) / 127.5f;
                out[idx++] = (b - 127.5f) / 127.5f;
            }
        }

        return out;
    }

    /**
     * L2-normalize embedding vector
     */
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

    /**
     * Close resources
     */
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (env != null) {
            env.close();
        }
        log.info("ArcFace ONNX resources closed");
    }

    /**
     * Utility: convert float[] to JSON string
     */
    public static String embeddingToJson(float[] emb) {
        return new Gson().toJson(emb);
    }

    /**
     * Utility: convert float[] to byte[] (for BLOB storage)
     */
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

    /**
     * Utility: convert bytes back to float[]
     */
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

