package sunshine_dental_care.services.doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Client for interacting with OpenRouter API using meta-llama/llama-3.1-8b-instruct model
 */
@Component("doctorGeminiApiClient")
@Slf4j
public class GeminiApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Chỉ để key trong properties
    @Value("${openrouter.api.api-key}")
    private String apiKey;

    // Các giá trị cố định để trong file
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "meta-llama/llama-3.1-8b-instruct";

    // System prompt cố định cho dental assistant
    private static final String SYSTEM_PROMPT = """
            You are an intelligent dental clinic assistant specialized in summarizing patient records.
            
            STRICT GUIDELINES:
            1. Provide concise, professional medical summaries
            2. Use neutral, factual language without emotional adjectives
            3. Do not translate or explain unusual text
            4. Focus only on information provided in the patient data
            5. Format responses exactly as requested
            6. For dental terminology, use professional terms
            7. If data quality issues exist, note them briefly
            8. Never add information not in the provided data
            
            Your role is to help dentists quickly understand patient history before appointments.
            """;

    public GeminiApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate content using OpenRouter API with Llama 3.1 8B Instruct
     *
     * @param prompt The prompt/text to send to AI
     * @return Generated text response
     */
    public String generateContent(String prompt) {
        validateApiConfiguration();

        try {
            // Build request body for OpenRouter
            Map<String, Object> requestBody = buildRequestBody(prompt);

            // Prepare headers
            HttpHeaders headers = buildHeaders();

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("Sending request to OpenRouter API. Model: {}, Prompt length: {}", MODEL, prompt.length());

            // Send request
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            return handleApiResponse(response);

        } catch (Exception e) {
            log.error("Error calling OpenRouter API with Llama 3.1", e);
            return generateFallbackResponse(prompt);
        }
    }

    /**
     * Validate API configuration
     */
    private void validateApiConfiguration() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("OpenRouter API key is not configured");
            throw new IllegalStateException("OpenRouter API key is required. Please configure openrouter.api.key in application.properties");
        }
    }

    /**
     * Build request body for Llama 3.1 model
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();

        // Messages array for chat format
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);

        // User message
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        messages.add(systemMessage);
        messages.add(userMessage);

        requestBody.put("model", MODEL);
        requestBody.put("messages", messages);

        // Generation parameters optimized for Llama 3.1
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("temperature", 0.1); // Very low temperature for consistent medical summaries
        parameters.put("max_tokens", 1024); // Sufficient for summaries
        parameters.put("top_p", 0.9);
        parameters.put("frequency_penalty", 0.1); // Slight penalty to avoid repetition
        parameters.put("presence_penalty", 0.1);
        parameters.put("stream", false);

        requestBody.putAll(parameters);

        return requestBody;
    }

    /**
     * Build HTTP headers for OpenRouter
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    /**
     * Handle API response
     */
    private String handleApiResponse(ResponseEntity<String> response) {
        if (response.getStatusCode() == HttpStatus.OK) {
            return extractGeneratedText(response.getBody());
        } else {
            log.error("OpenRouter API error - Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
            throw new RuntimeException("OpenRouter API returned status: " + response.getStatusCode());
        }
    }

    /**
     * Extract generated text from OpenRouter API response
     */
    private String extractGeneratedText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check for errors
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                String errorMessage = errorNode.has("message") ?
                        errorNode.get("message").asText() : "Unknown error";
                log.error("OpenRouter API error: {}", errorMessage);
                throw new RuntimeException("OpenRouter API error: " + errorMessage);
            }

            // Extract text from choices
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");

                if (message != null && message.has("content")) {
                    String text = message.get("content").asText().trim();
                    log.info("Successfully generated text with Llama 3.1, length: {}", text.length());
                    return text;
                }

                // Check for content filtering
                JsonNode finishReason = firstChoice.get("finish_reason");
                if (finishReason != null && "content_filter".equals(finishReason.asText())) {
                    log.warn("Response filtered by content safety");
                    return "Response filtered due to content safety policies.";
                }
            }

            log.error("Unexpected response format from OpenRouter");
            throw new RuntimeException("Unexpected response format from OpenRouter API");

        } catch (Exception e) {
            log.error("Failed to parse OpenRouter API response", e);
            throw new RuntimeException("Failed to parse API response: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback response when API fails
     */
    private String generateFallbackResponse(String prompt) {
        log.warn("Using fallback response for AI summary (Llama 3.1 API unavailable)");

        // Simple extraction of patient info for fallback
        String patientInfo = extractPatientInfoForFallback(prompt);
        String currentTime = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM/dd/yyyy, hh:mm:ss a")
        );

        return buildFallbackSummary(patientInfo, currentTime);
    }

    /**
     * Extract basic patient info from prompt for fallback
     */
    private String extractPatientInfoForFallback(String prompt) {
        try {
            if (prompt.contains("Name: ")) {
                int start = prompt.indexOf("Name: ") + 6;
                int end = prompt.indexOf("|", start);
                if (end > start) {
                    return prompt.substring(start, end).trim();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract patient name from prompt");
        }
        return "the patient";
    }

    /**
     * Build fallback summary template
     */
    private String buildFallbackSummary(String patientName, String currentTime) {
        return String.format("""
                # AI Support – Patient Summary
                
                Legacy Format  
                Updated: %s  
                
                ---
                
                ## Attention Level (Moderate Attention)
                Based on analysis of treatment history and data quality  
                
                ---
                
                ### Using Legacy Format
                AI service temporarily unavailable. Please review records manually.
                
                ---
                
                ### Overview
                %s has a scheduled appointment. AI analysis service is currently unavailable.
                
                ---
                
                ### Alerts
                Manual review required - AI service unavailable
                
                ---
                
                ### Recent Treatments
                Refer to patient's treatment history for details
                """, currentTime, patientName);
    }

    /**
     * Simple validation of API configuration
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Get model information
     */
    public String getModelInfo() {
        return String.format("OpenRouter API - Model: %s", MODEL);
    }
}