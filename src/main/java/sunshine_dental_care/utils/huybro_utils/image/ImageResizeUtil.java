package sunshine_dental_care.utils.huybro_utils.image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

public class ImageResizeUtil {

    public static byte[] resizeToFit(File inputFile, int targetWidth, int targetHeight, String format) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) throw new IOException("Cannot read image: " + inputFile.getAbsolutePath());

        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        double scale = Math.min((double) targetWidth / w, (double) targetHeight / h);
        int newW = Math.max(1, (int) Math.round(w * scale));
        int newH = Math.max(1, (int) Math.round(h * scale));

        Image scaled = originalImage.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage output = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(output, format, baos);
        return baos.toByteArray();
    }
}
