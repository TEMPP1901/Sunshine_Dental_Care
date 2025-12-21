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
     * @return AI-generated patient summary in LEGACY format
     */
    public AIPatientSummaryResponse getAISummary(Integer appointmentId, Integer doctorId) {
        log.info("Generating AI summary for appointmentId={}, doctorId={}", appointmentId, doctorId);

        // Check cache first
        String cachedSummary = cacheService.get(appointmentId);
        if (cachedSummary != null) {
            log.info("Returning cached summary for appointmentId={}", appointmentId);
            return parseLegacyAISummary(cachedSummary);
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

        // Generate prompt - UPDATED for Legacy Format
        String prompt = buildLegacyAIPrompt(context);

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

        // Parse and return in LEGACY format
        return parseLegacyAISummary(aiResponse);
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

    private String buildPatientContext(Appointment appointment,
                                       List<MedicalRecord> records,
                                       List<AppointmentService> treatments) {

        String sep = " | ";
        StringBuilder context = new StringBuilder();

        // Patient basic info
        var patient = appointment.getPatient();
        context.append("PATIENT INFORMATION:")
                .append(sep).append("Name: ").append(patient.getFullName())
                .append(sep).append("Patient Code: ")
                .append(patient.getPatientCode() != null ? patient.getPatientCode() : "N/A");

        if (patient.getDateOfBirth() != null) {
            context.append(sep).append("Date of Birth: ")
                    .append(patient.getDateOfBirth().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (patient.getGender() != null) {
            context.append(sep).append("Gender: ").append(patient.getGender());
        }
        if (patient.getPhone() != null) {
            context.append(sep).append("Phone: ").append(patient.getPhone());
        }
        if (patient.getNote() != null && !patient.getNote().trim().isEmpty()) {
            context.append(sep).append("Patient Notes: ").append(patient.getNote());
        }

        if ("VIP".equals(appointment.getAppointmentType())) {
            context.append(sep).append("VIP Patient: Yes");
        }

        // Current appointment
        context.append(sep).append("CURRENT APPOINTMENT:")
                .append(sep).append("Appointment ID: ").append(appointment.getId())
                .append(sep).append("Date: ").append(appointment.getStartDateTime())
                .append(sep).append("Status: ").append(appointment.getStatus());

        if (appointment.getNote() != null && !appointment.getNote().trim().isEmpty()) {
            context.append(sep).append("Appointment Note: ").append(appointment.getNote());
        }

        // Medical records
        context.append(sep).append("RECENT MEDICAL RECORDS (")
                .append(records.size()).append("):");

        if (records.isEmpty()) {
            context.append(sep).append("No medical records found");
        } else {
            for (int i = 0; i < records.size(); i++) {
                MedicalRecord r = records.get(i);
                context.append(sep).append("Record #").append(i + 1).append(":")
                        .append(" Date=").append(r.getRecordDate());

                if (r.getService() != null) {
                    context.append(", Service=").append(r.getService().getServiceName());
                }
                if (r.getServiceVariant() != null) {
                    context.append(", Variant=").append(r.getServiceVariant().getVariantName());
                }
                if (r.getDiagnosis() != null && !r.getDiagnosis().trim().isEmpty()) {
                    context.append(", Diagnosis=").append(r.getDiagnosis());
                }
                if (r.getTreatmentPlan() != null && !r.getTreatmentPlan().trim().isEmpty()) {
                    context.append(", Treatment Plan=").append(r.getTreatmentPlan());
                }
                if (r.getPrescriptionNote() != null && !r.getPrescriptionNote().trim().isEmpty()) {
                    context.append(", Prescription=").append(r.getPrescriptionNote());
                }
                if (r.getNote() != null && !r.getNote().trim().isEmpty()) {
                    context.append(", Notes=").append(r.getNote());
                }
            }
        }

        // Treatments
        context.append(sep).append("RECENT TREATMENTS (")
                .append(treatments.size()).append("):");

        if (treatments.isEmpty()) {
            context.append(sep).append("No recent treatments found");
        } else {
            for (int i = 0; i < treatments.size(); i++) {
                AppointmentService t = treatments.get(i);
                context.append(sep).append("Treatment #").append(i + 1).append(":");

                if (t.getAppointment() != null) {
                    context.append(" Appointment Date=")
                            .append(t.getAppointment().getStartDateTime());
                }
                if (t.getService() != null) {
                    context.append(", Service=").append(t.getService().getServiceName());
                }
                if (t.getServiceVariant() != null) {
                    context.append(", Variant=").append(t.getServiceVariant().getVariantName());
                }
                if (t.getQuantity() != null) {
                    context.append(", Quantity=").append(t.getQuantity());
                }
                if (t.getNote() != null && !t.getNote().trim().isEmpty()) {
                    context.append(", Notes=").append(t.getNote());
                }
            }
        }

        return context.toString();
    }

    private String buildLegacyAIPrompt(String patientContext) {
        return """
        You are an intelligent dental clinic assistant helping dentists quickly review patient records.
        
                STRICT RULES:
                - DO NOT translate or explain offensive or unusual text.
                - DO NOT use emotional, judgmental, or alarmist language.
                - DO NOT add commentary outside provided data.
                - If data appears invalid or inappropriate, mark as "Data quality issue" only.
                - Alerts must be factual, short, neutral.
                - NEVER use adjectives like: alarming, stupid, critical, dangerous.
                
                
        **TASK:**
        Analyze the patient data below and generate a summary in LEGACY FORMAT.
        
        **PATIENT DATA:**
        %s
        
        **REQUIRED OUTPUT FORMAT (LEGACY FORMAT):**
        
        # AI Support – Patient Summary
        
        Legacy Format  
        Updated: [current_datetime in format: MM/dd/yyyy, HH:mm:ss AM/PM]  
        
        ---
        
        ## Attention Level ([Attention Level])
        Based on analysis of treatment history and data quality  
        
        ---
        
        ### Using Legacy Format
        Enhanced AI analysis features not available. Update to new API format for full capabilities.
        
        ---
        
        ### Overview
        [Provide a 2-3 sentence overview of the patient. Mention if VIP, key observations]
        
        ---
        
        ### Alerts [Important or leave blank]
        [List important alerts, concerns, or unusual findings. If no significant alerts, write "No significant alerts"]
        
        ---
        
        ### Recent Treatments
        [List recent treatments in chronological order, most recent first]
        
        **IMPORTANT RULES:**
        1. For Attention Level: Use "Low Attention", "Moderate Attention", or "High Attention" based on:
           - Low: No concerning findings, complete records
           - Moderate: Some issues requiring review
           - High: Serious concerns or critical missing information
        
        2. For Alerts section:
           - Include if there are: serious diagnoses, unusual findings, missing information, data conflicts
           - Mark as "Important" if there are alerts
           - If no alerts, just write "No significant alerts"
        
        3. For Recent Treatments:
           - List treatments in bullet points
           - Include dates and brief descriptions
           - Focus on the most recent 3-5 treatments
        
        4. Use concise, professional language
        5. Base all conclusions strictly on provided data
        6. Current datetime example: 12/21/2025, 11:09:03 PM
        
        **EXAMPLE OUTPUT:**
        # AI Support – Patient Summary
        
        Legacy Format  
        Updated: 12/21/2025, 11:09:03 PM  
        
        ---
        
        ## Attention Level (Moderate Attention)
        Based on analysis of treatment history and data quality  
        
        ---
        
        ### Using Legacy Format
        Enhanced AI analysis features not available. Update to new API format for full capabilities.
        
        ---
        
        ### Overview
        Patient Nguyễn Văn A (SDC-001) is a VIP patient with a history of recent and varied dental procedures. The most recent medical records indicate ongoing dental issues and significant systemic health concerns.
        
        ---
        
        ### Alerts Important
        Patient has a documented diagnosis of 'tiểu đường' (diabetes). There is also a diagnosis of 'sâu răng viêm nướu nặng' (severe tooth decay and gingivitis) with a prescribed 3-month course of Amoxicillin and Chlorhexidine. A highly unusual and concerning diagnosis of 'sắp chết' (dying soon) was recorded on 2025-12-16, which requires immediate verification.
        
        ---
        
        ### Recent Treatments
        Recent treatments include a Permanent Implant on 2025-12-20. On 2025-12-16, the patient received a Children Dental Checkup (Fluoride varnish application), underwent Wisdom Tooth Removal (both milk tooth and surgical wisdom tooth extraction), and had Veneers & Whitening (1 veneer).
        """.formatted(patientContext);
    }

    private AIPatientSummaryResponse parseLegacyAISummary(String aiResponse) {
        try {
            // Extract sections from the legacy format
            String overview = extractSection(aiResponse, "### Overview", "### Alerts");
            String alerts = extractSection(aiResponse, "### Alerts", "### Recent Treatments");
            String recentTreatments = extractSection(aiResponse, "### Recent Treatments", null);

            // Clean up the text
            overview = overview != null ? overview.trim() : "";
            alerts = (alerts == null || alerts.isBlank())
                    ? "No significant alerts"
                    : alerts;

            recentTreatments = recentTreatments != null ? recentTreatments.trim() : "";

            // Remove "Important" label from alerts if present
            if (alerts.startsWith("Important")) {
                alerts = alerts.substring(9).trim();
            }

            log.info("Parsed Legacy AI Summary:");
            log.info("Overview: {}", overview);
            log.info("Alerts: {}", alerts);
            log.info("Recent Treatments: {}", recentTreatments);

            return AIPatientSummaryResponse.builder()
                    .overview(overview)
                    .alerts(alerts.isEmpty() ? "No significant alerts" : alerts)
                    .recentTreatments(recentTreatments.isEmpty() ? "No recent treatments found" : recentTreatments)
                    .rawSummary(aiResponse)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse legacy AI response: {}", e.getMessage());

            // Fallback: return basic response
            return AIPatientSummaryResponse.builder()
                    .overview("Unable to generate AI summary. Please try again.")
                    .alerts("AI analysis unavailable")
                    .recentTreatments("Recent treatments data not available")
                    .rawSummary(aiResponse)
                    .build();
        }
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        try {
            int startIndex = text.indexOf(startMarker);
            if (startIndex == -1) {
                return "";
            }

            // Move to the content after the marker
            startIndex += startMarker.length();

            // Find the end of the section
            int endIndex;
            if (endMarker != null) {
                endIndex = text.indexOf(endMarker, startIndex);
                if (endIndex == -1) {
                    endIndex = text.length();
                }
            } else {
                endIndex = text.length();
            }

            return text.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            log.warn("Error extracting section with markers {} - {}: {}", startMarker, endMarker, e.getMessage());
            return "";
        }
    }
}