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
import sunshine_dental_care.repositories.auth.ClinicRepo;
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
    private final ClinicRepo clinicRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Tạo lập lịch hàng tuần từ mô tả người dùng, tự động gửi đến AI và kiểm tra lại, có retry tự động khi gặp lỗi
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
                String enhancedPrompt = promptBuilderService.buildEnhancedPrompt(description, weekStart);

                // Nếu là retry thì bổ sung feedback cho AI về lỗi trả về ở lần trước
                if (attempt > 1 && lastError != null) {
                    enhancedPrompt = addRetryFeedback(enhancedPrompt, lastError, attempt);
                }

                String aiResponse = geminiApiClient.generateContent(enhancedPrompt);
                if (aiResponse == null) {
                    throw new RuntimeException("AI service is unavailable or failed to generate a response.");
                }

                CreateWeeklyScheduleRequest request = parseAndValidateResponse(aiResponse, weekStart, description);
                log.info("AI Schedule Generation - Success on attempt {}", attempt);
                return request;

            } catch (IllegalArgumentException e) {
                lastError = e.getMessage();
                log.warn("AI Schedule Generation - Attempt {} failed validation: {}", attempt, lastError);

                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to generate valid schedule after " + maxRetries
                            + " attempts. Last error: " + lastError, e);
                }
            } catch (Exception e) {
                log.error("AI Schedule Generation - Fatal error on attempt {}: {}", attempt, e.getMessage());
                throw new RuntimeException("Failed to generate schedule using AI: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Failed to generate schedule using AI after " + maxRetries + " attempts.");
    }

    // Thêm đoạn feedback về lỗi cho prompt gửi lại AI khi retry
    private String addRetryFeedback(String originalPrompt, String errorMessage, int attempt) {
        StringBuilder feedbackPrompt = new StringBuilder(originalPrompt);
        feedbackPrompt.append("\n\n");
        feedbackPrompt.append("=== RETRY ATTEMPT ").append(attempt).append(" - PREVIOUS ATTEMPT FAILED ===\n");
        feedbackPrompt.append("The previous schedule generation was REJECTED due to the following errors:\n");
        feedbackPrompt.append(errorMessage).append("\n");
        feedbackPrompt.append("\n");
        feedbackPrompt.append("CRITICAL: You MUST fix these errors in this attempt:\n");
        feedbackPrompt.append(
                "1. If the error mentions 'Doctor ID X not found', you used an invalid ID. Check the 'AVAILABLE DOCTORS' list and use ONLY valid IDs.\n");
        feedbackPrompt.append(
                "2. If the error mentions 'missing schedule for [day]', you must add assignments for that day.\n");
        feedbackPrompt.append(
                "3. If the error mentions 'must have at least 2 doctors assigned', you must add more doctors to that shift.\n");
        feedbackPrompt.append("4. Ensure ALL doctors work on ALL working days (every doctor, every day).\n");
        feedbackPrompt.append("5. Verify EVERY doctorId in your JSON exists in the 'AVAILABLE DOCTORS' list.\n");
        feedbackPrompt.append("\n");
        feedbackPrompt.append(
                "Please regenerate the schedule with ALL errors fixed. Your response must be complete and valid.\n");
        return feedbackPrompt.toString();
    }

    // Sinh lịch từ prompt do người dùng tự nhập (bản chất giống generateScheduleFromDescription)
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromCustomPrompt(LocalDate weekStart, String customPrompt) {
        return generateScheduleFromDescription(weekStart, customPrompt);
    }

    // Phân tích và kiểm tra response từ AI (gồm cả kiểm tra cứng & cảnh báo mềm)
    private CreateWeeklyScheduleRequest parseAndValidateResponse(String aiResponse, LocalDate weekStart,
            String originalPrompt) throws JsonProcessingException {
        String jsonText = extractJsonFromText(aiResponse);
        if (jsonText == null || jsonText.isEmpty()) {
            throw new RuntimeException(
                    "AI did not return a valid schedule format. Please ensure the AI response contains valid JSON with 'dailyAssignments' field.");
        }

        CreateWeeklyScheduleRequest request = parseJsonToRequest(jsonText, weekStart);

        // Kiểm tra hợp lệ cứng (các luật bắt buộc)
        ValidationResultDto hardValidation = hrService.validateSchedule(request);
        if (!hardValidation.isValid()) {
            throw new IllegalArgumentException(
                    "Generated schedule is invalid: " + String.join(", ", hardValidation.getErrors()));
        }

        // Kiểm tra hợp lệ mềm (gợi ý/cảnh báo, không bắt buộc dừng)
        ValidationResultDto softValidation = scheduleHeuristicsValidator.validate(request, originalPrompt);
        // Nếu cần có thể lưu cảnh báo vào DTO (chưa sử dụng)
        return request;
    }

    // Parse JSON từ response AI -> Kiểm tra các ngày làm việc & logic clinic nghỉ/làm
    private CreateWeeklyScheduleRequest parseJsonToRequest(String jsonText, LocalDate weekStart)
            throws JsonProcessingException {
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

        // Xác định danh sách ngày phải làm việc (ngoại trừ các ngày mà toàn bộ phòng khám nghỉ)
        String[] dayNames = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        List<String> expectedWorkingDays = new ArrayList<>();
        List<sunshine_dental_care.entities.Clinic> allClinics = clinicRepo.findAll();
        for (int dayIndex = 0; dayIndex < dayNames.length; dayIndex++) {
            String day = dayNames[dayIndex];
            LocalDate workDate = weekStart.plusDays(dayIndex);

            // Kiểm tra nếu tất cả phòng khám đều nghỉ ngày này thì loại khỏi workingDays
            boolean allClinicsOnHoliday = true;
            for (sunshine_dental_care.entities.Clinic clinic : allClinics) {
                if (!holidayService.isHoliday(workDate, clinic.getId())) {
                    allClinicsOnHoliday = false;
                    break;
                }
            }
            if (!allClinicsOnHoliday) {
                expectedWorkingDays.add(day);
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

        // Kiểm tra thiếu ngày (ngày phải làm mà không có lịch)
        List<String> missingDays = new ArrayList<>();
        for (String expectedDay : expectedWorkingDays) {
            if (!dailyAssignments.containsKey(expectedDay)) {
                missingDays.add(expectedDay);
            }
        }

        // Nếu thiếu bất kỳ ngày đang có phòng khám làm việc nào thì ném lỗi
        if (!missingDays.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Missing schedule for working day(s): ");
            errorMsg.append(String.join(", ", missingDays)).append(". ");
            errorMsg.append("You must schedule ALL working days where at least one clinic is active: ");
            errorMsg.append(String.join(", ", expectedWorkingDays)).append(". ");
            errorMsg.append("Note: Days where ALL clinics are on holiday are automatically skipped.");

            throw new IllegalArgumentException(errorMsg.toString());
        }

        CreateWeeklyScheduleRequest request = new CreateWeeklyScheduleRequest();
        request.setWeekStart(weekStart);
        request.setDailyAssignments(dailyAssignments);
        request.setNote("Auto-generated by AI");
        return request;
    }

    // Tách chuỗi JSON từ response của AI dựa theo vị trí dấu ngoặc nhọn đầu/cuối
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
