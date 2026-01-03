package sunshine_dental_care.dto.doctorDTO;

/**
 * DTO for chatbot response
 * Contains the answer from Gemini AI based on the doctor's query
 */
public record ChatbotResponse(
    /**
     * The answer/response from the chatbot (Gemini AI)
     * This contains the information requested by the doctor
     */
    String answer,
    
    /**
     * The original question that was asked
     */
    String question,
    
    /**
     * Indicates if the response was successfully generated
     */
    boolean success,
    
    /**
     * Error message if something went wrong (null if success is true)
     */
    String errorMessage
) {
    /**
     * Factory method to create a successful response
     */
    public static ChatbotResponse success(String answer, String question) {
        return new ChatbotResponse(answer, question, true, null);
    }
    
    /**
     * Factory method to create an error response
     */
    public static ChatbotResponse error(String question, String errorMessage) {
        return new ChatbotResponse(null, question, false, errorMessage);
    }
}


