package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.AiScheduleGenerationService;
import sunshine_dental_care.services.interfaces.hr.HrService;

@Service
@Slf4j
public class AiScheduleGenerationServiceImpl implements AiScheduleGenerationService {

    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final HrService hrService;

    public AiScheduleGenerationServiceImpl(
        UserRepo userRepo,
        ClinicRepo clinicRepo,
        RoomRepo roomRepo,
        DoctorSpecialtyRepo doctorSpecialtyRepo,
        UserRoleRepo userRoleRepo,
        DoctorScheduleRepo doctorScheduleRepo,
        LeaveRequestRepo leaveRequestRepo,
        @Lazy HrService hrService
    ) {
        this.userRepo = userRepo;
        this.clinicRepo = clinicRepo;
        this.roomRepo = roomRepo;
        this.doctorSpecialtyRepo = doctorSpecialtyRepo;
        this.userRoleRepo = userRoleRepo;
        this.doctorScheduleRepo = doctorScheduleRepo;
        this.leaveRequestRepo = leaveRequestRepo;
        this.hrService = hrService;
    }
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    // Phương thức quan trọng: sinh lịch từ mô tả người dùng thông qua AI Gemini
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description) {
        log.info("Generating schedule from user prompt for week starting: {} using Gemini AI", weekStart);
        log.debug("User prompt: {}", description);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            log.error("Gemini API key not configured. Cannot generate schedule without AI.");
            throw new RuntimeException("Gemini API key is not configured. Please configure app.gemini.api-key in application properties.");
        }

        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description/prompt cannot be null or empty");
        }

        try {
            // Tự động thêm hướng dẫn trả về JSON vào prompt người dùng
            String enhancedPrompt = buildEnhancedPrompt(description, weekStart);
            String aiResponse = callGeminiAPI(enhancedPrompt);

            if (aiResponse == null) {
                log.error("Gemini API unavailable. Cannot generate schedule.");
                throw new RuntimeException("Gemini API is unavailable. Please check your API key and network connection.");
            }

            CreateWeeklyScheduleRequest request = parseGeminiResponse(aiResponse, weekStart);

            ValidationResultDto validation = hrService.validateSchedule(request);
            if (!validation.isValid()) {
                log.warn("AI-generated schedule from user prompt failed validation: {}", validation.getErrors());
                throw new RuntimeException("Generated schedule failed validation. Errors: " + String.join(", ", validation.getErrors()));
            }

            log.info("Successfully generated and validated schedule from user prompt using Gemini AI with {} days of assignments", 
                request.getDailyAssignments().size());

            return request;

        } catch (Exception e) {
            log.error("Error calling Gemini API with user prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate schedule using user prompt: " + e.getMessage(), e);
        }
    }

    // Phương thức quan trọng: sinh lịch từ custom prompt của người dùng
    @Override
    public CreateWeeklyScheduleRequest generateScheduleFromCustomPrompt(LocalDate weekStart, String customPrompt) {
        log.info("Generating schedule from custom prompt for week starting: {} using Gemini AI", weekStart);
        log.debug("Custom prompt: {}", customPrompt);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            log.error("Gemini API key not configured. Cannot generate schedule without AI.");
            throw new RuntimeException("Gemini API key is not configured. Please configure app.gemini.api-key in application properties.");
        }

        if (customPrompt == null || customPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Custom prompt cannot be null or empty");
        }

        try {
            String aiResponse = callGeminiAPI(customPrompt);

            if (aiResponse == null) {
                log.error("Gemini API unavailable. Cannot generate schedule.");
                throw new RuntimeException("Gemini API is unavailable. Please check your API key and network connection.");
            }

            CreateWeeklyScheduleRequest request = parseGeminiResponse(aiResponse, weekStart);

            ValidationResultDto validation = hrService.validateSchedule(request);
            if (!validation.isValid()) {
                log.warn("AI-generated schedule from custom prompt failed validation: {}", validation.getErrors());
                throw new RuntimeException("Generated schedule failed validation. Errors: " + String.join(", ", validation.getErrors()));
            }

            log.info("Successfully generated and validated schedule from custom prompt using Gemini AI with {} days of assignments", 
                request.getDailyAssignments().size());

            return request;

        } catch (Exception e) {
            log.error("Error calling Gemini API with custom prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate schedule using custom prompt: " + e.getMessage(), e);
        }
    }

    // Phương thức quan trọng: gửi prompt tới Gemini API, thử nhiều model nếu có lỗi 429/404
    private String callGeminiAPI(String prompt) {
        List<String> availableModels = getAvailableGeminiModels();

        if (availableModels != null && !availableModels.isEmpty()) {
            Exception lastException = null;
            for (String modelName : availableModels) {
                try {
                    String apiUrl = String.format(
                        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
                        modelName
                    );
                    return callGeminiAPIWithUrl(apiUrl, prompt);
                } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests 
                        | org.springframework.web.client.HttpClientErrorException.NotFound e) {
                    if (e instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
                        log.warn("Model {} quota exceeded (429), trying next model...", modelName);
                    } else {
                        log.debug("Model {} not found (404), trying next...", modelName);
                    }
                    lastException = e;
                } catch (Exception e) {
                    log.debug("Error with model {}: {}", modelName, e.getMessage());
                    lastException = e;
                }
            }
            log.warn("All available Gemini models failed. Last error: {}", 
                lastException != null ? lastException.getMessage() : "Unknown");
        } else {
            log.warn("No available Gemini models found.");
        }

        return null;
    }

    // Lấy danh sách các model Gemini có hỗ trợ generateContent
    private List<String> getAvailableGeminiModels() {
        try {
            String listModelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey;
            ResponseEntity<String> response = restTemplate.exchange(
                listModelsUrl,
                HttpMethod.GET,
                null,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode models = rootNode.get("models");

                if (models != null && models.isArray()) {
                    log.info("Found {} available Gemini models", models.size());
                    String[] preferredModels = {
                        "gemini-2.0-flash-exp",
                        "gemini-exp-1206",
                        "gemini-2.0-flash-thinking-exp-1219",
                        "gemini-2.0-flash-thinking-exp",
                        "gemini-exp-1121",
                        "gemini-exp-1114",
                        "gemini-1.5-flash-latest",
                        "gemini-1.5-flash",
                        "gemini-1.5-flash-8b-latest",
                        "gemini-1.5-flash-8b",
                        "gemini-1.5-pro-latest",
                        "gemini-1.5-pro",
                        "gemini-pro"
                    };

                    List<String> supportedModels = new ArrayList<>();
                    for (JsonNode model : models) {
                        String name = model.get("name") != null ? model.get("name").asText() : null;
                        if (name != null) {
                            String modelName = name.replace("models/", "");
                            JsonNode supportedMethods = model.get("supportedGenerationMethods");
                            boolean supportsGenerateContent = false;
                            if (supportedMethods != null && supportedMethods.isArray()) {
                                for (JsonNode method : supportedMethods) {
                                    if ("generateContent".equals(method.asText())) {
                                        supportsGenerateContent = true;
                                        break;
                                    }
                                }
                            }
                            if (supportsGenerateContent && !modelName.contains("embedding") && !modelName.contains("imagen")) {
                                supportedModels.add(modelName);
                                log.debug("Model {} supports generateContent", modelName);
                            }
                        }
                    }

                    List<String> orderedModels = new ArrayList<>();
                    for (String preferred : preferredModels) {
                        for (String supported : supportedModels) {
                            if ((supported.contains(preferred) || supported.equals(preferred)) 
                                && !orderedModels.contains(supported)) {
                                orderedModels.add(supported);
                            }
                        }
                    }

                    for (String supported : supportedModels) {
                        if (!orderedModels.contains(supported)) {
                            orderedModels.add(supported);
                        }
                    }

                    if (!orderedModels.isEmpty()) {
                        log.info("Found {} models supporting generateContent. Will try in order: {}", 
                            orderedModels.size(), orderedModels);
                        return orderedModels;
                    }
                }
            }
        } catch (org.springframework.web.client.RestClientException 
                | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("Failed to list available models: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // Gửi data tới Gemini API với url cụ thể (POST)
    private String callGeminiAPIWithUrl(String apiUrl, String prompt) {
        String url = apiUrl + "?key=" + geminiApiKey;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(part);
        content.put("parts", parts);
        requestBody.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Gemini API: {}", url.replace(geminiApiKey, "***"));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                request, 
                String.class
            );
            log.info("Successfully called Gemini API");
            log.debug("Gemini API response: {}", response.getBody());
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini API error - Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    // Phương thức quan trọng: parse response từ Gemini, trả ra đối tượng CreateWeeklyScheduleRequest chuẩn hóa
    private CreateWeeklyScheduleRequest parseGeminiResponse(String aiResponse, LocalDate weekStart) {
        try {
            JsonNode rootNode = objectMapper.readTree(aiResponse);

            String text = "";
            if (rootNode.has("candidates") && rootNode.get("candidates").isArray() && rootNode.get("candidates").size() > 0) {
                JsonNode candidate = rootNode.get("candidates").get(0);
                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    JsonNode parts = candidate.get("content").get("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        text = parts.get(0).get("text").asText();
                    }
                }
            }

            if (text.isEmpty()) {
                throw new RuntimeException("Empty response from Gemini");
            }

            log.debug("Raw text from Gemini: {}", text);

            String jsonText = extractJsonFromText(text);

            if (jsonText == null || jsonText.isEmpty()) {
                log.error("Could not extract JSON from Gemini response. Response text: {}", text);
                throw new RuntimeException("Gemini did not return valid JSON. Please ensure your prompt asks for JSON format output.");
            }

            JsonNode scheduleNode = objectMapper.readTree(jsonText);
            JsonNode dailyAssignmentsNode = scheduleNode.get("dailyAssignments");

            if (dailyAssignmentsNode == null) {
                throw new RuntimeException("Missing 'dailyAssignments' field in JSON response");
            }

            Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = new HashMap<>();

            String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
            for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
                String day = days[dayIndex];
                LocalDate workDate = weekStart.plusDays(dayIndex);
                if (dailyAssignmentsNode.has(day) && dailyAssignmentsNode.get(day).isArray()) {
                    List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = new ArrayList<>();
                    for (JsonNode assignmentNode : dailyAssignmentsNode.get(day)) {
                        int doctorId = assignmentNode.get("doctorId").asInt();
                        // Nếu bác sĩ có leave request đã duyệt trong ngày này thì bỏ qua
                        boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(doctorId, workDate);
                        if (hasApprovedLeave) {
                            log.info("Filtering out AI-generated assignment for doctor {} on {} - has approved leave request",
                                    doctorId, workDate);
                            continue;
                        }
                        CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment = 
                            new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                        assignment.setDoctorId(doctorId);
                        assignment.setClinicId(assignmentNode.get("clinicId").asInt());
                        assignment.setRoomId(assignmentNode.get("roomId").asInt());
                        assignment.setStartTime(LocalTime.parse(assignmentNode.get("startTime").asText()));
                        assignment.setEndTime(LocalTime.parse(assignmentNode.get("endTime").asText()));
                        dayAssignments.add(assignment);
                    }
                    if (!dayAssignments.isEmpty()) {
                        dailyAssignments.put(day, dayAssignments);
                    }
                }
            }

            CreateWeeklyScheduleRequest request = new CreateWeeklyScheduleRequest();
            request.setWeekStart(weekStart);
            request.setDailyAssignments(dailyAssignments);
            request.setNote("Auto-generated by Gemini AI from user prompt");

            return request;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error parsing Gemini response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error parsing Gemini response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    // Phương thức quan trọng: sinh prompt chuẩn hóa (tiếng Anh), chứa tất cả rule và tài nguyên
    private String buildEnhancedPrompt(String userDescription, LocalDate weekStart) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI-powered HR Schedule Generator for Sunshine Dental Care.\n");
        prompt.append("Your sole output MUST be a perfectly formatted JSON object that adheres to ALL CRITICAL BUSINESS RULES.\n");
        prompt.append("DO NOT output any text, explanation, or markdown wrappers (no ```json or ```).\n\n");

        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("USER REQUEST & WEEK INFO\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("USER REQUEST: ").append(userDescription).append("\n");
        prompt.append("WEEK START DATE: ").append(weekStart).append(" (Monday)\n");
        prompt.append("WEEK END DATE: ").append(weekStart.plusDays(5)).append(" (Saturday)\n\n");

        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("🗓️ AUTO-CALCULATED WORK EXCLUSIONS FOR THIS WEEK\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        LocalDate weekEnd = weekStart.plusDays(5);
        List<String> exclusionDays = calculateHolidaysInWeek(weekStart);
        Map<Integer, List<String>> doctorDaysOff = parseDoctorDaysOff(userDescription, weekStart, exclusionDays);

        List<sunshine_dental_care.entities.DoctorSchedule> existingSchedules = 
            doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);

        prompt.append("👉 WORKING DAYS (MUST be included in JSON keys):\n");
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (int i = 0; i < 6; i++) {
            if (!exclusionDays.contains(dayNames[i])) {
                prompt.append(String.format("  - %s (%s)\n", dayNames[i], weekStart.plusDays(i)));
            }
        }

        if (!exclusionDays.isEmpty()) {
            prompt.append("\n❌ EXCLUDED DAYS (DO NOT include in JSON keys):\n");
            for (int i = 0; i < 6; i++) {
                if (exclusionDays.contains(dayNames[i])) {
                    LocalDate date = weekStart.plusDays(i);
                    String holidayName = getHolidayName(date);
                    prompt.append(String.format("  - %s (%s): %s\n", dayNames[i], date, holidayName));
                }
            }
        }

        if (!doctorDaysOff.isEmpty()) {
            prompt.append("\n⚠️ DOCTOR SPECIFIC DAYS OFF (MUST EXCLUDE DOCTOR):\n");
            for (Map.Entry<Integer, List<String>> entry : doctorDaysOff.entrySet()) {
                int docId = entry.getKey();
                String days = String.join(", ", entry.getValue());
                prompt.append(String.format("  - Doctor ID %d is OFF on: %s\n", docId, days.toUpperCase()));
            }
        }

        if (!existingSchedules.isEmpty()) {
            prompt.append("\n⚠️ EXISTING SCHEDULES (DO NOT CREATE DUPLICATES):\n");
            prompt.append("The following schedules already exist for this week. DO NOT create overlapping assignments:\n");
            Map<LocalDate, List<sunshine_dental_care.entities.DoctorSchedule>> schedulesByDate = new HashMap<>();
            for (var schedule : existingSchedules) {
                schedulesByDate.putIfAbsent(schedule.getWorkDate(), new ArrayList<>());
                schedulesByDate.get(schedule.getWorkDate()).add(schedule);
            }
            for (Map.Entry<LocalDate, List<sunshine_dental_care.entities.DoctorSchedule>> entry : schedulesByDate.entrySet()) {
                LocalDate date = entry.getKey();
                String dayName = getDayNameFromDate(weekStart, date);
                prompt.append(String.format("  - %s (%s):\n", dayName, date));
                for (var schedule : entry.getValue()) {
                    prompt.append(String.format("    • Doctor ID %d: %s-%s at Clinic %d\n", 
                        schedule.getDoctor().getId(), 
                        schedule.getStartTime(), 
                        schedule.getEndTime(),
                        schedule.getClinic().getId()));
                }
            }
            prompt.append("  ⚠️ CRITICAL: DO NOT create any assignments that overlap with existing schedules above!\n");
        }

        prompt.append("═══════════════════════════════════════════════════════════\n\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("📋 AVAILABLE RESOURCES IN SYSTEM\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");

        var allUserRoles = userRoleRepo.findAll();
        var doctors = allUserRoles.stream()
            .filter(ur -> ur.getIsActive() && ur.getRole() != null && "DOCTOR".equals(ur.getRole().getRoleName()))
            .map(ur -> ur.getUser())
            .filter(u -> u != null && u.getIsActive())
            .distinct()
            .toList();

        prompt.append("AVAILABLE DOCTORS (you MUST use ONLY these doctor IDs):\n");
        if (doctors.isEmpty()) {
            prompt.append("  ⚠️ NO DOCTORS AVAILABLE - Cannot create schedule\n");
        } else {
            for (var doctor : doctors) {
                var specialties = doctorSpecialtyRepo.findByDoctorIdAndIsActiveTrue(doctor.getId());
                String specialtyStr = specialties.isEmpty() ? "General" : 
                    String.join(", ", specialties.stream().map(s -> s.getSpecialtyName()).toList());
                prompt.append(String.format("  - Doctor ID: %d, Name: %s, Specialty: %s\n", 
                    doctor.getId(), doctor.getFullName(), specialtyStr));
            }
        }
        prompt.append("\n");

        Map<Integer, List<String>> doctorLeaveDays = new HashMap<>();
        for (var doctor : doctors) {
            List<String> leaveDays = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                LocalDate date = weekStart.plusDays(i);
                boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(doctor.getId(), date);
                if (hasApprovedLeave) {
                    String dayName = dayNames[i];
                    leaveDays.add(dayName);
                }
            }
            if (!leaveDays.isEmpty()) {
                doctorLeaveDays.put(doctor.getId(), leaveDays);
            }
        }
        if (!doctorLeaveDays.isEmpty()) {
            prompt.append("🚫 APPROVED LEAVE REQUESTS (MUST EXCLUDE DOCTOR ON THESE DAYS):\n");
            prompt.append("The following doctors have APPROVED leave requests. DO NOT create schedules for them on these days:\n");
            for (Map.Entry<Integer, List<String>> entry : doctorLeaveDays.entrySet()) {
                int docId = entry.getKey();
                String days = String.join(", ", entry.getValue());
                prompt.append(String.format("  - Doctor ID %d has APPROVED leave on: %s\n", docId, days.toUpperCase()));
            }
            prompt.append("  ⚠️ CRITICAL: DO NOT create any assignments for doctors with approved leave on those days!\n");
            prompt.append("\n");
        }

        var clinics = clinicRepo.findAll().stream()
            .filter(c -> c.getIsActive() != null && c.getIsActive())
            .toList();
        prompt.append("AVAILABLE CLINICS (you MUST use ONLY these clinic IDs):\n");
        for (var clinic : clinics) {
            prompt.append(String.format("  - Clinic ID: %d, Name: %s, Address: %s\n", 
                clinic.getId(), clinic.getClinicName(), clinic.getAddress()));
        }
        prompt.append("\n");

        var rooms = roomRepo.findByIsActiveTrueOrderByRoomNameAsc();
        prompt.append("AVAILABLE ROOMS (you MUST use ONLY these room IDs):\n");
        for (var room : rooms) {
            var clinic = room.getClinic();
            String clinicName = clinic != null ? clinic.getClinicName() : "Unknown";
            Integer clinicId = clinic != null ? clinic.getId() : 0;
            prompt.append(String.format("  - Room ID: %d, Name: %s, Clinic: %s (ID: %d), Chairs: %d\n", 
                room.getId(), room.getRoomName(), clinicName, clinicId, 
                room.getNumberOfChairs()));
        }
        prompt.append("═══════════════════════════════════════════════════════════\n\n");

        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("⚠️ CRITICAL INSTRUCTION - MUST FOLLOW ⚠️\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("You MUST return ONLY valid JSON in this EXACT format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"dailyAssignments\": {\n");
        prompt.append("    \"monday\": [\n");
        prompt.append("      // Each doctor must work 2 shifts per day at different clinics\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"13:00\", \"endTime\": \"18:00\"}\n");
        prompt.append("    ],\n");
        prompt.append("    \"tuesday\": [\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"13:00\", \"endTime\": \"18:00\"}\n");
        prompt.append("    ],\n");
        prompt.append("    \"wednesday\": [...],\n");
        prompt.append("    \"thursday\": [...],\n");
        prompt.append("    \"friday\": [...],\n");
        prompt.append("    \"saturday\": [\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"13:00\", \"endTime\": \"18:00\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("BUSINESS RULES (IN PRIORITY ORDER):\n");
        prompt.append("1. All doctors work all working days (Monday-Saturday), except holidays and their days off\n");
        prompt.append("2. Each doctor must work 2 shifts/day (morning 08:00-11:00 & afternoon 13:00-18:00)\n");
        prompt.append("3. For each day, a doctor must work at different clinics for morning and afternoon shift\n");
        prompt.append("4. Minimum 2 doctors per clinic per shift\n");
        prompt.append("5. Each specialty must be present at both clinics each day\n");
        prompt.append("6. No assignments on holiday\n");
        prompt.append("7. Doctor's days off strictly follow user request (see DOCTOR SPECIFIC DAYS OFF)\n");
        prompt.append("8. Do not create any conflicts with existing schedules\n");
        prompt.append("9. Only use IDs provided in AVAILABLE RESOURCES\n");
        prompt.append("\nJSON FORMAT:\n");
        prompt.append("• Output ONLY the JSON object, no extra text\n");
        prompt.append("• Do not wrap in markdown (no ```json)\n");
        prompt.append("• Double quotes on all keys\n");
        prompt.append("• If no valid schedule, return {\"dailyAssignments\":{}}\n");
        prompt.append("\nNow generate the schedule based on the USER REQUEST above.\nONLY output the JSON object.\n");

        return prompt.toString();
    }
    
    // Phương thức quan trọng: extract JSON thuần từ text trả về (có thể có markdown hoặc text)
    private String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        if (trimmed.contains("```json")) {
            int startIndex = trimmed.indexOf("```json") + 7;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                return trimmed.substring(startIndex, endIndex).trim();
            }
        }
        if (trimmed.contains("```")) {
            int startIndex = trimmed.indexOf("```") + 3;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                String extracted = trimmed.substring(startIndex, endIndex).trim();
                if (extracted.startsWith("json")) {
                    extracted = extracted.substring(4).trim();
                }
                return extracted;
            }
        }
        int jsonStart = trimmed.indexOf('{');
        if (jsonStart >= 0) {
            int jsonEnd = trimmed.lastIndexOf('}');
            if (jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd + 1);
            }
        }
        return null;
    }
    
    // Lấy danh sách các ngày lễ của Việt Nam trong một tuần
    private List<String> calculateHolidaysInWeek(LocalDate weekStart) {
        List<String> holidays = new ArrayList<>();
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (int i = 0; i < 6; i++) {
            LocalDate date = weekStart.plusDays(i);
            if (isVietnameseHoliday(date)) {
                holidays.add(dayNames[i]);
            }
        }
        return holidays;
    }
    
    // Kiểm tra 1 ngày có phải là ngày lễ (VN) không
    private boolean isVietnameseHoliday(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        int year = date.getYear();
        if (month == 1 && day == 1)
            return true;
        if (month == 4 && day == 30)
            return true;
        if (month == 5 && day == 1)
            return true;
        if (month == 9 && day == 2)
            return true;
        if (month == 4 && day == 10)
            return true;
        if (isTetNguyenDan(date, year))
            return true;
        return false;
    }
    
    // Kiểm tra ngày có thuộc Tết Nguyên Đán không (chỉ check cứng vài năm)
    private boolean isTetNguyenDan(LocalDate date, int year) {
        Map<Integer, LocalDate[]> tetDates = new HashMap<>();
        tetDates.put(2024, new LocalDate[]{LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 16)});
        tetDates.put(2025, new LocalDate[]{LocalDate.of(2025, 1, 29), LocalDate.of(2025, 2, 4)});
        tetDates.put(2026, new LocalDate[]{LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 23)});
        if (tetDates.containsKey(year)) {
            LocalDate[] range = tetDates.get(year);
            return !date.isBefore(range[0]) && !date.isAfter(range[1]);
        }
        return false;
    }
    
    // Lấy tên ngày lễ từ ngày
    private String getHolidayName(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        if (month == 1 && day == 1) return "New Year's Day (Tết Dương lịch)";
        if (month == 4 && day == 10) return "Hung Kings Festival (Giỗ Tổ Hùng Vương)";
        if (month == 4 && day == 30) return "Liberation Day (Ngày Giải phóng miền Nam)";
        if (month == 5 && day == 1) return "Labor Day (Ngày Quốc tế Lao động)";
        if (month == 9 && day == 2) return "National Day (Quốc khánh)";
        if (isTetNguyenDan(date, date.getYear())) return "Vietnamese New Year (Tết Nguyên Đán)";
        return "Public Holiday";
    }
    
    // Chỉ parse ngày nghỉ của bác sĩ theo mô tả user, không tự động gán ngày cụ thể
    private Map<Integer, List<String>> parseDoctorDaysOff(String userDescription, @SuppressWarnings("unused") LocalDate weekStart, @SuppressWarnings("unused") List<String> exclusionDays) {
        Map<Integer, List<String>> doctorDaysOff = new HashMap<>();
        if (userDescription == null || userDescription.trim().isEmpty()) {
            return doctorDaysOff;
        }
        String descLower = userDescription.toLowerCase();
        Map<String, String> dayMap = new HashMap<>();
        dayMap.put("thứ 2", "monday");
        dayMap.put("thứ hai", "monday");
        dayMap.put("thứ 3", "tuesday");
        dayMap.put("thứ ba", "tuesday");
        dayMap.put("thứ 4", "wednesday");
        dayMap.put("thứ tư", "wednesday");
        dayMap.put("thứ 5", "thursday");
        dayMap.put("thứ năm", "thursday");
        dayMap.put("thứ 6", "friday");
        dayMap.put("thứ sáu", "friday");
        dayMap.put("thứ 7", "saturday");
        dayMap.put("thứ bảy", "saturday");
        dayMap.put("monday", "monday");
        dayMap.put("tuesday", "tuesday");
        dayMap.put("wednesday", "wednesday");
        dayMap.put("thursday", "thursday");
        dayMap.put("friday", "friday");
        dayMap.put("saturday", "saturday");
        Pattern pattern = Pattern.compile("(?:doc|doctor)\\s*(\\d+)\\s+(?:nghỉ|off|nghỉ cả tuần|off all week)(?:\\s+(?:vào|on|thứ|ngày)?\\s*([^,\\.]+))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(descLower);
        while (matcher.find()) {
            int doctorId = Integer.parseInt(matcher.group(1));
            String daysStr = matcher.group(2);
            List<String> days = new ArrayList<>();
            if (daysStr != null && !daysStr.trim().isEmpty()) {
                String[] parts = daysStr.split("(?:và|and|,|\\s+)");
                for (String part : parts) {
                    part = part.trim();
                    for (Map.Entry<String, String> entry : dayMap.entrySet()) {
                        if (part.contains(entry.getKey())) {
                            days.add(entry.getValue());
                            break;
                        }
                    }
                }
            } else {
                days.addAll(List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday"));
            }
            if (!days.isEmpty()) {
                doctorDaysOff.put(doctorId, days);
            }
        }
        return doctorDaysOff;
    }
    
    // Trả về tên ngày trong tuần (mon-sat) theo thứ tự tuần
    private String getDayNameFromDate(LocalDate weekStart, LocalDate date) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(weekStart, date);
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        if (daysBetween >= 0 && daysBetween < 6) {
            return dayNames[(int) daysBetween];
        }
        return "unknown";
    }
}
