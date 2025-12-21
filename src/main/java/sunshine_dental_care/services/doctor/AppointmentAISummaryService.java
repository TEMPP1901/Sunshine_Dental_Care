package sunshine_dental_care.services.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.doctorDTO.AIPatientSummaryResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.MedicalRecord;
import sunshine_dental_care.repositories.doctor.AppointmentServiceRepository;
import sunshine_dental_care.repositories.doctor.DoctorAppointmentRepo;
import sunshine_dental_care.repositories.doctor.MedicalRecordRepository;
import sunshine_dental_care.repositories.doctor.PatientInsightRepository;
import sunshine_dental_care.services.impl.hr.GeminiApiClient;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating AI-powered patient summaries for doctors.
 * Aggregates patient data and uses Gemini AI to generate concise summaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentAISummaryService {
    
    private final DoctorAppointmentRepo appointmentRepo;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientInsightRepository patientInsightRepository;
    private final GeminiApiClient geminiApiClient;
    private final AppointmentAISummaryCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final int MAX_MEDICAL_RECORDS = 5;
    private static final int MAX_RECENT_TREATMENTS = 10;
    
    /**
     * Generate or retrieve cached AI summary for an appointment.
     * Validates that the doctor owns the appointment.
     * 
     * @param appointmentId Appointment ID
     * @param doctorId Doctor ID (from authentication)
     * @return AI-generated patient summary
     */
    public AIPatientSummaryResponse getAISummary(Integer appointmentId, Integer doctorId) {
        log.info("Generating AI summary for appointmentId={}, doctorId={}", appointmentId, doctorId);
        
        // Check cache first
        String cachedSummary = cacheService.get(appointmentId);
        if (cachedSummary != null) {
            log.info("Returning cached summary for appointmentId={}", appointmentId);
            return parseAISummary(cachedSummary);
        }
        
        // Load and validate appointment
        Appointment appointment = appointmentRepo.findByIdAndDoctorId(appointmentId, doctorId);
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment not found or doctor does not own this appointment");
        }
        
        Integer patientId = appointment.getPatient().getId();
        
        // Aggregate patient data
        List<MedicalRecord> recentRecords = getRecentMedicalRecords(patientId);
        List<AppointmentService> recentTreatments = getRecentAppointmentServices(patientId);
        
        // Build context for AI
        String context = buildPatientContext(appointment, recentRecords, recentTreatments);
        
        // Generate prompt
        String prompt = buildAIPrompt(context);
        
        // Call Gemini AI
        String aiResponse;
        try {
            aiResponse = geminiApiClient.generateContent(prompt);
            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                throw new IllegalStateException("AI service returned empty response");
            }
        } catch (Exception e) {
            log.error("Failed to generate AI summary", e);
            throw new RuntimeException("Failed to generate AI summary: " + e.getMessage(), e);
        }
        
        // Cache the result
        cacheService.put(appointmentId, aiResponse);
        
        // Parse and return
        return parseAISummary(aiResponse);
    }
    
    private List<MedicalRecord> getRecentMedicalRecords(Integer patientId) {
        List<MedicalRecord> allRecords = medicalRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId);
        return allRecords.stream()
                .limit(MAX_MEDICAL_RECORDS)
                .collect(Collectors.toList());
    }
    
    private List<AppointmentService> getRecentAppointmentServices(Integer patientId) {
        List<AppointmentService> allServices = patientInsightRepository.recentAppointmentServicesByPatientId(patientId);
        return allServices.stream()
                .limit(MAX_RECENT_TREATMENTS)
                .collect(Collectors.toList());
    }
    
    private String buildPatientContext(Appointment appointment, List<MedicalRecord> records, List<AppointmentService> treatments) {
        StringBuilder context = new StringBuilder();
        
        // Patient basic info
        var patient = appointment.getPatient();
        context.append("PATIENT INFORMATION:\n");
        context.append("- Name: ").append(patient.getFullName()).append("\n");
        context.append("- Patient Code: ").append(patient.getPatientCode() != null ? patient.getPatientCode() : "N/A").append("\n");
        if (patient.getDateOfBirth() != null) {
            context.append("- Date of Birth: ").append(patient.getDateOfBirth().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
        }
        if (patient.getGender() != null) {
            context.append("- Gender: ").append(patient.getGender()).append("\n");
        }
        if (patient.getPhone() != null) {
            context.append("- Phone: ").append(patient.getPhone()).append("\n");
        }
        if (patient.getNote() != null && !patient.getNote().trim().isEmpty()) {
            context.append("- Patient Notes: ").append(patient.getNote()).append("\n");
        }
        context.append("\n");
        
        // Current appointment info
        context.append("CURRENT APPOINTMENT:\n");
        context.append("- Appointment ID: ").append(appointment.getId()).append("\n");
        context.append("- Date: ").append(appointment.getStartDateTime()).append("\n");
        context.append("- Status: ").append(appointment.getStatus()).append("\n");
        if (appointment.getNote() != null && !appointment.getNote().trim().isEmpty()) {
            context.append("- Appointment Note: ").append(appointment.getNote()).append("\n");
        }
        context.append("\n");
        
        // Recent medical records (3-5 most recent)
        context.append("RECENT MEDICAL RECORDS (").append(records.size()).append(" records):\n");
        if (records.isEmpty()) {
            context.append("- No medical records found.\n");
        } else {
            for (int i = 0; i < records.size(); i++) {
                MedicalRecord record = records.get(i);
                context.append("\nRecord #").append(i + 1).append(":\n");
                context.append("- Date: ").append(record.getRecordDate()).append("\n");
                if (record.getService() != null) {
                    context.append("- Service: ").append(record.getService().getServiceName()).append("\n");
                }
                if (record.getServiceVariant() != null) {
                    context.append("- Variant: ").append(record.getServiceVariant().getVariantName()).append("\n");
                }
                if (record.getDiagnosis() != null && !record.getDiagnosis().trim().isEmpty()) {
                    context.append("- Diagnosis: ").append(record.getDiagnosis()).append("\n");
                }
                if (record.getTreatmentPlan() != null && !record.getTreatmentPlan().trim().isEmpty()) {
                    context.append("- Treatment Plan: ").append(record.getTreatmentPlan()).append("\n");
                }
                if (record.getPrescriptionNote() != null && !record.getPrescriptionNote().trim().isEmpty()) {
                    context.append("- Prescription: ").append(record.getPrescriptionNote()).append("\n");
                }
                if (record.getNote() != null && !record.getNote().trim().isEmpty()) {
                    context.append("- Notes: ").append(record.getNote()).append("\n");
                }
            }
        }
        context.append("\n");
        
        // Recent treatments (from AppointmentServices)
        context.append("RECENT TREATMENTS (").append(treatments.size()).append(" treatments):\n");
        if (treatments.isEmpty()) {
            context.append("- No recent treatments found.\n");
        } else {
            for (int i = 0; i < treatments.size(); i++) {
                AppointmentService treatment = treatments.get(i);
                context.append("\nTreatment #").append(i + 1).append(":\n");
                if (treatment.getAppointment() != null) {
                    context.append("- Appointment Date: ").append(treatment.getAppointment().getStartDateTime()).append("\n");
                }
                if (treatment.getService() != null) {
                    context.append("- Service: ").append(treatment.getService().getServiceName()).append("\n");
                }
                if (treatment.getServiceVariant() != null) {
                    context.append("- Variant: ").append(treatment.getServiceVariant().getVariantName()).append("\n");
                }
                if (treatment.getQuantity() != null) {
                    context.append("- Quantity: ").append(treatment.getQuantity()).append("\n");
                }
                if (treatment.getNote() != null && !treatment.getNote().trim().isEmpty()) {
                    context.append("- Notes: ").append(treatment.getNote()).append("\n");
                }
            }
        }
        
        return context.toString();
    }

    private String buildAIPrompt(String patientContext) {
        return """
        You are an intelligent dental clinic assistant helping dentists quickly review and assess patient records.
        
        **CLINICAL RULES:**
        1. DO NOT diagnose diseases or medical conditions.
        2. DO NOT recommend or prescribe treatments.
        3. ONLY analyze and summarize the provided data.
        4. Detect abnormalities, inconsistencies, and missing information in the records.
        5. Follow the principle of "do no harm".
        
        **PERMITTED ANALYSIS SCOPE:**
        1. Identify general risk factors.
        2. Underlying medical conditions (only if explicitly documented).
        3. Repeated or recurring treatment history.
        4. Recent invasive or high-impact procedures.
        5. Detection of abnormal or low-quality data.
        6. Unprofessional or inappropriate clinical notes.
        7. Prescriptions missing dosage or duration.
        8. Inconsistencies or contradictions across records.
        
        **PATIENT DATA:**
        %s
        
        **MANDATORY OUTPUT FORMAT (AI Output Contract):**
        Return ONLY valid JSON with the following structure:
        {
          "overview": "A brief overview of the patient and treatment context (1–2 sentences, no raw data listing)",
          "attentionLevel": "LOW | MEDIUM | HIGH (based on historical data, not emotion)",
          "dataQualityIssues": [
            {
              "issue": "description of the data quality issue",
              "severity": "LOW | MEDIUM | HIGH",
              "suggestion": "recommended corrective action"
            }
          ],
          "riskFactors": [
            {
              "factor": "name of the risk factor",
              "evidence": "supporting evidence from the records",
              "impact": "MINOR | MODERATE | SIGNIFICANT"
            }
          ],
          "advisoryNotes": [
            "Operational advisory notes (e.g., requires verification, requires careful review, record update needed)"
          ],
          "summaryReport": "A professional narrative summary written like a clinical review report"
        }
        
        **ANALYSIS GUIDELINES:**
        
        1. ATTENTION LEVEL ASSESSMENT:
           - LOW: Records are complete and consistent, no notable concerns.
           - MEDIUM: Some issues require review (missing data, repeated treatments).
           - HIGH: Serious issues detected (data conflicts, critical missing information).
        
        2. DATA QUALITY ISSUE DETECTION:
           - Notes that do not meet professional clinical standards.
           - Prescriptions missing dosage or treatment duration.
           - Ambiguous, subjective, or emotional wording.
           - Missing essential clinical information.
        
        3. RISK FACTOR IDENTIFICATION:
           - Documented underlying medical conditions.
           - Repeated treatments for the same condition.
           - Recent invasive or high-risk procedures.
           - Abnormal intervals between visits or treatments.
        
        4. ADVISORY NOTES:
           - "Requires verification: [information to verify]"
           - "Requires careful review by the doctor: [item to review]"
           - "Record update required: [information to be added or corrected]"
        
        5. SUMMARY REPORT:
           - Written in a professional clinical reporting style.
           - Concise and focused on actionable information.
           - Structure: Overview → Issues → Advisory notes.
        
        **IMPORTANT NOTES:**
        - All output must be in English.
        - The JSON must be 100% valid and parsable.
        - Base all conclusions strictly on the provided data.
        - Prioritize identifying issues over positive commentary.
        - Act as a “clinical record quality auditor,” not a clinician.
        """.formatted(patientContext);
    }


    private AIPatientSummaryResponse parseAISummary(String aiResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponse);

            // Parse basic fields
            String overview = jsonNode.path("overview").asText("");
            String attentionLevel = jsonNode.path("attentionLevel").asText("LOW");
            String summaryReport = jsonNode.path("summaryReport").asText("");

            // Parse data quality issues
            List<AIPatientSummaryResponse.DataQualityIssue> dataQualityIssues = new ArrayList<>();
            JsonNode issuesNode = jsonNode.path("dataQualityIssues");
            if (issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    AIPatientSummaryResponse.DataQualityIssue issue =
                            AIPatientSummaryResponse.DataQualityIssue.builder()
                                    .issue(issueNode.path("issue").asText(""))
                                    .severity(issueNode.path("severity").asText("LOW"))
                                    .suggestion(issueNode.path("suggestion").asText(""))
                                    .build();
                    dataQualityIssues.add(issue);
                }
            }

            // Parse risk factors
            List<AIPatientSummaryResponse.RiskFactor> riskFactors = new ArrayList<>();
            JsonNode risksNode = jsonNode.path("riskFactors");
            if (risksNode.isArray()) {
                for (JsonNode riskNode : risksNode) {
                    AIPatientSummaryResponse.RiskFactor risk =
                            AIPatientSummaryResponse.RiskFactor.builder()
                                    .factor(riskNode.path("factor").asText(""))
                                    .evidence(riskNode.path("evidence").asText(""))
                                    .impact(riskNode.path("impact").asText("MINOR"))
                                    .build();
                    riskFactors.add(risk);
                }
            }

            // Parse advisory notes
            List<String> advisoryNotes = new ArrayList<>();
            JsonNode notesNode = jsonNode.path("advisoryNotes");
            if (notesNode.isArray()) {
                for (JsonNode noteNode : notesNode) {
                    advisoryNotes.add(noteNode.asText(""));
                }
            }

            return AIPatientSummaryResponse.builder()
                    .overview(overview)
                    .attentionLevel(attentionLevel)
                    .dataQualityIssues(dataQualityIssues)
                    .riskFactors(riskFactors)
                    .advisoryNotes(advisoryNotes)
                    .summaryReport(summaryReport)
                    .rawSummary(aiResponse)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI response as JSON: {}", e.getMessage());

            // Fallback: create a basic response
            return AIPatientSummaryResponse.builder()
                    .overview(aiResponse)
                    .attentionLevel("MEDIUM")
                    .dataQualityIssues(List.of(
                            AIPatientSummaryResponse.DataQualityIssue.builder()
                                    .issue("AI response is not in the expected format")
                                    .severity("HIGH")
                                    .suggestion("Review AI configuration or prompt definition")
                                    .build()
                    ))
                    .riskFactors(new ArrayList<>())
                    .advisoryNotes(List.of("AI output requires manual review"))
                    .summaryReport("Unable to analyze patient records due to AI response formatting error")
                    .rawSummary(aiResponse)
                    .build();
        }
    }
}
