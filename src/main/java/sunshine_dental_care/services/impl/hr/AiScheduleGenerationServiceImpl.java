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
    private final HolidayService holidayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Tạo lập lịch hàng tuần từ mô tả tiếng Anh của user
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description/prompt cannot be null or empty");
        }
        
        int maxRetries = 3;
        String lastError = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("AI Schedule Generation - Attempt {}/{}", attempt, maxRetries);
                
                // Bước 1: Xây dựng prompt đầy đủ cho AI từ mô tả gốc
                String enhancedPrompt = promptBuilderService.buildEnhancedPrompt(description, weekStart);
                
                // Nếu đây là retry, thêm feedback về lỗi trước đó
                if (attempt > 1 && lastError != null) {
                    enhancedPrompt = addRetryFeedback(enhancedPrompt, lastError, attempt);
                }

                // Bước 2: Gửi prompt sang AI để lấy kết quả lịch
                String aiResponse = geminiApiClient.generateContent(enhancedPrompt);
                if (aiResponse == null) {
                    throw new RuntimeException("AI service is unavailable or failed to generate a response.");
                }

                // Bước 3: Parse kết quả trả về từ AI và kiểm tra hợp lệ
                CreateWeeklyScheduleRequest request = parseAndValidateResponse(aiResponse, weekStart, description);
                log.info("AI Schedule Generation - Success on attempt {}", attempt);
                return request;
                
            } catch (IllegalArgumentException e) {
                // Validation failed - lưu lỗi để retry với feedback
                lastError = e.getMessage();
                log.warn("AI Schedule Generation - Attempt {} failed validation: {}", attempt, lastError);
                
                if (attempt == maxRetries) {
                    // Đã hết số lần thử, throw lỗi cuối cùng
                    throw new RuntimeException("Failed to generate valid schedule after " + maxRetries + " attempts. Last error: " + lastError, e);
                }
                // Tiếp tục retry
            } catch (Exception e) {
                // Lỗi khác (network, parsing, etc.) - không retry
                log.error("AI Schedule Generation - Fatal error on attempt {}: {}", attempt, e.getMessage());
                throw new RuntimeException("Failed to generate schedule using AI: " + e.getMessage(), e);
            }
        }
        
        // Không bao giờ đến đây, nhưng compiler cần return
        throw new RuntimeException("Failed to generate schedule using AI after " + maxRetries + " attempts.");
    }
    
    // Thêm feedback về lỗi vào prompt khi retry
    private String addRetryFeedback(String originalPrompt, String errorMessage, int attempt) {
        StringBuilder feedbackPrompt = new StringBuilder(originalPrompt);
        feedbackPrompt.append("\n\n");
        feedbackPrompt.append("=== RETRY ATTEMPT ").append(attempt).append(" - PREVIOUS ATTEMPT FAILED ===\n");
        feedbackPrompt.append("The previous schedule generation was REJECTED due to the following errors:\n");
        feedbackPrompt.append(errorMessage).append("\n");
        feedbackPrompt.append("\n");
        feedbackPrompt.append("CRITICAL: You MUST fix these errors in this attempt:\n");
        feedbackPrompt.append("1. If the error mentions 'Doctor ID X not found', you used an invalid ID. Check the 'AVAILABLE DOCTORS' list and use ONLY valid IDs.\n");
        feedbackPrompt.append("2. If the error mentions 'missing schedule for [day]', you must add assignments for that day.\n");
        feedbackPrompt.append("3. If the error mentions 'must have at least 2 doctors assigned', you must add more doctors to that shift.\n");
        feedbackPrompt.append("4. Ensure ALL doctors work on ALL working days (every doctor, every day).\n");
        feedbackPrompt.append("5. Verify EVERY doctorId in your JSON exists in the 'AVAILABLE DOCTORS' list.\n");
        feedbackPrompt.append("\n");
        feedbackPrompt.append("Please regenerate the schedule with ALL errors fixed. Your response must be complete and valid.\n");
        return feedbackPrompt.toString();
    }

    // Sinh lịch từ custom prompt tiếng Anh (logic giống generateScheduleFromDescription)
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromCustomPrompt(LocalDate weekStart, String customPrompt) {
        return generateScheduleFromDescription(weekStart, customPrompt);
    }

    // Parse kết quả trả về của AI, validate cứng và validate mềm
    private CreateWeeklyScheduleRequest parseAndValidateResponse(String aiResponse, LocalDate weekStart, String originalPrompt) throws JsonProcessingException {
        String jsonText = extractJsonFromText(aiResponse);
        if (jsonText == null || jsonText.isEmpty()) {
            throw new RuntimeException("AI did not return a valid schedule format. Please ensure the AI response contains valid JSON with 'dailyAssignments' field.");
        }

        CreateWeeklyScheduleRequest request = parseJsonToRequest(jsonText, weekStart);

        // Hard validation theo quy tắc nghiệp vụ
        ValidationResultDto hardValidation = hrService.validateSchedule(request);
        if (!hardValidation.isValid()) {
            throw new IllegalArgumentException("Generated schedule is invalid: " + String.join(", ", hardValidation.getErrors()));
        }

        // Soft validation kiểm tra chất lượng & cảnh báo (không ném lỗi)
        ValidationResultDto softValidation = scheduleHeuristicsValidator.validate(request, originalPrompt);
        // Nếu muốn attach cảnh báo vào DTO: request.setWarnings(softValidation.getWarnings());
        return request;
    }

    // Parse JSON string về CreateWeeklyScheduleRequest, kiểm tra tính hợp lệ của cấu trúc ngày và lịch
    private CreateWeeklyScheduleRequest parseJsonToRequest(String jsonText, LocalDate weekStart) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(jsonText);

        List<String> availableFields = new ArrayList<>();
        rootNode.fieldNames().forEachRemaining(availableFields::add);

        JsonNode dailyAssignmentsNode = rootNode.path("dailyAssignments");

        if (dailyAssignmentsNode.isMissingNode() || dailyAssignmentsNode.isNull()) {
            StringBuilder errorMsg = new StringBuilder("AI response is missing the 'dailyAssignments' field.");
            if (!availableFields.isEmpty()) {
                errorMsg.append(" Available fields: ").append(String.join(", ", availableFields));
            } else {
                errorMsg.append(" JSON appears to be empty or invalid.");
            }
            throw new IllegalArgumentException(errorMsg.toString());
        }

        // Calculate expected working days (excluding holidays)
        String[] dayNames = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        List<String> expectedWorkingDays = new ArrayList<>();
        List<String> holidayDays = holidayService.calculateHolidaysInWeek(weekStart);
        for (int i = 0; i < dayNames.length; i++) {
            // Only add non-holiday days to expected working days
            if (!holidayDays.contains(dayNames[i])) {
                expectedWorkingDays.add(dayNames[i]);
            }
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

        // Validate that we have schedules for ALL expected working days
        List<String> missingDays = new ArrayList<>();
        for (String expectedDay : expectedWorkingDays) {
            if (!dailyAssignments.containsKey(expectedDay)) {
                missingDays.add(expectedDay);
            }
        }
        
        // If we're missing any working days, that's an error
        if (!missingDays.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Missing schedule for working day(s): ");
            errorMsg.append(String.join(", ", missingDays)).append(". ");
            errorMsg.append("You must schedule ALL ").append(expectedWorkingDays.size()).append(" working days: ");
            errorMsg.append(String.join(", ", expectedWorkingDays)).append(". ");
            
            // Special emphasis for Friday/Saturday after holiday
            boolean missingFriday = missingDays.contains("friday");
            boolean missingSaturday = missingDays.contains("saturday");
            if ((missingFriday || missingSaturday) && !holidayDays.isEmpty()) {
                errorMsg.append("CRITICAL: If there was a holiday earlier in the week, you MUST still schedule Friday and Saturday. ");
                errorMsg.append("A holiday does NOT end the work week.");
            }
            
            throw new IllegalArgumentException(errorMsg.toString());
        }

        CreateWeeklyScheduleRequest request = new CreateWeeklyScheduleRequest();
        request.setWeekStart(weekStart);
        request.setDailyAssignments(dailyAssignments);
        request.setNote("Auto-generated by AI");
        return request;
    }

    // Trích xuất JSON từ response thô của AI (dò từ dấu '{' đầu về '}' cuối)
    private String extractJsonFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null;
    }
}
