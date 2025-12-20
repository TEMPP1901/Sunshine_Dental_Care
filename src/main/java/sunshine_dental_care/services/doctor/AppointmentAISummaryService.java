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
            You are a dental clinic assistant helping a dentist quickly understand a patient's key information.
            
            IMPORTANT RULES:
            1. You are providing READ-ONLY, ADVISORY information only
            2. You do NOT diagnose or recommend treatments
            3. You summarize existing medical records and treatment history
            4. Focus on important alerts, patterns, and key information
            
            PATIENT DATA:
            %s
            
            Please generate a concise JSON summary with the following structure:
            {
              "overview": "Brief patient overview (demographics, basic info, key characteristics)",
              "alerts": "Important alerts, medical history concerns, allergies, risks, or warnings (if any)",
              "recentTreatments": "Summary of recent treatments and procedures (last 3-5 visits)"
            }
            
            Guidelines:
            - Keep each field concise (2-4 sentences max)
            - Highlight important medical history or concerns in "alerts"
            - If no alerts exist, use "No significant alerts" in alerts field
            - Focus on actionable information for the dentist
            - Use clear, professional medical language
            """.formatted(patientContext);
    }
    
    private AIPatientSummaryResponse parseAISummary(String aiResponse) {
        try {
            // Try to parse as JSON first
            JsonNode jsonNode = objectMapper.readTree(aiResponse);
            
            String overview = jsonNode.path("overview").asText("");
            String alerts = jsonNode.path("alerts").asText("");
            String recentTreatments = jsonNode.path("recentTreatments").asText("");
            
            return AIPatientSummaryResponse.builder()
                    .overview(overview)
                    .alerts(alerts)
                    .recentTreatments(recentTreatments)
                    .rawSummary(aiResponse)
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to parse AI response as JSON, treating as plain text: {}", e.getMessage());
            // Fallback: treat entire response as overview
            return AIPatientSummaryResponse.builder()
                    .overview(aiResponse)
                    .alerts("Unable to parse structured summary")
                    .recentTreatments("See overview for details")
                    .rawSummary(aiResponse)
                    .build();
        }
    }
}
