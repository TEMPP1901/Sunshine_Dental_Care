package sunshine_dental_care.dto.doctorDTO;

/**
 * DTO for chatbot request from doctor
 * Contains the question/query that the doctor wants to ask about medical records, appointments, services, etc.
 */

public record ChatbotRequest(
    /**
     * The question or query from the doctor
     * Example: "Show me all appointments for patient with ID 123"
     * Example: "What services were provided to patient John Doe?"
     */
    String question,
    
    /**
     * Optional: Patient ID to filter results
     * If provided, the chatbot will focus on information related to this specific patient
     */
    String patientCode,
    
    /**
     * Optional: Medical Record ID to get specific record information
     * If provided, the chatbot will focus on this specific medical record
     */
    Integer recordId,
    
    /**
     * Optional: Appointment ID to get specific appointment information
     * If provided, the chatbot will focus on this specific appointment
     */
    Integer appointmentId
) {
}


