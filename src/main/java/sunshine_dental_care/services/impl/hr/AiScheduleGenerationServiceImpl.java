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
    private final HrService hrService;

    public AiScheduleGenerationServiceImpl(
        UserRepo userRepo,
        ClinicRepo clinicRepo,
        RoomRepo roomRepo,
        DoctorSpecialtyRepo doctorSpecialtyRepo,
        UserRoleRepo userRoleRepo,
        DoctorScheduleRepo doctorScheduleRepo,
        @Lazy HrService hrService
    ) {
        this.userRepo = userRepo;
        this.clinicRepo = clinicRepo;
        this.roomRepo = roomRepo;
        this.doctorSpecialtyRepo = doctorSpecialtyRepo;
        this.userRoleRepo = userRoleRepo;
        this.doctorScheduleRepo = doctorScheduleRepo;
        this.hrService = hrService;
    }
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    // Phương thức chính: sinh lịch làm việc từ prompt người dùng sử dụng AI Gemini
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
            // TỰ ĐỘNG thêm instruction yêu cầu JSON vào prompt của user
            String enhancedPrompt = buildEnhancedPrompt(description, weekStart);
            
            // Gửi prompt đã enhance tới Gemini API
            String aiResponse = callGeminiAPI(enhancedPrompt);

            if (aiResponse == null) {
                log.error("Gemini API unavailable. Cannot generate schedule.");
                throw new RuntimeException("Gemini API is unavailable. Please check your API key and network connection.");
            }

            // Parse kết quả trả về, sinh đối tượng lịch
            CreateWeeklyScheduleRequest request = parseGeminiResponse(aiResponse, weekStart);

            // Validate lịch
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

    // Phương thức sinh lịch từ prompt tùy chỉnh của người dùng
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
            // Gửi prompt trực tiếp từ người dùng tới Gemini API
            String aiResponse = callGeminiAPI(customPrompt);

            if (aiResponse == null) {
                log.error("Gemini API unavailable. Cannot generate schedule.");
                throw new RuntimeException("Gemini API is unavailable. Please check your API key and network connection.");
            }

            // Parse kết quả trả về, sinh đối tượng lịch
            CreateWeeklyScheduleRequest request = parseGeminiResponse(aiResponse, weekStart);

            // Validate lịch
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

    // Gửi prompt tới Gemini API
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


    // Lấy danh sách model Gemini hỗ trợ generateContent
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

    // Gửi data tới Gemini API với url cụ thể
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

    // Parse kết quả sinh từ Gemini, sinh ra đối tượng CreateWeeklyScheduleRequest
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

            // Xử lý text: tìm JSON block trong response
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
            for (String day : days) {
                if (dailyAssignmentsNode.has(day) && dailyAssignmentsNode.get(day).isArray()) {
                    List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = new ArrayList<>();
                    for (JsonNode assignmentNode : dailyAssignmentsNode.get(day)) {
                        CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment = 
                            new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                        assignment.setDoctorId(assignmentNode.get("doctorId").asInt());
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

    // Tự động thêm instruction yêu cầu JSON vào prompt của user
    private String buildEnhancedPrompt(String userDescription, LocalDate weekStart) {
        StringBuilder prompt = new StringBuilder();
        
        // Phần 1: Introduction
        prompt.append("You are an AI-powered HR Schedule Generator for Sunshine Dental Care.\n");
        prompt.append("Your sole output MUST be a perfectly formatted JSON object that adheres to ALL CRITICAL BUSINESS RULES.\n");
        prompt.append("DO NOT output any text, explanation, or markdown wrappers (no ```json or ```).\n\n");
        
        // Phần 2: User request & Week info
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("USER REQUEST & WEEK INFO\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("USER REQUEST: ").append(userDescription).append("\n");
        prompt.append("WEEK START DATE: ").append(weekStart).append(" (Monday)\n");
        prompt.append("WEEK END DATE: ").append(weekStart.plusDays(5)).append(" (Saturday)\n\n");
        
        // Phần 2.5: AUTO-CALCULATED EXCLUSIONS
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("🗓️ AUTO-CALCULATED WORK EXCLUSIONS FOR THIS WEEK\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        
        // Tính toán ngày nghỉ lễ và ngày nghỉ của bác sĩ
        LocalDate weekEnd = weekStart.plusDays(5);
        List<String> exclusionDays = calculateHolidaysInWeek(weekStart);
        Map<Integer, List<String>> doctorDaysOff = parseDoctorDaysOff(userDescription, weekStart, exclusionDays);
        
        // KHÔNG thêm rule cứng về doc6 - để AI tự quyết định dựa trên USER REQUEST
        // Nếu user yêu cầu "full tuần", doc6 cũng phải làm thứ 7
        
        // Query existing schedules để tránh conflict
        List<sunshine_dental_care.entities.DoctorSchedule> existingSchedules = 
            doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);
        
        // Hiển thị working days
        prompt.append("👉 WORKING DAYS (MUST be included in JSON keys):\n");
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (int i = 0; i < 6; i++) {
            if (!exclusionDays.contains(dayNames[i])) {
                prompt.append(String.format("  - %s (%s)\n", dayNames[i], weekStart.plusDays(i)));
            }
        }
        
        // Hiển thị excluded days
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
        
        // Hiển thị doctor days off
        if (!doctorDaysOff.isEmpty()) {
            prompt.append("\n⚠️ DOCTOR SPECIFIC DAYS OFF (MUST EXCLUDE DOCTOR):\n");
            for (Map.Entry<Integer, List<String>> entry : doctorDaysOff.entrySet()) {
                int docId = entry.getKey();
                String days = String.join(", ", entry.getValue());
                prompt.append(String.format("  - Doctor ID %d is OFF on: %s\n", docId, days.toUpperCase()));
            }
        }
        
        // Hiển thị existing schedules để AI tránh conflict
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
        
        // Phần 3: AVAILABLE RESOURCES - Query from database
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("📋 AVAILABLE RESOURCES IN SYSTEM\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        
        // Get available doctors with DOCTOR role
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
        
        // Get available clinics
        var clinics = clinicRepo.findAll().stream()
            .filter(c -> c.getIsActive() != null && c.getIsActive())
            .toList();
        prompt.append("AVAILABLE CLINICS (you MUST use ONLY these clinic IDs):\n");
        for (var clinic : clinics) {
            prompt.append(String.format("  - Clinic ID: %d, Name: %s, Address: %s\n", 
                clinic.getId(), clinic.getClinicName(), clinic.getAddress()));
        }
        prompt.append("\n");
        
        // Get available rooms
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
        
        // Phần 4: CRITICAL - Yêu cầu JSON format
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("⚠️ CRITICAL INSTRUCTION - MUST FOLLOW ⚠️\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("You MUST return ONLY valid JSON in this EXACT format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"dailyAssignments\": {\n");
        prompt.append("    \"monday\": [\n");
        prompt.append("      // ⚠️ CRITICAL RULE: Each doctor works 2 shifts per day at DIFFERENT clinics\n");
        prompt.append("      // Doctor 21: Morning at Clinic 1, Afternoon at Clinic 2 (DIFFERENT clinics!)\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // Doctor 22: Morning at Clinic 2, Afternoon at Clinic 1 (DIFFERENT clinics!)\n");
        prompt.append("      {\"doctorId\": 22, \"clinicId\": 2, \"roomId\": 8, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 22, \"clinicId\": 1, \"roomId\": 2, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // Doctor 23: Morning at Clinic 1, Afternoon at Clinic 2\n");
        prompt.append("      {\"doctorId\": 23, \"clinicId\": 1, \"roomId\": 3, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 23, \"clinicId\": 2, \"roomId\": 9, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // ... continue for all doctors (each doctor = 2 entries with DIFFERENT clinics)\n");
        prompt.append("    ],\n");
        prompt.append("    \"tuesday\": [\n");
        prompt.append("      // You can rotate: If Monday morning was Clinic 1, Tuesday morning can be Clinic 2\n");
        prompt.append("      // But SAME DAY must have DIFFERENT clinics for morning and afternoon\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // ...\n");
        prompt.append("    ],\n");
        prompt.append("    \"wednesday\": [...],\n");
        prompt.append("    \"thursday\": [...],\n");
        prompt.append("    \"friday\": [...],\n");
        prompt.append("    \"saturday\": [\n");
        prompt.append("      // ⚠️ IMPORTANT: Saturday is a WORKING DAY - include ALL doctors (unless they have days off)\n");
        prompt.append("      // If user requests 'full tuần', ALL doctors including Doctor 6 must work on Saturday\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // ... include ALL doctors (each doctor = 2 entries with DIFFERENT clinics)\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("BUSINESS RULES (MUST FOLLOW - IN ORDER OF PRIORITY):\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("🎯 CORE REQUIREMENTS (HIGHEST PRIORITY):\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("1. ⚠️ FULL WEEK COVERAGE: ALL doctors MUST work on ALL working days (Monday through Saturday, excluding holidays and their specific days off)\n");
        prompt.append("   - FULL WEEK means Monday, Tuesday, Wednesday, Thursday, Friday, AND Saturday (6 days total)\n");
        prompt.append("   - ⚠️ CRITICAL: If user requests 'full tuần' or 'full week', this means ALL 6 days for ALL doctors\n");
        prompt.append("   - If a day is NOT a holiday and NOT in a doctor's days off list, that doctor MUST be scheduled\n");
        prompt.append("   - Example: If Monday is a working day and Doctor 21 is not off, Doctor 21 MUST appear in Monday's schedule\n");
        prompt.append("   - Example: If Saturday is NOT a holiday and Doctor 21 is not off, Doctor 21 MUST appear in Saturday's schedule\n");
        prompt.append("   - ⚠️ CRITICAL: Saturday is a WORKING DAY unless it's a holiday. Include ALL doctors on Saturday (except those with days off)\n");
        prompt.append("   - ⚠️ CRITICAL: If user says 'full tuần', Doctor 6 MUST also work on Saturday (unless Saturday is a holiday or Doctor 6 has Saturday in days off)\n");
        prompt.append("   - This means: Count working days (excluding holidays). Each doctor must work on ALL of those days (except their days off)\n\n");
        prompt.append("2. ⚠️ DUAL SHIFT REQUIREMENT: Each doctor MUST work BOTH morning (08:00-11:00) AND afternoon (13:00-18:00) shifts on EVERY working day\n");
        prompt.append("   - This means 2 entries per doctor per working day (one for morning, one for afternoon)\n");
        prompt.append("   - Example: Doctor 21 on Monday should have EXACTLY 2 entries: 08:00-11:00 and 13:00-18:00\n");
        prompt.append("   - NO EXCEPTIONS: Every doctor working on a day MUST have both shifts\n\n");
        prompt.append("3. ⚠️ CLINIC ROTATION: Each doctor MUST work at DIFFERENT clinics for morning and afternoon shifts on the SAME day\n");
        prompt.append("   - Example: If Doctor 21 works at Clinic 1 in the morning (08:00-11:00), they MUST work at Clinic 2 in the afternoon (13:00-18:00)\n");
        prompt.append("   - Example: If Doctor 22 works at Clinic 2 in the morning, they MUST work at Clinic 1 in the afternoon\n");
        prompt.append("   - This rule applies to EVERY doctor on EVERY working day\n");
        prompt.append("   - ⚠️ WEEKLY ROTATION: Rotate clinics across different days to ensure fair distribution\n");
        prompt.append("     * Example: Doctor 21: Mon (C1 AM, C2 PM), Tue (C2 AM, C1 PM), Wed (C1 AM, C2 PM), etc.\n");
        prompt.append("     * This creates variety and ensures doctors work at both clinics throughout the week\n\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("📋 STAFFING REQUIREMENTS:\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("4. Each clinic must have at least 2 doctors in morning shift (08:00-11:00) on EVERY working day\n");
        prompt.append("5. Each clinic must have at least 2 doctors in afternoon shift (13:00-18:00) on EVERY working day\n");
        prompt.append("6. ⚠️ SPECIALTY BALANCE: Each specialty MUST have doctors assigned to BOTH clinics on each working day\n");
        prompt.append("   - Example: If you assign a 'Preventive Care' doctor to Clinic 1, you MUST also assign\n");
        prompt.append("     another 'Preventive Care' doctor to Clinic 2 on the same day\n");
        prompt.append("   - Exception: If only ONE doctor of a specialty is available (due to days off), assign them to both clinics (morning at one, afternoon at the other)\n\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("🚫 EXCLUSION RULES:\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("7. ⚠️ PUBLIC HOLIDAYS: DO NOT schedule ANY doctors on public holidays\n");
        prompt.append("   - If a date is listed in EXCLUDED DAYS section above, that date MUST be EXCLUDED from dailyAssignments\n");
        prompt.append("   - Example: If Thursday (01/01/2026) is New Year's Day, do NOT include \"thursday\" in dailyAssignments\n");
        prompt.append("   - Only schedule on working days (non-holiday dates)\n\n");
        prompt.append("8. ⚠️ DOCTOR DAYS OFF: Follow the USER REQUEST exactly for doctor days off (see DOCTOR SPECIFIC DAYS OFF section above)\n");
        prompt.append("   - Read the USER REQUEST carefully. If user says 'doc4 và doc5 nghỉ hai ngày không tính ngày lễ', you MUST:\n");
        prompt.append("     * Count working days (excluding holidays listed in EXCLUDED DAYS section)\n");
        prompt.append("     * Assign Doctor 4 to be off on exactly 2 working days (you choose which 2 days, but they must be different)\n");
        prompt.append("     * Assign Doctor 5 to be off on exactly 2 DIFFERENT working days (you choose which 2 days, different from Doctor 4's days off)\n");
        prompt.append("     * Ensure the 2 days off for each doctor are different from each other\n");
        prompt.append("   - When a doctor is off, redistribute their shifts to other available doctors\n");
        prompt.append("   - Ensure remaining doctors still follow the 2-shifts-per-day and different-clinics rules\n");
        prompt.append("   - ⚠️ CRITICAL: Days off are IN ADDITION to holidays. If user says '2 days off excluding holidays', count 2 working days (not including holidays)\n");
        prompt.append("   - ⚠️ CRITICAL: If user specifies specific days off, follow exactly. If user says 'nghỉ hai ngày không tính ngày lễ', you choose which 2 working days\n\n");
        prompt.append("9. ⚠️ FOLLOW USER REQUEST: If user requests 'full tuần' or 'full week', ALL doctors (including Doctor 6) MUST work on Saturday (unless it's a holiday or their specific days off)\n");
        prompt.append("   - FULL WEEK means ALL 6 days (Monday-Saturday) for ALL doctors\n");
        prompt.append("   - Only exclude doctors from Saturday if:\n");
        prompt.append("     * Saturday is a public holiday, OR\n");
        prompt.append("     * The doctor has Saturday in their specific days off list (see DOCTOR SPECIFIC DAYS OFF section)\n");
        prompt.append("   - If user does NOT specify that Doctor 6 should be off on Saturday, Doctor 6 MUST be scheduled on Saturday\n\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("✅ VALIDATION CHECKLIST:\n");
        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("Before returning the JSON, verify:\n");
        prompt.append("- [ ] Every working day (Monday-Saturday, excluding holidays) has ALL available doctors scheduled\n");
        prompt.append("- [ ] Saturday is included as a working day (unless it's a holiday)\n");
        prompt.append("- [ ] Every doctor on every working day has EXACTLY 2 entries (morning + afternoon)\n");
        prompt.append("- [ ] Every doctor's morning and afternoon shifts are at DIFFERENT clinics\n");
        prompt.append("- [ ] Doctors rotate clinics across different days of the week\n");
        prompt.append("- [ ] Each clinic has at least 2 doctors in both morning and afternoon shifts\n");
        prompt.append("- [ ] Follow USER REQUEST exactly for Doctor 4 and Doctor 5 days off (if specified)\n");
        prompt.append("- [ ] If user requests 'full tuần', Doctor 6 MUST be scheduled on Saturday (unless Saturday is a holiday)\n");
        prompt.append("- [ ] No assignments on public holidays\n");
        prompt.append("- [ ] All 6 days (Monday-Saturday) are covered, except holidays\n\n");
        
        prompt.append("JSON FORMAT RULES:\n");
        prompt.append("1. Return ONLY the JSON object, NO explanatory text before or after\n");
        prompt.append("2. Do NOT wrap in markdown code blocks (no ```json or ```)\n");
        prompt.append("3. Use double quotes for all strings\n");
        prompt.append("4. Time format: \"HH:mm\" (e.g., \"08:00\", \"13:00\")\n");
        prompt.append("5. Days: monday, tuesday, wednesday, thursday, friday, saturday\n");
        prompt.append("6. All fields (doctorId, clinicId, roomId, startTime, endTime) are required\n");
        prompt.append("7. ⚠️ CRITICAL: Use ONLY the doctor IDs, clinic IDs, and room IDs listed in AVAILABLE RESOURCES above\n");
        prompt.append("8. ⚠️ CRITICAL: Match room IDs with their correct clinic IDs (check the room's clinic in the list)\n");
        prompt.append("9. ⚠️ CRITICAL: EXCLUDE any day that is a Vietnamese public holiday (see VIETNAM PUBLIC HOLIDAYS section above)\n");
        prompt.append("   - If a day is a holiday, do NOT include that day key in dailyAssignments\n");
        prompt.append("   - Only include working days (non-holiday dates) in the schedule\n");
        prompt.append("10. If you cannot generate a schedule, return an empty dailyAssignments object: {\"dailyAssignments\": {}}\n");
        prompt.append("═══════════════════════════════════════════════════════════\n\n");
        prompt.append("Now generate the schedule based on the USER REQUEST above.\n");
        prompt.append("Remember: ONLY return the JSON object, nothing else.\n");
        
        return prompt.toString();
    }
    
    // Trích xuất JSON từ text response (xử lý trường hợp Gemini trả về text + JSON)
    private String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String trimmed = text.trim();

        // Trường hợp 1: Text bắt đầu bằng { hoặc [ (JSON thuần)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        // Trường hợp 2: Text có markdown code block ```json ... ```
        if (trimmed.contains("```json")) {
            int startIndex = trimmed.indexOf("```json") + 7;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                return trimmed.substring(startIndex, endIndex).trim();
            }
        }

        // Trường hợp 3: Text có markdown code block ``` ... ```
        if (trimmed.contains("```")) {
            int startIndex = trimmed.indexOf("```") + 3;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                String extracted = trimmed.substring(startIndex, endIndex).trim();
                // Bỏ "json" nếu có ở đầu
                if (extracted.startsWith("json")) {
                    extracted = extracted.substring(4).trim();
                }
                return extracted;
            }
        }

        // Trường hợp 4: Tìm JSON object trong text (bắt đầu bằng { và kết thúc bằng })
        int jsonStart = trimmed.indexOf('{');
        if (jsonStart >= 0) {
            int jsonEnd = trimmed.lastIndexOf('}');
            if (jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd + 1);
            }
        }

        // Không tìm thấy JSON
        return null;
    }
    
    // Tính toán ngày nghỉ lễ Việt Nam trong tuần
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
    
    // Kiểm tra xem một ngày có phải là ngày nghỉ lễ Việt Nam không
    private boolean isVietnameseHoliday(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        int year = date.getYear();
        
        // 1. Tết Dương lịch (1/1)
        if (month == 1 && day == 1) {
            return true;
        }
        
        // 2. Ngày Giải phóng miền Nam (30/4)
        if (month == 4 && day == 30) {
            return true;
        }
        
        // 3. Ngày Quốc tế Lao động (1/5)
        if (month == 5 && day == 1) {
            return true;
        }
        
        // 4. Quốc khánh (2/9)
        if (month == 9 && day == 2) {
            return true;
        }
        
        // 5. Giỗ Tổ Hùng Vương (10/3 âm lịch) - xấp xỉ 10/4 dương lịch
        // Note: Cần tính chính xác theo âm lịch, nhưng tạm thời dùng 10/4
        if (month == 4 && day == 10) {
            return true;
        }
        
        // 6. Tết Nguyên Đán - cần tính theo âm lịch
        // Tạm thời hardcode một số năm gần đây (có thể cải thiện sau)
        if (isTetNguyenDan(date, year)) {
            return true;
        }
        
        return false;
    }
    
    // Kiểm tra Tết Nguyên Đán (tạm thời hardcode, nên cải thiện bằng thư viện âm lịch)
    private boolean isTetNguyenDan(LocalDate date, int year) {
        // Tết Nguyên Đán thường rơi vào cuối tháng 1 hoặc đầu tháng 2
        // Hardcode một số năm (có thể cải thiện bằng thư viện âm lịch)
        Map<Integer, LocalDate[]> tetDates = new HashMap<>();
        tetDates.put(2024, new LocalDate[]{LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 16)}); // 10-16/2/2024
        tetDates.put(2025, new LocalDate[]{LocalDate.of(2025, 1, 29), LocalDate.of(2025, 2, 4)}); // 29/1-4/2/2025
        tetDates.put(2026, new LocalDate[]{LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 23)}); // 17-23/2/2026
        
        if (tetDates.containsKey(year)) {
            LocalDate[] range = tetDates.get(year);
            return !date.isBefore(range[0]) && !date.isAfter(range[1]);
        }
        
        return false;
    }
    
    // Lấy tên ngày nghỉ lễ
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
    
    // CHỈ PARSE - KHÔNG TỰ ĐỘNG XỬ LÝ - ĐỂ AI TỰ QUYẾT ĐỊNH DỰA TRÊN PROMPT
    private Map<Integer, List<String>> parseDoctorDaysOff(String userDescription, @SuppressWarnings("unused") LocalDate weekStart, @SuppressWarnings("unused") List<String> exclusionDays) {
        Map<Integer, List<String>> doctorDaysOff = new HashMap<>();
        if (userDescription == null || userDescription.trim().isEmpty()) {
            return doctorDaysOff;
        }
        
        String descLower = userDescription.toLowerCase();
        
        // Map tiếng Việt và tiếng Anh
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
        
        // Pattern: doc<id> nghỉ <days> hoặc doctor <id> off <days>
        Pattern pattern = Pattern.compile("(?:doc|doctor)\\s*(\\d+)\\s+(?:nghỉ|off|nghỉ cả tuần|off all week)(?:\\s+(?:vào|on|thứ|ngày)?\\s*([^,\\.]+))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(descLower);
        
        while (matcher.find()) {
            int doctorId = Integer.parseInt(matcher.group(1));
            String daysStr = matcher.group(2);
            
            List<String> days = new ArrayList<>();
            
            if (daysStr != null && !daysStr.trim().isEmpty()) {
                // Parse các ngày
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
                // "nghỉ cả tuần" hoặc "off all week"
                days.addAll(List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday"));
            }
            
            if (!days.isEmpty()) {
                doctorDaysOff.put(doctorId, days);
            }
        }
        
        // KHÔNG tự động xử lý - chỉ parse thông tin từ user input
        // AI sẽ tự quyết định dựa trên yêu cầu trong promp
        
        return doctorDaysOff;
    }
    
    // Lấy tên ngày từ date (monday, tuesday, etc.)
    private String getDayNameFromDate(LocalDate weekStart, LocalDate date) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(weekStart, date);
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        if (daysBetween >= 0 && daysBetween < 6) {
            return dayNames[(int) daysBetween];
        }
        return "unknown";
    }
}
