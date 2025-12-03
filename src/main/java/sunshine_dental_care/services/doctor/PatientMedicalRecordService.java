package sunshine_dental_care.services.doctor;

import org.springframework.web.multipart.MultipartFile;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordImageDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordRequest;

import java.util.List;

public interface PatientMedicalRecordService {
    List<MedicalRecordDTO> getRecords(Integer patientId);

    MedicalRecordDTO createRecord(Integer patientId, MedicalRecordRequest request);

    MedicalRecordDTO updateRecord(Integer patientId, Integer recordId, MedicalRecordRequest request);

    MedicalRecordImageDTO uploadImage(Integer patientId, Integer recordId, MultipartFile file, String description, String aiTag);

    void deleteImage(Integer patientId, Integer recordId, Integer imageId);
}

