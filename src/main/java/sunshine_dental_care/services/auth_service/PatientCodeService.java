package sunshine_dental_care.services.auth_service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.PatientSequence;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientSequenceRepo;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PatientCodeService {

    private final PatientSequenceRepo seqRepo;
    private final ClinicRepo clinicRepo;

    @Transactional
    public String nextPatientCode(Integer clinicId) {
        PatientSequence seq = seqRepo.lockByClinicId(clinicId)
                .orElseThrow(() -> new IllegalStateException("Patient sequence not initialized for clinicId=" + clinicId));

        int next = (seq.getCurrentNumber() == null ? 0 : seq.getCurrentNumber()) + 1;
        seq.setCurrentNumber(next);
        seq.setUpdatedAt(java.time.Instant.now());

        String prefix = (seq.getPrefix() == null || seq.getPrefix().isBlank()) ? "SDC" : seq.getPrefix();
        String clinicCode = clinicRepo.findById(clinicId).map(Clinic::getClinicCode).orElse("CLN");
        String yyMM = YearMonth.now().format(DateTimeFormatter.ofPattern("yyMM"));

        return String.format("%s-%s-%s-%06d", prefix, yyMM, clinicCode, next);
    }
}
