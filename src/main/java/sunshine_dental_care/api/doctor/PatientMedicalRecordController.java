package sunshine_dental_care.api.doctor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordImageDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordRequest;
import sunshine_dental_care.services.doctor.PatientMedicalRecordService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/patients/{patientId}/records")
@RequiredArgsConstructor
public class PatientMedicalRecordController {

    private final PatientMedicalRecordService patientMedicalRecordService;

    // Lấy danh sách tất cả các hồ sơ bệnh án của 1 bệnh nhân theo patientId
    @GetMapping
    public ResponseEntity<List<MedicalRecordDTO>> getRecords(@PathVariable Integer patientId) {
        // Gọi service để lấy danh sách hồ sơ bệnh án của bệnh nhân
        return ResponseEntity.ok(patientMedicalRecordService.getRecords(patientId));
    }

    // Tạo mới một hồ sơ bệnh án cho bệnh nhân
    @PostMapping
    public ResponseEntity<MedicalRecordDTO> createRecord(
            @PathVariable Integer patientId,
            @Valid @RequestBody MedicalRecordRequest request
    ) {
        // Gọi service để tạo hồ sơ bệnh án với thông tin từ request
        return ResponseEntity.ok(patientMedicalRecordService.createRecord(patientId, request));
    }

    // Cập nhật thông tin của một hồ sơ bệnh án cụ thể (recordId)
    @PutMapping("/{recordId}")
    public ResponseEntity<MedicalRecordDTO> updateRecord(
            @PathVariable Integer patientId,
            @PathVariable Integer recordId,
            @Valid @RequestBody MedicalRecordRequest request
    ) {
        // Gọi service cập nhật hồ sơ bệnh án với id tương ứng và dữ liệu được gửi lên
        return ResponseEntity.ok(patientMedicalRecordService.updateRecord(patientId, recordId, request));
    }

    // Upload ảnh vào hồ sơ bệnh án (có thể là ảnh chụp, phim X-quang, ...)
    @PostMapping("/{recordId}/images")
    public ResponseEntity<MedicalRecordImageDTO> uploadRecordImage(
            @PathVariable Integer patientId,
            @PathVariable Integer recordId,
            @RequestParam("file") MultipartFile file, // file ảnh cần upload
            @RequestParam(value = "description", required = false) String description, // mô tả ảnh (không bắt buộc)
            @RequestParam(value = "aiTag", required = false) String aiTag // tag AI nếu có (không bắt buộc)
    ) {
        // Gọi service để upload ảnh vào hồ sơ bệnh án
        return ResponseEntity.ok(
            patientMedicalRecordService.uploadImage(patientId, recordId, file, description, aiTag)
        );
    }

    // Xóa một ảnh trong hồ sơ bệnh án của bệnh nhân
    @DeleteMapping("/{recordId}/images/{imageId}")
    public ResponseEntity<Void> deleteRecordImage(
            @PathVariable Integer patientId,
            @PathVariable Integer recordId,
            @PathVariable Integer imageId
    ) {
        // Gọi service để xóa ảnh với id (imageId) khỏi hồ sơ bệnh án tương ứng
        patientMedicalRecordService.deleteImage(patientId, recordId, imageId);
        // Trả về kết quả xóa thành công, không có nội dung (204 No Content)
        return ResponseEntity.noContent().build();
    }

    // Export medical record as PDF
    @GetMapping("/{recordId}/export-pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable Integer patientId,
            @PathVariable Integer recordId
    ) throws IOException {
        // Generate PDF
        byte[] pdfBytes = patientMedicalRecordService
                .exportRecordToPDF(patientId, recordId);
        
        // Set response headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "medical-record-" + recordId + ".pdf");
        headers.setContentLength(pdfBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}

