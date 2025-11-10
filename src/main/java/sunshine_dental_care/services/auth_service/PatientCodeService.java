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

    private static final int GLOBAL_SEQ_CLINIC_ID = 1;
    private static final int WIDTH = 8;

    @org.springframework.transaction.annotation.Transactional
    public String nextPatientCode() {
        PatientSequence seq = seqRepo.lockByClinicId(GLOBAL_SEQ_CLINIC_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Patient sequence not initialized (clinicId=" + GLOBAL_SEQ_CLINIC_ID + ")"));

        int current = (seq.getCurrentNumber() == null ? 0 : seq.getCurrentNumber());
        int next = current + 1;

        if (next > 99_999_999) {
            throw new IllegalStateException("Patient code capacity reached for 8-digit format; switch to 9 digits or Base36.");
        }

        seq.setCurrentNumber(next);
        seq.setUpdatedAt(java.time.Instant.now());

        String prefix = (seq.getPrefix() == null || seq.getPrefix().isBlank()) ? "SDC" : seq.getPrefix();
        String number = String.format("%0" + WIDTH + "d", next);

        return (prefix + "-" + number).toUpperCase();
    }
}

