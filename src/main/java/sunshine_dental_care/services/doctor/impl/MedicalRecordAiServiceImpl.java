package sunshine_dental_care.services.doctor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.config.OpenRouterProperties;
import sunshine_dental_care.dto.medical.GeminiAiResponseDto;
import sunshine_dental_care.dto.medical.GeneratedMedicalRecordDto;
import sunshine_dental_care.dto.medical.PrescriptionItemDto;
import sunshine_dental_care.services.doctor.MedicalRecordAiService;

@Slf4j
@Service
public class MedicalRecordAiServiceImpl implements MedicalRecordAiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenRouterProperties openRouterProperties;

    public MedicalRecordAiServiceImpl(OpenRouterProperties openRouterProperties,
                                      RestTemplate restTemplate,
                                      ObjectMapper objectMapper) {
        this.openRouterProperties = openRouterProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedMedicalRecordDto generateFromDiagnosis(Integer appointmentId, String diagnosis, Integer doctorId) {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new IllegalArgumentException("Diagnosis is required");
        }

        String prompt = buildPrompt(diagnosis);
        String rawResponse = callOpenRouter(prompt);
        GeminiAiResponseDto parsedResponse = parseAndValidateAiResponse(rawResponse);
        String formattedPrescription = formatPrescription(parsedResponse.getPrescriptionNote());

        return GeneratedMedicalRecordDto.builder()
                .treatmentPlan(parsedResponse.getTreatmentPlan())
                .prescriptionNote(parsedResponse.getPrescriptionNote())
                .prescriptionNoteFormatted(formattedPrescription)
                .note(parsedResponse.getNote())
                .build();
    }

    private String buildPrompt(String diagnosis) {
        return String.format("""
        You are a clinical dentist directly examining and treating a patient.
        Your task is to create a complete, professional, and personalized dental medical record based on the initial diagnosis.
        
        **PRINCIPLES:**
        1. Output language MUST match the language of the input diagnosis.
        2. Use professional dental medical terminology appropriate to that language.
        3. DO NOT diagnose conditions beyond the information provided.
        4. DO NOT prescribe controlled substances.
        5. If essential information is missing, note it specifically in the "note" section.
        6. Always include follow-up advice and safety notes.
        7. Format prescriptions clearly with specific dosage, quantity, and usage instructions.
        
        **DENTAL MEDICAL RECORD STRUCTURE:**
        - treatmentPlan: Detailed treatment plan, broken into phases if needed.
        - prescriptionNote: Medication prescriptions (if needed) with: drug name, dosage, quantity, usage instructions.
        - note: Clinical notes, patient recommendations, follow-up schedule, and AI limitations disclaimer.
        
        **REQUIRED JSON FORMAT:**
        {
          "treatmentPlan": "string (detailed, may include multiple steps)",
          "prescriptionNote": [
            {
              "drugName": "string (exact medication name)",
              "dosage": "string (e.g., 500mg, 0.12% gel)",
              "quantity": number (quantity units: tablets, tubes, bottles...),
              "usageInstruction": "string (specific instructions: times per day, after meals...)"
            }
          ],
          "note": "string (complete notes including follow-up advice)"
        }
        
        **IMPORTANT NOTES:**
        - If no medication is needed: set "prescriptionNote" to empty array []
        - Always return exactly the JSON structure above, with no additional text.
        - Use formal, clear medical language.
        - Specify follow-up appointment schedule in the note section.
        
        **Dentist's Diagnosis:**
        "%s"
        
        Create a complete dental medical record based on the above diagnosis.
        """, escapeForPrompt(diagnosis));
    }
    private String callOpenRouter(String prompt) {
        String apiKey = openRouterProperties.getApiKey();
        String model = openRouterProperties.getModel();

        if (apiKey == null || apiKey.isBlank()) {
            // Check environment variable as fallback
            apiKey = System.getenv("OPENROUTER_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("OpenRouter API key is not configured. " +
                        "Please set openrouter.api.key in application.properties or OPENROUTER_API_KEY environment variable.");
            }
        }

        // Use default model if not configured
        if (model == null || model.isBlank()) {
            model = "google/gemini-2.0-flash-exp:free";
        }

        String apiUrl = "https://openrouter.ai/api/v1/chat/completions";

        log.debug("Calling OpenRouter API with model: {}", model);

        try {
            // Build request body for OpenRouter
            Map<String, Object> requestBody = new HashMap<>();

            // Add model
            requestBody.put("model", model);

            // Add messages
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            requestBody.put("messages", messages);

            // Add parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("max_tokens", 800);
            parameters.put("temperature", 0.7);
            parameters.put("response_format", Map.of("type", "json_object"));
            requestBody.put("response_format", Map.of("type", "json_object"));

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer", "http://localhost:3000"); // Required by OpenRouter
            headers.set("X-Title", "Sunshine Dental Care"); // Optional but recommended

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Make API call
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                throw new IllegalStateException("OpenRouter API returned non-OK status: " + responseEntity.getStatusCode());
            }

            String responseBody = responseEntity.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Empty response from OpenRouter API");
            }

            log.debug("OpenRouter raw response: {}", responseBody);

            // Extract JSON from OpenRouter response
            return extractJsonFromOpenRouterResponse(responseBody);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenRouter API HTTP error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new IllegalStateException("AI service error: " + e.getMessage(), e);
        } catch (RestClientException e) {
            log.error("Failed to call OpenRouter API", e);
            throw new IllegalStateException("Failed to call AI service: " + e.getMessage(), e);
        }
    }

    private String extractJsonFromOpenRouterResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                String content = message.path("content").asText();

                if (content != null && !content.isBlank()) {
                    // OpenRouter sometimes returns JSON wrapped in code blocks
                    content = content.trim();

                    // Remove markdown code blocks if present
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    }
                    if (content.startsWith("```")) {
                        content = content.substring(3);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }

                    return content.trim();
                }
            }

            // Fallback: try to extract JSON from text
            return extractJsonFromText(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse OpenRouter response", e);
            return extractJsonFromText(response);
        }
    }

    private GeminiAiResponseDto parseAndValidateAiResponse(String aiResponseText) {
        if (aiResponseText == null || aiResponseText.isBlank()) {
            throw new AiResponseInvalidException("Empty AI response");
        }

        try {
            // Parse the JSON response
            JsonNode responseJson = objectMapper.readTree(aiResponseText);

            // Extract and validate fields
            String treatmentPlan = getRequiredText(responseJson, "treatmentPlan");
            String note = getRequiredText(responseJson, "note");

            JsonNode prescriptionArray = responseJson.get("prescriptionNote");

            List<PrescriptionItemDto> prescriptionItems = new ArrayList<>();

            if (prescriptionArray != null && prescriptionArray.isArray()) {
                int itemIndex = 0;
                for (JsonNode itemNode : prescriptionArray) {
                    itemIndex++;

                    String drugName = getRequiredText(itemNode, "drugName");
                    String dosage = getOptionalText(itemNode, "dosage", "");
                    String usageInstruction = getOptionalText(itemNode, "usageInstruction", "");

                    // Parse quantity with heuristics to recover swapped fields
                    Integer quantity = null;
                    JsonNode qNode = itemNode.get("quantity");
                    if (qNode != null) {
                        if (qNode.canConvertToInt()) {
                            quantity = qNode.asInt();
                        } else if (qNode.isTextual()) {
                            String qText = qNode.asText().trim();
                            Integer fromQText = extractFirstInteger(qText);
                            if (fromQText != null) {
                                quantity = fromQText;
                            }
                        }
                    }

                    // If quantity still missing, try to extract from dosage (possible swap)
                    if (quantity == null) {
                        Integer fromDosage = extractFirstInteger(dosage);
                        String qText = (qNode != null && qNode.isTextual()) ? qNode.asText().trim() : "";
                        if (fromDosage != null && (qText.isEmpty() || containsLetters(qText) || !isNumericOnly(dosage))) {
                            // Use numeric part of dosage as quantity and move textual qNode to dosage if present
                            quantity = fromDosage;
                            if (!qText.isEmpty()) {
                                dosage = qText;
                                log.warn("Detected swapped dosage/quantity fields in AI response for item {}. Swapped values.", itemIndex);
                            } else {
                                // remove numeric part from dosage if it consisted only of a number
                                if (isNumericOnly(dosage)) {
                                    dosage = "";
                                }
                                log.warn("Using numeric dosage as quantity for item {}", itemIndex);
                            }
                        }
                    }

                    // Final validation
                    if (quantity == null || quantity <= 0) {
                        throw new AiResponseInvalidException(
                                "Invalid quantity at prescription item " + itemIndex + ": " + quantity);
                    }

                    prescriptionItems.add(PrescriptionItemDto.builder()
                            .drugName(drugName)
                            .dosage(dosage)
                            .quantity(quantity)
                            .usageInstruction(usageInstruction)
                            .build());
                }
            } else {
                // If AI omitted prescriptionNote or it's not an array, treat as empty list
                log.warn("AI response missing or invalid 'prescriptionNote'; treating as empty list. Response: {}", responseJson.toString());
            }

            // Build and return the DTO
            return GeminiAiResponseDto.builder()
                    .treatmentPlan(treatmentPlan)
                    .prescriptionNote(prescriptionItems)
                    .note(note)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI JSON response: {}", aiResponseText, e);
            throw new AiResponseInvalidException("Invalid JSON from AI: " + e.getMessage(), e);
        }
    }

    private String formatPrescription(List<PrescriptionItemDto> prescriptionItems) {
        if (prescriptionItems == null || prescriptionItems.isEmpty()) {
            return "";
        }

        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < prescriptionItems.size(); i++) {
            PrescriptionItemDto item = prescriptionItems.get(i);

            if (i > 0) {
                formatted.append("\n");
            }

            formatted.append(i + 1)
                    .append(". ")
                    .append(item.getDrugName());

            if (item.getDosage() != null && !item.getDosage().isBlank()) {
                formatted.append(" ").append(item.getDosage());
            }

            formatted.append(" • Qty: ").append(item.getQuantity());

            if (item.getUsageInstruction() != null && !item.getUsageInstruction().isBlank()) {
                formatted.append(" • Instructions: ").append(item.getUsageInstruction());
            }
        }

        return formatted.toString();
    }

    // Helper methods for JSON parsing
    private String getRequiredText(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new AiResponseInvalidException("Missing required field: " + fieldName);
        }
        String value = fieldNode.asText("").trim();
        if (value.isEmpty()) {
            throw new AiResponseInvalidException("Empty required field: " + fieldName);
        }
        return value;
    }

    private String getOptionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asText(defaultValue).trim();
    }

    private Integer getRequiredInteger(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new AiResponseInvalidException("Missing required field: " + fieldName);
        }
        if (!fieldNode.canConvertToInt()) {
            throw new AiResponseInvalidException("Invalid integer value for field: " + fieldName);
        }
        return fieldNode.asInt();
    }

    // Helper: extract the first integer from a text string (e.g., "20 viên" -> 20)
    private Integer extractFirstInteger(String s) {
        if (s == null || s.isBlank()) return null;
        Pattern p = Pattern.compile("(\\d+)");
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find()) {
            try {
                return Integer.valueOf(m.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    // Helper: detect if a string contains any letters (unicode-aware)
    private boolean containsLetters(String s) {
        if (s == null || s.isBlank()) return false;
        return s.matches(".*\\p{L}.*");
    }

    // Helper: detect if the string contains only digits (with optional whitespace)
    private boolean isNumericOnly(String s) {
        if (s == null) return false;
        return s.matches("^\\s*\\d+\\s*$");
    }

    private String escapeForPrompt(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractJsonFromText(String text) {
        if (text == null) {
            return "";
        }

        // Find first '{' and last '}'
        int startIndex = text.indexOf('{');
        int endIndex = text.lastIndexOf('}');

        if (startIndex >= 0 && endIndex >= 0 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1);
        }

        return text;
    }
}