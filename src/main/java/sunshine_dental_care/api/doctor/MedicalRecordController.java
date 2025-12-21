package sunshine_dental_care.api.doctor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.medical.DiagnosisRequestDto;
import sunshine_dental_care.dto.medical.GeneratedMedicalRecordDto;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.doctor.MedicalRecordAiService;

/**
 * Medical Record Controller
 *
 * Plain-English description (easy to understand):
 * This controller exposes medical-record related operations used by doctors.
 * The AI endpoint below converts a doctor's short "diagnosis" text into suggested
 * medical-record fields (treatment plan, prescription list, and a short note).
 *
 * High-level flow for the AI endpoint:
 * 1. Ensure the requester is authenticated (doctor must be logged in).
 * 2. Optionally verify that the doctor owns the appointment (not implemented here,
 *    add business checks if needed).
 * 3. Call the MedicalRecordAiService which:
 *    - builds a strict Vietnamese prompt with the LLM RULES,
 *    - calls Gemini API, parses the JSON-only response,
 *    - validates required fields, and formats the prescription into a single string.
 * 4. Return generated fields to the frontend for the doctor to **review** (no DB write).
 *
 * Error handling note: the service throws specific exceptions for invalid AI output and
 * request validation errors; add a `@ControllerAdvice` to map those to HTTP statuses.
 */
@RestController
@RequestMapping("/api/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordAiService aiService;

    /**
     * POST /api/medical-records/{appointmentId}/ai/generate
     *
     * What this endpoint does (plain English):
     * - Requires an authenticated doctor.
     * - Accepts a short `diagnosis` text provided by the doctor.
     * - Returns generated medical-record fields: `treatmentPlan`, `prescriptionNote` (array),
     *   `prescriptionNoteFormatted` (single formatted string), and `note`.
     * - The generated result is for review only; this endpoint does NOT persist to the database.
     *
     * Request body: DiagnosisRequestDto { appointmentId, diagnosis }
     * Response body: GeneratedMedicalRecordDto { treatmentPlan, prescriptionNote, prescriptionNoteFormatted, note }
     */
    @PostMapping("/{appointmentId}/ai/generate")
    public ResponseEntity<GeneratedMedicalRecordDto> generateForAppointment(
            @PathVariable Integer appointmentId,
            @RequestBody DiagnosisRequestDto request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        // 1) Authentication: reject if not logged in
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        // 2) Authorization: ensure the current doctor can act on this appointment.
        //    (This check is intentionally left as a TODO because your domain
        //     logic determines ownership. Add it here if needed.)

        // 3) Call AI service: this will build the prompt, call Gemini, parse and validate
        //    the JSON response, and format the prescription string. IMPORTANT: the
        //    service does not save any records to the database.
        GeneratedMedicalRecordDto generated = aiService.generateFromDiagnosis(
                appointmentId,
                request.getDiagnosis(),
                currentUser.userId());

        // 4) Return the generated data to the frontend (HTTP 200). The frontend should
        //    display this to the doctor for confirmation before saving.
        return ResponseEntity.ok(generated);
    }
}
