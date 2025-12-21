package sunshine_dental_care.services.hr;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.services.upload_file.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CvExtractionService {

    private final ImageStorageService imageStorageService;
    private Tesseract tesseract;

    @Value("${tesseract.datapath:}")
    private String tesseractDataPath;

    private void initTesseract() {
        if (tesseract == null) {
            try {
                tesseract = new Tesseract();
                if (tesseractDataPath != null && !tesseractDataPath.trim().isEmpty()) {
                    tesseract.setDatapath(tesseractDataPath);
                    log.info("Tesseract data path set to: {}", tesseractDataPath);
                }
                // Cấu hình hỗ trợ nhận diện cả tiếng Việt và tiếng Anh
                tesseract.setLanguage("vie+eng");
                tesseract.setPageSegMode(1);
                tesseract.setOcrEngineMode(1);
                log.info("Tesseract OCR initialized with languages: vie+eng");
            } catch (Exception e) {
                log.error("Failed to initialize Tesseract OCR: {}", e.getMessage(), e);
                throw new RuntimeException("Tesseract OCR initialization failed", e);
            }
        }
    }

    // Chuyển trang PDF sang BufferedImage để OCR
    private BufferedImage convertPdfPageToImage(byte[] pdfBytes, int pageIndex) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            // Sử dụng DPI cao để tăng độ chính xác OCR
            return pdfRenderer.renderImageWithDPI(pageIndex, 300);
        }
    }

    // Lấy text từ BufferedImage bằng OCR
    private String ocrFromImage(BufferedImage image) throws TesseractException {
        initTesseract();
        if (tesseract == null) {
            throw new IllegalStateException("Tesseract OCR not initialized");
        }
        return tesseract.doOCR(image);
    }

    // Ưu tiên lấy text trực tiếp từ PDF, nếu không được thì dùng OCR
    public String extractTextFromPdf(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        StringBuilder text = new StringBuilder();
        int pageCount = 0;
        boolean hasTextLayer = false;

        try (InputStream is = new ByteArrayInputStream(fileBytes);
             PdfDocument pdfDoc = new PdfDocument(new PdfReader(is))) {
            pageCount = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = pdfDoc.getPage(i);

                try {
                    String pageText = PdfTextExtractor.getTextFromPage(page);
                    if (pageText != null && !pageText.trim().isEmpty()) {
                        text.append(pageText).append("\n\n");
                        hasTextLayer = true;
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract text from page {}: {}", i, e.getMessage());
                }
            }
        }

        // Nếu không có lớp text hoặc text quá ít -> OCR từng trang
        if (!hasTextLayer || text.toString().trim().length() < 50) {
            log.info("PDF không có lớp text hoặc rất ít text, thực hiện OCR cho toàn bộ trang");
            text.setLength(0);

            for (int i = 0; i < pageCount; i++) {
                try {
                    BufferedImage pageImage = convertPdfPageToImage(fileBytes, i);
                    String ocrText = ocrFromImage(pageImage);
                    if (ocrText != null && !ocrText.trim().isEmpty()) {
                        text.append(ocrText).append("\n\n");
                        log.info("OCR extracted {} characters from page {}", ocrText.length(), i + 1);
                    }
                } catch (Exception e) {
                    log.warn("Failed to OCR page {}: {}", i + 1, e.getMessage());
                }
            }
        }

        return text.toString();
    }

    // Lấy text trực tiếp từ DOCX, nếu có ảnh thì OCR luôn trên ảnh
    public String extractTextFromDoc(MultipartFile file) throws Exception {
        StringBuilder text = new StringBuilder();

        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            document.getParagraphs().forEach(para -> {
                String paraText = para.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    text.append(paraText).append("\n");
                }
            });

            List<XWPFPictureData> pictures = document.getAllPictures();
            if (!pictures.isEmpty()) {
                log.info("Tìm thấy {} ảnh trong DOCX, tiến hành OCR", pictures.size());
                initTesseract();

                for (int i = 0; i < pictures.size(); i++) {
                    try {
                        XWPFPictureData pictureData = pictures.get(i);
                        byte[] imageBytes = pictureData.getData();

                        if (imageBytes != null && imageBytes.length > 0) {
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                            if (image != null) {
                                String ocrText = ocrFromImage(image);
                                if (ocrText != null && !ocrText.trim().isEmpty()) {
                                    text.append("\n[Text từ ảnh ").append(i + 1).append("]\n");
                                    text.append(ocrText).append("\n");
                                    log.info("OCR extracted {} characters from image {}", ocrText.length(), i + 1);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Không thể OCR ảnh {} trong DOCX: {}", i + 1, e.getMessage());
                    }
                }
            }
        }

        return text.toString();
    }

    // Trích xuất ảnh từ PDF (hỗ trợ lấy tất cả image object)
    public List<byte[]> extractImagesFromPdf(MultipartFile file) throws Exception {
        List<byte[]> images = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             PdfDocument pdfDoc = new PdfDocument(new PdfReader(is))) {

            int pageCount = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = pdfDoc.getPage(i);

                try {
                    com.itextpdf.kernel.pdf.PdfResources resources = page.getResources();
                    if (resources != null) {
                        com.itextpdf.kernel.pdf.PdfDictionary xObjects = resources.getResource(com.itextpdf.kernel.pdf.PdfName.XObject);
                        if (xObjects != null) {
                            for (com.itextpdf.kernel.pdf.PdfName key : xObjects.keySet()) {
                                try {
                                    com.itextpdf.kernel.pdf.PdfStream xObjectStream = xObjects.getAsStream(key);
                                    if (xObjectStream != null) {
                                        com.itextpdf.kernel.pdf.PdfName subType = xObjectStream.getAsName(com.itextpdf.kernel.pdf.PdfName.Subtype);
                                        if (subType != null && subType.equals(com.itextpdf.kernel.pdf.PdfName.Image)) {
                                            PdfImageXObject imageXObject = new PdfImageXObject(xObjectStream);
                                            BufferedImage bufferedImage = imageXObject.getBufferedImage();

                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            ImageIO.write(bufferedImage, "png", baos);
                                            images.add(baos.toByteArray());
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("Không trích xuất được ảnh object {}: {}", key, e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Không trích xuất được ảnh từ trang {}: {}", i, e.getMessage());
                }
            }
        }

        return images;
    }

    // Trích xuất ảnh từ file DOC/DOCX
    public List<byte[]> extractImagesFromDoc(MultipartFile file) throws Exception {
        List<byte[]> images = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            List<XWPFPictureData> pictures = document.getAllPictures();
            for (XWPFPictureData pictureData : pictures) {
                if (pictureData != null && pictureData.getData() != null) {
                    images.add(pictureData.getData());
                }
            }
        }
        return images;
    }

    // Hàm xử lý upload và extract CV, return thông tin rút trích
    public CvExtractionResult processCvUpload(MultipartFile file, Integer userId) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("File name is null");
        }

        String fileType = fileName.toLowerCase();
        String extractedText;
        List<byte[]> extractedImages;

        // Xác định loại file và trích xuất tương ứng
        if (fileType.endsWith(".pdf")) {
            extractedText = extractTextFromPdf(file);
            extractedImages = extractImagesFromPdf(file);
        } else if (fileType.endsWith(".doc") || fileType.endsWith(".docx")) {
            extractedText = extractTextFromDoc(file);
            extractedImages = extractImagesFromDoc(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Only PDF, DOC, DOCX are supported.");
        }

        // Upload các ảnh đã trích xuất lên Cloudinary, trả về url cho từng ảnh
        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < extractedImages.size(); i++) {
            byte[] imageBytes = extractedImages.get(i);
            try {
                MultipartFile imageFile = new ByteArrayMultipartFile(
                    "image_" + i + ".png",
                    "image/png",
                    imageBytes
                );

                ImageStorageService.ImageUploadResult imageUpload = imageStorageService.upload(
                    imageFile, "employee-cv-images/" + userId
                );
                imageUrls.add(imageUpload.getUrl());
            } catch (Exception e) {
                log.warn("Upload ảnh extract thất bại {}: {}", i, e.getMessage());
            }
        }

        // Trả về kết quả chỉ gồm text & image, không lưu file CV gốc
        CvExtractionResult result = new CvExtractionResult();
        result.setExtractedText(extractedText);
        result.setExtractedImageUrls(imageUrls);
        result.setCvFileUrl(null);
        result.setCvFilePublicId(null);
        result.setOriginalFileName(fileName);
        result.setFileType(fileType.endsWith(".pdf") ? "PDF" : fileType.endsWith(".docx") ? "DOCX" : "DOC");
        result.setFileSize(file.getSize());

        return result;
    }

    // Tạo MultipartFile tạm từ dữ liệu byte[]
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] content;

        public ByteArrayMultipartFile(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }

    // Lưu thông tin kết quả sau khi rút trích nội dung CV
    public static class CvExtractionResult {
        private String extractedText;
        private List<String> extractedImageUrls;
        private String cvFileUrl;
        private String cvFilePublicId;
        private String originalFileName;
        private String fileType;
        private Long fileSize;

        public String getExtractedText() {
            return extractedText;
        }

        public void setExtractedText(String extractedText) {
            this.extractedText = extractedText;
        }

        public List<String> getExtractedImageUrls() {
            return extractedImageUrls;
        }

        public void setExtractedImageUrls(List<String> extractedImageUrls) {
            this.extractedImageUrls = extractedImageUrls;
        }

        public String getCvFileUrl() {
            return cvFileUrl;
        }

        public void setCvFileUrl(String cvFileUrl) {
            this.cvFileUrl = cvFileUrl;
        }

        public String getCvFilePublicId() {
            return cvFilePublicId;
        }

        public void setCvFilePublicId(String cvFilePublicId) {
            this.cvFilePublicId = cvFilePublicId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }
    }
}
