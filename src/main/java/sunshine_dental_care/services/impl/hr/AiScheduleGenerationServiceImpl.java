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

    // Tạo lập lịch hàng tuần từ mô tả tiếng Anh của user
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description/prompt cannot be null or empty");
        }
        try {
            // Bước 1: Xây dựng prompt đầy đủ cho AI từ mô tả gốc
            String enhancedPrompt = promptBuilderService.buildEnhancedPrompt(description, weekStart);

            // Bước 2: Gửi prompt sang AI để lấy kết quả lịch
            String aiResponse = geminiApiClient.generateContent(enhancedPrompt);
            if (aiResponse == null) {
                throw new RuntimeException("AI service is unavailable or failed to generate a response.");
            }

            // Bước 3: Parse kết quả trả về từ AI và kiểm tra hợp lệ
            CreateWeeklyScheduleRequest request = parseAndValidateResponse(aiResponse, weekStart, description);
            return request;
        } catch (Exception e) {
            // Xử lý lỗi AI và ném ra lỗi phù hợp hiển thị cho người dùng
            throw new RuntimeException("Failed to generate schedule using AI: " + e.getMessage(), e);
        }
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
