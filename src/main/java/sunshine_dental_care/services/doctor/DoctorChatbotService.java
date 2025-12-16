package sunshine_dental_care.services.doctor;

import sunshine_dental_care.dto.doctorDTO.ChatbotRequest;
import sunshine_dental_care.dto.doctorDTO.ChatbotResponse;

/**
 * Service interface for doctor chatbot functionality
 * Allows doctors to query information about medical records, appointments, services, and patients using natural language
 */
public interface DoctorChatbotService {
    
    /**
     * Process a chatbot query from a doctor
     * 
     * Flow:
     * 1. Query database based on request parameters (patientId, recordId, appointmentId)
     * 2. Build context from database results (MedicalRecord, Appointment, ServiceVariant, Patient, Service)
     * 3. Send context + question to Gemini AI
     * 4. Return the AI-generated response
     * 
     * @param request The chatbot request containing the question and optional filters
     * @return ChatbotResponse containing the answer from Gemini AI
     */
    ChatbotResponse processQuery(ChatbotRequest request);
}


