package sunshine_dental_care.utils.huybro_utils.image;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ImageUploadUtil {

    private static final int MIN_WIDTH = 1000;
    private static final int MIN_HEIGHT = 1000;
    private static final int RANDOM_LENGTH = 5;

    public static String saveProductImage(
            MultipartFile file,
            String sku,
            String originalDir
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("Invalid image file name");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("Invalid image content");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            throw new IllegalArgumentException("Image must be at least 1000x1000 pixels");
        }

        String randomSuffix = randomAlphaNumeric(RANDOM_LENGTH);
        String fileName = sku + "_" + randomSuffix + extension;

        Path uploadPath = Paths.get(originalDir);
        Path targetPath = uploadPath.resolve(fileName);

        System.out.println(">>> Saving image to: " + targetPath.toAbsolutePath());

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath.toString().replace("\\", "/");
    }

    private static String randomAlphaNumeric(int length) {
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, length);
    }
}
