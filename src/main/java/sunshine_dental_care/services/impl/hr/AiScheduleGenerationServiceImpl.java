package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        
        // Phần 1: User request
        prompt.append("USER REQUEST:\n");
        prompt.append(userDescription).append("\n\n");
        
        // Phần 2: Week info
        prompt.append("WEEK START DATE: ").append(weekStart).append(" (Monday)\n\n");
        
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
        prompt.append("      // ⚠️ IMPORTANT: Doctor 6 (doc6) MUST NOT appear in Saturday schedule\n");
        prompt.append("      // Only assign other doctors on Saturday\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
        prompt.append("      {\"doctorId\": 21, \"clinicId\": 2, \"roomId\": 7, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
        prompt.append("      // ... (NO Doctor 6 entries here)\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        prompt.append("BUSINESS RULES (MUST FOLLOW):\n");
        prompt.append("1. ⚠️ CRITICAL: Each doctor MUST work BOTH morning (08:00-11:00) AND afternoon (13:00-18:00) shifts\n");
        prompt.append("   - This means 2 entries per doctor per working day (one for morning, one for afternoon)\n");
        prompt.append("   - Example: Doctor 21 on Monday should have 2 entries: 08:00-11:00 and 13:00-18:00\n");
        prompt.append("2. ⚠️ CRITICAL: Each doctor MUST work at DIFFERENT clinics for morning and afternoon shifts on the SAME day\n");
        prompt.append("   - Example: If Doctor 21 works at Clinic 1 in the morning (08:00-11:00), they MUST work at Clinic 2 in the afternoon (13:00-18:00)\n");
        prompt.append("   - Example: If Doctor 22 works at Clinic 2 in the morning, they MUST work at Clinic 1 in the afternoon\n");
        prompt.append("   - This rule applies to EVERY doctor on EVERY working day\n");
        prompt.append("3. Doctors can rotate clinics across different days of the week\n");
        prompt.append("   - Example: Doctor 21 works at Clinic 1 (morning) and Clinic 2 (afternoon) on Monday,\n");
        prompt.append("     but can work at Clinic 2 (morning) and Clinic 1 (afternoon) on Tuesday\n");
        prompt.append("4. Each clinic must have at least 2 doctors in morning shift (08:00-11:00)\n");
        prompt.append("5. Each clinic must have at least 2 doctors in afternoon shift (13:00-18:00)\n");
        prompt.append("6. ⚠️ CRITICAL: Each specialty MUST have doctors assigned to BOTH clinics on each day\n");
        prompt.append("   - Example: If you assign a 'Preventive Care' doctor to Clinic 1, you MUST also assign\n");
        prompt.append("     another 'Preventive Care' doctor to Clinic 2 on the same day\n");
        prompt.append("7. Doctors cannot work more than 8 hours per day (morning + afternoon = 8 hours total)\n");
        prompt.append("8. ⚠️ SPECIAL RULE: Doctor with ID 6 (doc6) MUST NOT be scheduled on Saturday\n");
        prompt.append("   - Doctor 6 should only appear in monday, tuesday, wednesday, thursday, friday schedules\n");
        prompt.append("   - On Saturday, assign other doctors to cover Doctor 6's shifts\n");
        prompt.append("9. ⚠️ DAYS OFF RULES: If user specifies days off for specific doctors, exclude them from those days\n");
        prompt.append("   - If user says 'doc5 nghỉ cả tuần' or 'doctor 5 off all week', DO NOT schedule Doctor 5 on ANY day\n");
        prompt.append("   - If user says 'doc3 nghỉ thứ 2 và thứ 4' or 'doctor 3 off Monday and Wednesday', exclude Doctor 3 from monday and wednesday only\n");
        prompt.append("   - If user says 'doc7 nghỉ từ thứ 3 đến thứ 5', exclude Doctor 7 from tuesday, wednesday, thursday\n");
        prompt.append("   - When a doctor is off, redistribute their shifts to other available doctors\n");
        prompt.append("   - Ensure remaining doctors still follow the 2-shifts-per-day and different-clinics rules\n\n");
        
        prompt.append("JSON FORMAT RULES:\n");
        prompt.append("1. Return ONLY the JSON object, NO explanatory text before or after\n");
        prompt.append("2. Do NOT wrap in markdown code blocks (no ```json or ```)\n");
        prompt.append("3. Use double quotes for all strings\n");
        prompt.append("4. Time format: \"HH:mm\" (e.g., \"08:00\", \"13:00\")\n");
        prompt.append("5. Days: monday, tuesday, wednesday, thursday, friday, saturday\n");
        prompt.append("6. All fields (doctorId, clinicId, roomId, startTime, endTime) are required\n");
        prompt.append("7. ⚠️ CRITICAL: Use ONLY the doctor IDs, clinic IDs, and room IDs listed in AVAILABLE RESOURCES above\n");
        prompt.append("8. ⚠️ CRITICAL: Match room IDs with their correct clinic IDs (check the room's clinic in the list)\n");
        prompt.append("9. If you cannot generate a schedule, return an empty dailyAssignments object: {\"dailyAssignments\": {}}\n");
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
}
