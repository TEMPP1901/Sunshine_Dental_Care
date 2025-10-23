package sunshine_dental_care.utils.huybro_utils.image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ImageCacheUtil {

    public static File getOrCreateResized(String originalDir, String fileName,
                                          String cacheDir, int width, int height, String format) throws IOException {
        File originalFile = new File(originalDir, fileName);
        if (!originalFile.exists()) throw new IOException("Not found: " + originalFile.getAbsolutePath());

        File cacheDirectory = new File(cacheDir);
        if (!cacheDirectory.exists()) cacheDirectory.mkdirs();
        File cachedFile = new File(cacheDirectory, fileName);

        boolean needCreate = !cachedFile.exists() || originalFile.lastModified() > cachedFile.lastModified();
        if (needCreate) {
            byte[] resized = ImageResizeUtil.resizeToFit(originalFile, width, height, format);
            Files.write(cachedFile.toPath(), resized);
            cachedFile.setLastModified(System.currentTimeMillis());
        }
        return cachedFile;
    }
}
