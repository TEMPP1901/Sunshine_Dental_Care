package sunshine_dental_care.services.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.entities.EmployeeCvData;
import sunshine_dental_care.repositories.hr.EmployeeCvDataRepository;
import sunshine_dental_care.services.hr.CvExtractionService.CvExtractionResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeCvDataService {

    private final EmployeeCvDataRepository cvDataRepository;
    private final CvExtractionService cvExtractionService;
    private final ObjectMapper objectMapper;

    // Hàm upload CV, rút trích text và ảnh, sau đó lưu vào database
    @Transactional
    public EmployeeCvData uploadAndExtractCv(MultipartFile file, Integer userId) throws Exception {
        CvExtractionResult extractionResult = cvExtractionService.processCvUpload(file, userId);
        String extractedImagesJson = objectMapper.writeValueAsString(extractionResult.getExtractedImageUrls());

        Optional<EmployeeCvData> existingCv = cvDataRepository.findByUserId(userId);
        EmployeeCvData cvData;

        if (existingCv.isPresent()) {
            cvData = existingCv.get();
            // Cập nhật bản ghi cũ, không lưu file gốc
            cvData.setOriginalFileName(extractionResult.getOriginalFileName());
            cvData.setFileType(extractionResult.getFileType());
            cvData.setFileSize(extractionResult.getFileSize());
            cvData.setCvFileUrl(null);
            cvData.setCvFilePublicId(null);
            cvData.setExtractedText(extractionResult.getExtractedText());
            cvData.setExtractedImages(extractedImagesJson);
            cvData.setUpdatedAt(Instant.now());
        } else {
            // Tạo mới bản ghi, không lưu file gốc
            cvData = new EmployeeCvData();
            cvData.setUserId(userId);
            cvData.setOriginalFileName(extractionResult.getOriginalFileName());
            cvData.setFileType(extractionResult.getFileType());
            cvData.setFileSize(extractionResult.getFileSize());
            cvData.setCvFileUrl(null);
            cvData.setCvFilePublicId(null);
            cvData.setExtractedText(extractionResult.getExtractedText());
            cvData.setExtractedImages(extractedImagesJson);
            cvData.setCreatedAt(Instant.now());
            cvData.setUpdatedAt(Instant.now());
        }

        // Lưu thông tin CV vào DB
        EmployeeCvData saved = cvDataRepository.save(cvData);
        log.info("CV data saved for user {}: {} images extracted, text length: {}",
                userId, extractionResult.getExtractedImageUrls().size(),
                extractionResult.getExtractedText() != null ? extractionResult.getExtractedText().length() : 0);

        return saved;
    }

    // Lấy thông tin CV mới nhất của user
    public Optional<EmployeeCvData> getCvDataByUserId(Integer userId) {
        return cvDataRepository.findByUserId(userId);
    }

    // Lấy toàn bộ các lần upload CV của user (nếu có nhiều version)
    public List<EmployeeCvData> getAllCvDataByUserId(Integer userId) {
        return cvDataRepository.findAllByUserId(userId);
    }

    // Xóa thông tin CV của user khỏi DB
    @Transactional
    public void deleteCvDataByUserId(Integer userId) {
        cvDataRepository.deleteByUserId(userId);
        log.info("CV data deleted for user {}", userId);
    }

    // Cập nhật lại trường extractedText cho CV
    @Transactional
    public EmployeeCvData updateExtractedText(Integer userId, String extractedText) {
        Optional<EmployeeCvData> existingCv = cvDataRepository.findByUserId(userId);

        if (existingCv.isEmpty()) {
            throw new RuntimeException("CV data không tồn tại cho user " + userId);
        }

        EmployeeCvData cvData = existingCv.get();
        cvData.setExtractedText(extractedText);
        cvData.setUpdatedAt(Instant.now());

        EmployeeCvData saved = cvDataRepository.save(cvData);
        log.info("CV extracted text updated for user {}", userId);

        return saved;
    }

    // Parse extractedImages (JSON) sang List<String>
    public List<String> parseExtractedImages(EmployeeCvData cvData) {
        if (cvData.getExtractedImages() == null || cvData.getExtractedImages().trim().isEmpty()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                    cvData.getExtractedImages(),
                    new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            log.warn("Không parse được extracted images JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
