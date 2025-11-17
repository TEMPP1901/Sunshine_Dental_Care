package sunshine_dental_care.utils.huybro_utils.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ImageCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageCacheUtil.class);

    public static File getOrCreateResized(String originalDir, String fileName,
                                          String cacheDir, int width, int height, String format) throws IOException {
        File originalFile = new File(originalDir, fileName);
        if (!originalFile.exists()) throw new IOException("Not found: " + originalFile.getAbsolutePath());

        log.info("► Bắt đầu resize ảnh: {}", fileName);

        File cacheDirectory = new File(cacheDir);
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
        File cachedFile = new File(cacheDirectory, fileName);

        boolean needCreate = !cachedFile.exists() || originalFile.lastModified() > cachedFile.lastModified();

        if (!needCreate) {
            log.info("✔ Ảnh đã có trong cache và còn mới: {}", cachedFile.getAbsolutePath());
            return cachedFile;
        }

        try {
            log.info("… Đang đọc ảnh gốc từ: {}", originalFile.getAbsolutePath());
            log.info("… Yêu cầu tối thiểu để resize: {}x{}", width, height);

            byte[] resized = ImageResizeUtil.resizeToFit(originalFile, width, height, format);

            Files.write(cachedFile.toPath(), resized);
            cachedFile.setLastModified(System.currentTimeMillis());

            log.info("✔ Đã resize và lưu cache: {}", cachedFile.getAbsolutePath());
            return cachedFile;
        } catch (IOException ex) {
            log.warn("✖ Bỏ qua resize cho ảnh {}. Lý do: {}", fileName, ex.getMessage());
            throw ex;
        }
    }
}
