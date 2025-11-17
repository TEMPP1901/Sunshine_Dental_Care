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
 * Utility class for ArcFace ONNX model: extract face embeddings from images using ONNX Runtime and OpenCV
 */
@Slf4j
public class ArcFaceOnnx {

    private OrtEnvironment env;
    private OrtSession session;

    private static final int INPUT_WIDTH = 112;
    private static final int INPUT_HEIGHT = 112;
    private static final int CHANNELS = 3;

    // static initializer: load OpenCV native libs
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

        // Kiểm tra file mô hình có tồn tại không
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new FileNotFoundException("ONNX model file not found: " + modelPath);
        }

        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        session = env.createSession(modelPath, opts);

        log.info("ArcFace ONNX model loaded successfully");
    }

    // Trích xuất face embedding từ đường dẫn ảnh, trả về float[512] đã normalize
    public float[] getEmbeddingFromImagePath(String imagePath) throws Exception {
        Mat img = opencv_imgcodecs.imread(imagePath, opencv_imgcodecs.IMREAD_COLOR);
        if (img == null || img.empty()) {
            throw new IOException("Cannot read image: " + imagePath);
        }

        try {
            // Nếu là ảnh full body phải detect/crop, demo này coi như đã crop face rồi
            Mat resized = new Mat();
            opencv_imgproc.resize(img, resized, new Size(INPUT_WIDTH, INPUT_HEIGHT), 0, 0, opencv_imgproc.INTER_LINEAR);

            try {
                opencv_imgproc.cvtColor(resized, resized, opencv_imgproc.COLOR_BGR2RGB);

                float[] inputData = matToFloatArray(resized);

                OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData),
                        new long[]{1, INPUT_HEIGHT, INPUT_WIDTH, CHANNELS});

                try (OrtSession.Result result = session.run(
                        Collections.singletonMap(session.getInputNames().iterator().next(), tensor))) {

                    OnnxValue v = result.get(0);
                    float[][] outArr = (float[][]) v.getValue();
                    float[] embedding = outArr[0];

                    l2Normalize(embedding); // Chuẩn hóa vector

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
