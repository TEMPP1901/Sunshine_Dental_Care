package sunshine_dental_care.services.auth_service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.PatientSequence;
import sunshine_dental_care.repositories.auth.PatientSequenceRepo;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PatientCodeService {
    private final PatientSequenceRepo seqRepo;

    @Transactional
    public String nextPatientCode(Integer clinicId) {
        PatientSequence seq = seqRepo.lockByClinicId(clinicId)
                .orElseThrow(() -> new IllegalStateException("Patient sequence not initialized for clinicId=" + clinicId));

        int next = (seq.getCurrentNumber() == null ? 0 : seq.getCurrentNumber()) + 1;
        seq.setCurrentNumber(next);
        seq.setUpdatedAt(java.time.Instant.now());

        String prefix = (seq.getPrefix() == null || seq.getPrefix().isBlank()) ? "SDC" : seq.getPrefix(); // <=10
        String yyMM = YearMonth.now().format(DateTimeFormatter.ofPattern("yyMM")); // 4
        String number = String.format("%06d", next); // 6

        String code = prefix + "-" + yyMM + "-" + number;
        if (code.length() > 20) {

            code = (prefix + yyMM + number);
            if (code.length() > 20) {
                int maxPrefixLen = Math.max(0, 20 - (yyMM.length() + number.length()));
                code = prefix.substring(0, Math.min(prefix.length(), maxPrefixLen)) + yyMM + number;
            }
        }
        return code;
    }
}
