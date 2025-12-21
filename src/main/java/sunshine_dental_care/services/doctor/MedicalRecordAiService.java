package sunshine_dental_care.services.doctor;

import sunshine_dental_care.dto.medical.GeneratedMedicalRecordDto;

public interface MedicalRecordAiService {
    /**
     * Generate suggested treatmentPlan, prescription and note from diagnosis.
     * Does not save to DB.
     * @param appointmentId optional appointment id
     * @param diagnosis required doctor's diagnosis text
     * @param doctorId id of the requesting doctor (for validation/audit)
     */
    GeneratedMedicalRecordDto generateFromDiagnosis(Integer appointmentId, String diagnosis, Integer doctorId);
}
