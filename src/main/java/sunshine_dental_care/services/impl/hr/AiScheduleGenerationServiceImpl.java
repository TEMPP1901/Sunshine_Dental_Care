package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.services.interfaces.hr.AiScheduleGenerationService;
import sunshine_dental_care.services.interfaces.hr.HrService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiScheduleGenerationServiceImpl implements AiScheduleGenerationService {

    private final HrService hrService;
    private final ScheduleHeuristicsValidator scheduleHeuristicsValidator;
    private final PromptBuilderService promptBuilderService;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description) {
        log.info("Generating schedule from user prompt for week starting: {}", weekStart);

        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description/prompt cannot be null or empty");
        }

        try {
            // Step 1: Build the detailed prompt
            String enhancedPrompt = promptBuilderService.buildEnhancedPrompt(description, weekStart);

            // Step 2: Call the AI API
            String aiResponse = geminiApiClient.generateContent(enhancedPrompt);
            if (aiResponse == null) {
                throw new RuntimeException("AI service is unavailable or failed to generate a response.");
            }

            // Step 3: Parse and validate the response
            CreateWeeklyScheduleRequest request = parseAndValidateResponse(aiResponse, weekStart, description);
            
            log.info("Successfully generated and validated schedule from user prompt.");
            return request;

        } catch (Exception e) {
            log.error("Error during AI schedule generation: {}", e.getMessage(), e);
            // Re-throw with a user-friendly message
            throw new RuntimeException("Failed to generate schedule using AI: " + e.getMessage(), e);
        }
    }

    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromCustomPrompt(LocalDate weekStart, String customPrompt) {
        // This method can be simplified or merged if the only difference is the prompt
        log.info("Generating schedule from custom prompt for week starting: {}", weekStart);
        return generateScheduleFromDescription(weekStart, customPrompt);
    }

    private CreateWeeklyScheduleRequest parseAndValidateResponse(String aiResponse, LocalDate weekStart, String originalPrompt) throws JsonProcessingException {
        log.info("Parsing AI response (length: {})", aiResponse != null ? aiResponse.length() : 0);
        log.debug("Raw AI response (first 1000 chars): {}", 
            aiResponse != null && aiResponse.length() > 1000 ? aiResponse.substring(0, 1000) + "..." : aiResponse);
        
        // Step 3a: Extract JSON from the raw AI response
        String jsonText = extractJsonFromText(aiResponse);
        if (jsonText == null || jsonText.isEmpty()) {
            log.error("Could not extract valid JSON from AI response.");
            log.error("Response length: {}", aiResponse != null ? aiResponse.length() : 0);
            log.error("Response content: {}", aiResponse);
            throw new RuntimeException("AI did not return a valid schedule format. " +
                "Please ensure the AI response contains valid JSON with 'dailyAssignments' field.");
        }
        
        log.debug("Extracted JSON text (length: {}, first 500 chars: {})", 
            jsonText.length(),
            jsonText.length() > 500 ? jsonText.substring(0, 500) + "..." : jsonText);

        // Step 3b: Parse the JSON into a request object
        CreateWeeklyScheduleRequest request = parseJsonToRequest(jsonText, weekStart);

        // Step 3c: Perform hard validation (business rules)
        ValidationResultDto hardValidation = hrService.validateSchedule(request);
        if (!hardValidation.isValid()) {
            log.warn("AI-generated schedule failed hard validation: {}", hardValidation.getErrors());
            throw new IllegalArgumentException("Generated schedule is invalid: " + String.join(", ", hardValidation.getErrors()));
        }

        // Step 3d: Perform soft validation (heuristics and quality checks)
        ValidationResultDto softValidation = scheduleHeuristicsValidator.validate(request, originalPrompt);
        if (!softValidation.getWarnings().isEmpty()) {
            log.warn("AI-generated schedule has quality warnings: {}", softValidation.getWarnings());
            // Optionally, attach warnings to the request object if the DTO supports it
            // request.setWarnings(softValidation.getWarnings());
        }
        
        return request;
    }

    private CreateWeeklyScheduleRequest parseJsonToRequest(String jsonText, LocalDate weekStart) throws JsonProcessingException {
        log.debug("Parsing JSON text (length: {}, first 500 chars: {})", 
            jsonText.length(), 
            jsonText.length() > 500 ? jsonText.substring(0, 500) + "..." : jsonText);
        
        JsonNode rootNode = objectMapper.readTree(jsonText);
        
        // Log available fields for debugging
        List<String> availableFields = new ArrayList<>();
        rootNode.fieldNames().forEachRemaining(availableFields::add);
        log.debug("Available fields in JSON root: {}", String.join(", ", availableFields));
        
        JsonNode dailyAssignmentsNode = rootNode.path("dailyAssignments");

        if (dailyAssignmentsNode.isMissingNode() || dailyAssignmentsNode.isNull()) {
            log.error("AI response is missing the 'dailyAssignments' field.");
            log.error("Full JSON response: {}", jsonText);
            log.error("Available fields in root: {}", String.join(", ", availableFields));
            
            // Try to provide helpful error message
            StringBuilder errorMsg = new StringBuilder("AI response is missing the 'dailyAssignments' field.");
            if (!availableFields.isEmpty()) {
                errorMsg.append(" Available fields: ").append(String.join(", ", availableFields));
            } else {
                errorMsg.append(" JSON appears to be empty or invalid.");
            }
            
            throw new IllegalArgumentException(errorMsg.toString());
            }

            Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = new HashMap<>();
        dailyAssignmentsNode.fields().forEachRemaining(entry -> {
            String day = entry.getKey();
            JsonNode assignmentsNode = entry.getValue();
            if (assignmentsNode.isArray()) {
                    List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = new ArrayList<>();
                assignmentsNode.forEach(node -> {
                    CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment = new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                    assignment.setDoctorId(node.path("doctorId").asInt());
                    assignment.setClinicId(node.path("clinicId").asInt());
                    assignment.setRoomId(node.path("roomId").asInt());
                    assignment.setStartTime(LocalTime.parse(node.path("startTime").asText()));
                    assignment.setEndTime(LocalTime.parse(node.path("endTime").asText()));
                        dayAssignments.add(assignment);
                });
                    if (!dayAssignments.isEmpty()) {
                        dailyAssignments.put(day, dayAssignments);
                    }
                }
        });

            CreateWeeklyScheduleRequest request = new CreateWeeklyScheduleRequest();
            request.setWeekStart(weekStart);
            request.setDailyAssignments(dailyAssignments);
        request.setNote("Auto-generated by AI");
            return request;
    }

    private String extractJsonFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        // Find the first '{' and the last '}'
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null; // Or more robust extraction logic if needed
    }
}
