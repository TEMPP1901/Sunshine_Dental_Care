package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;

@Component
@RequiredArgsConstructor
public class PromptBuilderService {

    private final UserRoleRepo userRoleRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final HolidayService holidayService;

    // Tạo prompt chính cho AI dựa trên mô tả và ngày bắt đầu tuần
    public String buildEnhancedPrompt(String userDescription, LocalDate weekStart) {
        StringBuilder prompt = new StringBuilder();
        appendHeader(prompt);
        appendUserRequest(prompt, userDescription, weekStart);
        List<String> workingDays = appendDataResources(prompt, weekStart);
        appendHardConstraints(prompt);
        appendSoftConstraints(prompt);
        appendForbiddenMistakes(prompt);
        appendOutputFormat(prompt, workingDays);
        appendThoughtProcess(prompt, workingDays);
        appendFinalInstructions(prompt, workingDays);
        return prompt.toString();
    }

    private void appendHeader(StringBuilder prompt) {
        prompt.append("You are an AI-powered HR Schedule Generator for Sunshine Dental Care.\n");
        prompt.append("YOUR OUTPUT MUST BE 100% VALID JSON. NOTHING ELSE.\n");
        prompt.append(
                "STRICTLY: Your ONLY output MUST be a perfectly formatted JSON object starting with { and ending with }.\n");
        prompt.append("STRICTLY: DO NOT output any text, explanation, or markdown wrappers.\n\n");
    }

    private void appendUserRequest(StringBuilder prompt, String userDescription, LocalDate weekStart) {
        prompt.append("=== USER REQUEST (HIGHEST PRIORITY) ===\n");
        prompt.append("USER REQUEST: \"").append(userDescription).append("\"\n");
        prompt.append("WEEK START DATE: ").append(weekStart).append(" (Monday)\n");
        prompt.append("\n");
        prompt.append("CRITICAL: USER REQUEST has HIGHEST PRIORITY. Follow it EXACTLY - DO NOT expand or modify:\n");
        prompt.append("  - If user asks a doctor to take ONE day off → that doctor should NOT work ONLY that ONE day.\n");
        prompt.append("    * DO NOT extend to multiple days. If user says 'off Monday', doctor works Tuesday-Saturday.\n");
        prompt.append("  - If user asks a doctor to work only morning OR only afternoon → follow that request EXACTLY.\n");
        prompt.append("    * DO NOT change it to both shifts. If user says 'only morning', assign ONLY morning.\n");
        prompt.append("  - If user asks for specific assignments → prioritize those assignments EXACTLY as requested.\n");
        prompt.append("  - DO NOT interpret or expand user requests. Follow them literally and precisely.\n");
        prompt.append("  - Only apply default rules (e.g., both shifts, all days) when user request doesn't specify otherwise.\n");
        prompt.append("\n");
    }

    // BLOCK 0: DATA RESOURCES
    private List<String> appendDataResources(StringBuilder prompt, LocalDate weekStart) {
        prompt.append("=== BLOCK 0: DATA RESOURCES ===\n");

        List<User> doctors = getActiveDoctors();
        prompt.append("AVAILABLE DOCTORS (IDs and Names):\n");
        Map<Integer, List<String>> doctorSpecialtiesMap = getDoctorSpecialtiesMap(doctors);
        for (User doctor : doctors) {
            String specialties = String.join(", ",
                    doctorSpecialtiesMap.getOrDefault(doctor.getId(), List.of("General")));
            prompt.append(String.format("- ID: %d, Name: %s, Specialty: %s\n", doctor.getId(), doctor.getFullName(),
                    specialties));
        }
        prompt.append("VALID DOCTOR IDs: ");
        List<Integer> doctorIds = doctors.stream().map(User::getId).sorted().collect(Collectors.toList());
        prompt.append(doctorIds.toString()).append("\n\n");

        List<sunshine_dental_care.entities.Clinic> clinics = getActiveClinics();
        List<sunshine_dental_care.entities.Room> rooms = getActiveRooms();
        Map<Integer, List<sunshine_dental_care.entities.Room>> roomsByClinic = rooms.stream()
                .filter(r -> r.getClinic() != null)
                .collect(Collectors.groupingBy(r -> r.getClinic().getId()));

        prompt.append("AVAILABLE CLINICS & ROOMS:\n");
        for (var clinic : clinics) {
            prompt.append(String.format("- Clinic ID: %d, Name: %s\n", clinic.getId(), clinic.getClinicName()));
            List<sunshine_dental_care.entities.Room> clinicRooms = roomsByClinic.get(clinic.getId());
            if (clinicRooms != null) {
                for (var room : clinicRooms) {
                    prompt.append(String.format("  - Room ID: %d, Name: %s\n", room.getId(), room.getRoomName()));
                }
            }
        }
        prompt.append("\n");

        LocalDate weekEnd = weekStart.plusDays(5);
        String[] dayNames = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        List<String> exclusionDays = holidayService.calculateHolidaysInWeek(weekStart);

        if (!exclusionDays.isEmpty()) {
            prompt.append("HOLIDAYS (No work - DO NOT schedule these days):\n");
            int consecutiveCount = 0;
            for (int i = 0; i < dayNames.length; i++) {
                if (exclusionDays.contains(dayNames[i])) {
                    LocalDate date = weekStart.plusDays(i);
                    String holidayName = holidayService.getHolidayName(date);
                    prompt.append(String.format("  - %s (%s): %s\n", dayNames[i].toUpperCase(), date,
                            holidayName != null ? holidayName : "Holiday"));
                    consecutiveCount++;
                } else if (consecutiveCount > 0) {
                    // Break in consecutive holidays
                    consecutiveCount = 0;
                }
            }
            if (consecutiveCount > 1) {
                prompt.append(String.format("NOTE: There are %d consecutive holiday days. You MUST still schedule ALL working days AFTER these holidays.\n", consecutiveCount));
            }
            prompt.append("\n");
            prompt.append("CRITICAL: The days listed above are holidays. DO NOT include them in your JSON.\n");
            prompt.append("CRITICAL: All OTHER days (NOT listed above) are WORKING DAYS and MUST be scheduled.\n");
            prompt.append("CRITICAL: Holidays can be SINGLE days OR MULTIPLE consecutive days. You MUST continue scheduling AFTER ALL holidays.\n");
            prompt.append("EXAMPLE: If Thursday is a holiday, you MUST still schedule Friday and Saturday.\n");
            prompt.append("EXAMPLE: If Wednesday, Thursday, Friday are holidays (3 consecutive days), you MUST still schedule Saturday.\n");
            prompt.append("\n");
        }

        List<String> workingDays = new ArrayList<>();
        prompt.append("WORKING DAYS (MUST SCHEDULE - These are NOT holidays):\n");
        boolean hasHoliday = !exclusionDays.isEmpty();
        for (int i = 0; i < dayNames.length; i++) {
            if (!exclusionDays.contains(dayNames[i])) {
                workingDays.add(dayNames[i]);
                LocalDate date = weekStart.plusDays(i);
                String dayName = dayNames[i].toUpperCase();
                String emphasis = "";
                // Special emphasis for days after holiday
                if (hasHoliday && i > 0) {
                    // Check if previous day was a holiday
                    if (exclusionDays.contains(dayNames[i-1])) {
                        emphasis = " - WORKING DAY (day after holiday - MUST schedule)";
                    }
                }
                // Special emphasis for Friday/Saturday
                if (dayName.equals("FRIDAY") || dayName.equals("SATURDAY")) {
                    emphasis += " - WORKING DAY (NOT a holiday - MUST schedule)";
                }
                prompt.append(String.format("  - %s (%s)%s\n", dayName, date, emphasis));
            }
        }
        prompt.append("\n");
        if (hasHoliday && !workingDays.isEmpty()) {
            prompt.append("REMINDER: You have ").append(workingDays.size()).append(" working days to schedule. ");
            prompt.append("Even though there is a holiday, you MUST schedule ALL ").append(workingDays.size()).append(" working days listed above.\n");
            prompt.append("DO NOT stop scheduling after the holiday. Continue until you have scheduled ALL working days.\n");
            prompt.append("\n");
        }

        Map<Integer, Map<String, String>> doctorLeaveDaysWithShift = getDoctorLeaveDaysWithShiftMap(doctors, weekStart, weekEnd, dayNames);
        if (!doctorLeaveDaysWithShift.isEmpty()) {
            prompt.append("APPROVED LEAVE (Doctors OFF - DO NOT assign these doctors for these shifts):\n");
            doctorLeaveDaysWithShift.forEach((docId, dayShiftMap) -> {
                List<String> leaveInfo = new ArrayList<>();
                dayShiftMap.forEach((day, shiftType) -> {
                    if ("FULL_DAY".equals(shiftType) || shiftType == null || shiftType.isEmpty()) {
                        leaveInfo.add(day.toUpperCase() + " (ALL SHIFTS)");
                    } else {
                        leaveInfo.add(day.toUpperCase() + " (" + shiftType + " shift only)");
                    }
                });
                prompt.append(String.format("  - Doctor ID %d: %s\n", docId, String.join(", ", leaveInfo)));
            });
            prompt.append("\n");
            prompt.append("CRITICAL: If a doctor has leave for MORNING shift on a day, you CAN assign them for AFTERNOON shift on that same day.\n");
            prompt.append("CRITICAL: If a doctor has leave for AFTERNOON shift on a day, you CAN assign them for MORNING shift on that same day.\n");
            prompt.append("CRITICAL: If a doctor has leave for FULL_DAY or no shift specified, you CANNOT assign them for ANY shift on that day.\n");
            prompt.append("\n");
        }

        List<DoctorSchedule> existingSchedules = doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);
        if (!existingSchedules.isEmpty()) {
            prompt.append("EXISTING SCHEDULES (Do not overlap):\n");
            existingSchedules.forEach(s -> prompt.append(String.format("  - Doctor ID %d on %s (%s-%s)\n",
                    s.getDoctor().getId(), s.getWorkDate(), s.getStartTime(), s.getEndTime())));
            prompt.append("\n");
        }

        return workingDays;
    }

    // BLOCK 1: HARD CONSTRAINTS
    private void appendHardConstraints(StringBuilder prompt) {
        prompt.append("=== BLOCK 1: HARD CONSTRAINTS (MUST FOLLOW) ===\n");
        prompt.append("Violating these = REJECTION\n\n");
        prompt.append("1. CLINIC: Exactly 2 clinics active every working day.\n");
        prompt.append("2. STAFFING: Morning (08:00-11:00) & Afternoon (13:00-18:00): Min 2 doctors per clinic per shift.\n");
        prompt.append("3. SPECIALTY: Every specialty must be present in BOTH clinics daily.\n");
        prompt.append("4. NO OVERLAP: Same doctor cannot have overlapping shifts.\n");
        prompt.append("5. LEAVE/HOLIDAYS: No assignments on approved leave days or holidays.\n");
        prompt.append("   - If a doctor has leave for MORNING shift → DO NOT assign them for MORNING, but CAN assign for AFTERNOON.\n");
        prompt.append("   - If a doctor has leave for AFTERNOON shift → DO NOT assign them for AFTERNOON, but CAN assign for MORNING.\n");
        prompt.append("   - If a doctor has leave for FULL_DAY → DO NOT assign them for ANY shift on that day.\n");
        prompt.append("6. VALID IDs: Use ONLY Doctor IDs from 'AVAILABLE DOCTORS' list.\n");
        prompt.append("7. COVERAGE: Schedule EVERY date in 'WORKING DAYS' (including Friday/Saturday if listed).\n");
        prompt.append("8. CONTINUE AFTER HOLIDAY (CRITICAL):\n");
        prompt.append("   - Holidays can be SINGLE days OR MULTIPLE consecutive days (e.g., Lunar New Year can span 3-5 days).\n");
        prompt.append("   - Holidays do NOT end the work week. You MUST continue scheduling AFTER all holidays.\n");
        prompt.append("   - Example: If Thursday is a holiday, you MUST still schedule Friday and Saturday.\n");
        prompt.append("   - Example: If Wednesday, Thursday, Friday are holidays (3 consecutive days), you MUST still schedule Saturday.\n");
        prompt.append("   - Example: If Monday, Tuesday, Wednesday are holidays (3 consecutive days), you MUST still schedule Thursday, Friday, Saturday.\n");
        prompt.append("   - DO NOT stop scheduling after holidays. Continue until ALL working days are scheduled.\n\n");
    }

    // BLOCK 2: SOFT CONSTRAINTS
    private void appendSoftConstraints(StringBuilder prompt) {
        prompt.append("=== BLOCK 2: SOFT CONSTRAINTS (PREFERRED) ===\n");
        prompt.append("These improve quality but won't cause rejection if violated.\n");
        prompt.append("NOTE: USER REQUEST overrides these preferences.\n\n");
        prompt.append("1. FAIRNESS: Assign ALL doctors to ALL working days (no one idle).\n");
        prompt.append("   - Exception: If USER REQUEST asks a doctor to take time off, follow that request.\n");
        prompt.append("2. FULL DAY (DEFAULT): By default, assign each doctor to BOTH Morning AND Afternoon shifts.\n");
        prompt.append("   - Exception: If USER REQUEST asks for only morning OR only afternoon, follow that request.\n");
        prompt.append("   - Exception: If USER REQUEST asks a doctor to take a day off, that doctor should NOT work that day.\n");
        prompt.append("3. ROTATION: If working both shifts, switch clinics (Morning Clinic 1 -> Afternoon Clinic 2).\n");
        prompt.append("4. BALANCE: Distribute work evenly - avoid some doctors working all shifts while others work few.\n");
        prompt.append("   - Exception: If USER REQUEST specifies different distribution, follow that request.\n\n");
        }

    // BLOCK 3: FORBIDDEN MISTAKES
    private void appendForbiddenMistakes(StringBuilder prompt) {
        prompt.append("=== BLOCK 3: FORBIDDEN MISTAKES ===\n");
        prompt.append("Common errors that cause rejection:\n\n");
        prompt.append("1. Invalid Doctor IDs (using IDs not in 'AVAILABLE DOCTORS' list).\n");
        prompt.append("2. Skipping Friday/Saturday if they are in 'WORKING DAYS'.\n");
        prompt.append("3. Same clinic for both shifts of same doctor (must rotate).\n");
        prompt.append("4. Markdown/text outside JSON (only return pure JSON).\n");
        prompt.append("5. Missing days from 'WORKING DAYS' list.\n");
        prompt.append("6. Less than 2 doctors per clinic per shift.\n\n");
    }

    // BLOCK 4: OUTPUT FORMAT
    private void appendOutputFormat(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== BLOCK 4: OUTPUT FORMAT ===\n");
        prompt.append("Return valid JSON only.\n\n");

        prompt.append("FORMAT A: SUCCESS (Complete schedule for ALL working days)\n");
        prompt.append("IMPORTANT: You MUST include ALL ").append(workingDays != null ? workingDays.size() : "X").append(" working days listed above.\n");
        prompt.append("If there is a holiday, you MUST still schedule ALL working days AFTER the holiday.\n");
        prompt.append("{\n");
        prompt.append("  \"dailyAssignments\": {\n");

        if (workingDays != null && !workingDays.isEmpty()) {
            // Show complete example for ALL working days to make pattern clear
            // Especially important for Friday/Saturday after holiday
            boolean hasFriday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("friday"));
            boolean hasSaturday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("saturday"));
            
            for (int i = 0; i < workingDays.size(); i++) {
                String day = workingDays.get(i);
                boolean isFridayOrSaturday = day.equalsIgnoreCase("friday") || day.equalsIgnoreCase("saturday");
                
                prompt.append("    \"").append(day).append("\": [\n");
                
                // Show full example for first day, and also for Friday/Saturday if they exist
                // This ensures AI sees the pattern for ALL days, especially after holiday
                if (i == 0 || (isFridayOrSaturday && (hasFriday || hasSaturday))) {
                    if (isFridayOrSaturday) {
                        prompt.append("      // ").append(day.toUpperCase()).append(" IS A WORKING DAY - MUST INCLUDE\n");
                    }
                    // Morning shift - Clinic 1 (2 doctors minimum)
                    prompt.append(
                            "      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 22, \"clinicId\": 1, \"roomId\": 2, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    // Morning shift - Clinic 2 (2 doctors minimum)
                    prompt.append(
                            "      {\"doctorId\": 23, \"clinicId\": 2, \"roomId\": 5, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 24, \"clinicId\": 2, \"roomId\": 6, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    // Afternoon shift - Clinic 1 (2 doctors minimum)
                    prompt.append(
                            "      {\"doctorId\": 25, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 26, \"clinicId\": 1, \"roomId\": 2, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
                    // Afternoon shift - Clinic 2 (2 doctors minimum)
                    prompt.append(
                            "      {\"doctorId\": 27, \"clinicId\": 2, \"roomId\": 5, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 28, \"clinicId\": 2, \"roomId\": 6, \"startTime\": \"13:00\", \"endTime\": \"18:00\"}\n");
                } else {
                    prompt.append("      // ... assignments for ").append(day).append(" (same pattern as above) ...\n");
                }
                prompt.append("    ]");
                if (i < workingDays.size() - 1) {
                    prompt.append(",");
                }
                prompt.append("\n");
            }
        }
        prompt.append("  }\n");
        prompt.append("}\n\n");

        prompt.append("SCENARIO B: FAILURE\n");
        prompt.append("{\n");
        prompt.append("  \"status\": \"fail\",\n");
        prompt.append("  \"reason\": \"...\"\n");
        prompt.append("}\n\n");
    }

    private void appendThoughtProcess(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== BLOCK 5: LOGIC STEPS ===\n");
        prompt.append("1. Read USER REQUEST first - understand what the user wants EXACTLY:\n");
        prompt.append("   - Does user ask any doctor to take time off? → Count HOW MANY days user mentioned.\n");
        prompt.append("     * If user says 'doctor X off Monday' → doctor X works Tuesday-Saturday (ONLY Monday off).\n");
        prompt.append("     * If user says 'doctor X off Monday and Tuesday' → doctor X works Wednesday-Saturday (ONLY those 2 days off).\n");
        prompt.append("     * DO NOT extend beyond what user specified. If user says 1 day, it's ONLY 1 day.\n");
        prompt.append("   - Does user ask any doctor to work only morning OR only afternoon? → Follow that request EXACTLY.\n");
        prompt.append("     * DO NOT change it to both shifts. Follow the request precisely.\n");
        prompt.append("   - Does user specify any special assignments? → Prioritize those EXACTLY as requested.\n");
        prompt.append("2. Read 'WORKING DAYS' list - count how many days you need to schedule.\n");
        prompt.append("3. Check 'APPROVED LEAVE' section - understand which doctors are off and for which shifts:\n");
        prompt.append("   - If a doctor has leave for MORNING shift on a day → They CAN work AFTERNOON shift on that day.\n");
        prompt.append("   - If a doctor has leave for AFTERNOON shift on a day → They CAN work MORNING shift on that day.\n");
        prompt.append("   - If a doctor has leave for FULL_DAY on a day → They CANNOT work ANY shift on that day.\n");
        prompt.append("4. Check if schedule is POSSIBLE:\n");
        prompt.append("   - Do you have at least 4 doctors available? (Need 2 per clinic × 2 clinics)\n");
        prompt.append("   - Are enough doctors available after accounting for leave/holidays and USER REQUEST?\n");
        prompt.append("   - Remember: Doctors with MORNING leave can still work AFTERNOON, and vice versa.\n");
        prompt.append("   - If NO → Return FORMAT B (status: fail) with reason.\n");
        prompt.append("   - If YES → Continue to step 5.\n");
        prompt.append("5. Plan assignments for ALL working days:\n");
        prompt.append("   - Count the working days: ").append(workingDays != null ? workingDays.size() : "X").append(" days\n");
        prompt.append("   - Apply USER REQUEST first - but ONLY what user specified:\n");
        prompt.append("     * If user asks doctor to take 1 day off → that doctor works on ALL OTHER days.\n");
        prompt.append("     * If user asks doctor to take 2 days off → that doctor works on ALL OTHER days.\n");
        prompt.append("     * DO NOT extend user's request. If user says 1 day, it's ONLY 1 day.\n");
        prompt.append("   - For doctors not mentioned in USER REQUEST, apply default rules (both shifts, all days).\n");
        prompt.append("   - Then check 'APPROVED LEAVE' - respect leave requests by shift:\n");
        prompt.append("     * If doctor has MORNING leave on a day → DO NOT assign them for MORNING, but CAN assign for AFTERNOON.\n");
        prompt.append("     * If doctor has AFTERNOON leave on a day → DO NOT assign them for AFTERNOON, but CAN assign for MORNING.\n");
        prompt.append("     * If doctor has FULL_DAY leave on a day → DO NOT assign them for ANY shift on that day.\n");
        prompt.append("   - If there are holidays in the week, remember: holidays can be SINGLE days OR MULTIPLE consecutive days.\n");
        prompt.append("   - You MUST schedule ALL working days, including those AFTER ALL holidays.\n");
        prompt.append("   - DO NOT stop after holidays. Continue scheduling until ALL working days are done.\n");
        prompt.append("5. Generate JSON following FORMAT A.\n");
        prompt.append("6. VERIFY before submitting:\n");
        if (workingDays != null && !workingDays.isEmpty()) {
            prompt.append("   a. Count keys: You need ").append(workingDays.size()).append(" keys: ").append(workingDays.toString()).append("\n");
            boolean hasFriday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("friday"));
            boolean hasSaturday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("saturday"));
            
            // Special check if there was a holiday (Friday/Saturday are working days after holiday)
            if (hasFriday || hasSaturday) {
                prompt.append("   b. HOLIDAY CHECK (CRITICAL):\n");
                prompt.append("      - If there was a holiday earlier in the week (e.g., Thursday), did you continue scheduling?\n");
                if (hasFriday) {
                    prompt.append("      - Do you have 'friday' key? If NO, add it NOW. Friday is a WORKING DAY.\n");
                }
                if (hasSaturday) {
                    prompt.append("      - Do you have 'saturday' key? If NO, add it NOW. Saturday is a WORKING DAY.\n");
                }
                prompt.append("      - A holiday does NOT end the week. You MUST schedule ALL working days.\n");
            } else {
                if (hasFriday) {
                    prompt.append("   b. Do you have 'friday' key? If NO, add it.\n");
                }
                if (hasSaturday) {
                    prompt.append("   b. Do you have 'saturday' key? If NO, add it.\n");
                }
            }
        }
        prompt.append("   c. Every day has 8+ assignments (2 doctors × 2 clinics × 2 shifts)?\n");
        prompt.append("   d. All Doctor IDs are valid?\n");
        prompt.append("   e. LEAVE REQUEST CHECK (CRITICAL):\n");
        prompt.append("      - Check 'APPROVED LEAVE' section - verify you did NOT assign doctors on their leave shifts:\n");
        prompt.append("        * If doctor has MORNING leave on a day → verify they are NOT assigned for MORNING (08:00-11:00) on that day.\n");
        prompt.append("        * If doctor has AFTERNOON leave on a day → verify they are NOT assigned for AFTERNOON (13:00-18:00) on that day.\n");
        prompt.append("        * If doctor has FULL_DAY leave on a day → verify they are NOT assigned for ANY shift on that day.\n");
        prompt.append("      - Remember: Doctors with MORNING leave CAN work AFTERNOON, and vice versa.\n");
        prompt.append("   f. Check assignments per doctor:\n");
        prompt.append("      * For doctors NOT mentioned in USER REQUEST: They should have BOTH morning AND afternoon on EVERY day.\n");
        prompt.append("      * For doctors mentioned in USER REQUEST: Follow the user's request EXACTLY - DO NOT expand:\n");
        prompt.append("        - If user asks doctor to take 1 day off → that doctor should NOT work ONLY that 1 day (works all other days).\n");
        prompt.append("        - If user asks doctor to take 2 days off → that doctor should NOT work ONLY those 2 days (works all other days).\n");
        prompt.append("        - If user asks doctor to work only morning → that doctor should work ONLY morning (not afternoon).\n");
        prompt.append("        - If user asks doctor to work only afternoon → that doctor should work ONLY afternoon (not morning).\n");
        prompt.append("        - CRITICAL: Count the days user mentioned. DO NOT add extra days off.\n\n");
    }

    private void appendFinalInstructions(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== FINAL INSTRUCTION ===\n");
        prompt.append("Generate the JSON now. Priority order:\n");
        prompt.append("1. USER REQUEST (HIGHEST) - Follow exactly what user asks (e.g., doctor off, only morning, etc.).\n");
        prompt.append("2. ALL working days must be covered (including Friday/Saturday if listed).\n");
        prompt.append("3. If there are holidays in the week (single or multiple consecutive days), you MUST still schedule ALL working days AFTER ALL holidays.\n");
        prompt.append("4. For doctors NOT in USER REQUEST: work BOTH morning AND afternoon on EVERY working day.\n");
        prompt.append("5. Each clinic-shift combination needs at least 2 doctors.\n");
        prompt.append("6. Use ONLY valid Doctor IDs from 'AVAILABLE DOCTORS' list.\n");
        if (workingDays != null && !workingDays.isEmpty()) {
            prompt.append("\n");
            prompt.append("FINAL CHECK: Count your JSON keys. You need exactly ").append(workingDays.size()).append(" keys for: ").append(workingDays.toString()).append("\n");
            prompt.append("If you have fewer keys, you are missing days. Add them NOW.\n");
        }
    }

    // Lấy danh sách bác sĩ đang hoạt động
    private List<User> getActiveDoctors() {
        return userRoleRepo.findAll().stream()
                .filter(ur -> ur.getIsActive() && ur.getRole() != null && ur.getRole().getId() == 3)
                .map(UserRole::getUser)
                .filter(u -> u != null && u.getIsActive())
                .distinct()
                .collect(Collectors.toList());
    }

    // Lấy danh sách phòng khám đang hoạt động
    private List<sunshine_dental_care.entities.Clinic> getActiveClinics() {
        return clinicRepo.findAll().stream()
                .filter(c -> c.getIsActive() != null && c.getIsActive())
                .collect(Collectors.toList());
    }

    // Lấy danh sách các phòng đang hoạt động
    private List<sunshine_dental_care.entities.Room> getActiveRooms() {
        return roomRepo.findByIsActiveTrueOrderByRoomNameAsc();
    }

    // Lấy map doctorId -> list chuyên môn đang hoạt động của bác sĩ
    private Map<Integer, List<String>> getDoctorSpecialtiesMap(List<User> doctors) {
        if (doctors.isEmpty())
            return new HashMap<>();
        List<Integer> doctorIds = doctors.stream().map(User::getId).collect(Collectors.toList());
        var specialties = doctorSpecialtyRepo.findByDoctorIdInAndIsActiveTrue(doctorIds);
        Map<Integer, List<String>> result = new HashMap<>();
        specialties.forEach(s -> {
            Integer doctorId = s.getDoctor().getId();
            result.computeIfAbsent(doctorId, k -> new ArrayList<>()).add(s.getSpecialtyName());
        });
        return result;
    }

    // Lấy map doctorId -> các ngày nghỉ của bác sĩ trong tuần đó (theo ca)
    private Map<Integer, Map<String, String>> getDoctorLeaveDaysWithShiftMap(List<User> doctors, LocalDate weekStart, LocalDate weekEnd,
            String[] dayNames) {
        if (doctors.isEmpty())
            return new HashMap<>();
        List<Integer> doctorIds = doctors.stream().map(User::getId).collect(Collectors.toList());
        var leaveRequests = leaveRequestRepo.findApprovedByUserIdsAndDateRange(doctorIds, weekStart, weekEnd);

        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (var leave : leaveRequests) {
            Integer doctorId = leave.getUser().getId();
            String shiftType = leave.getShiftType(); // MORNING, AFTERNOON, FULL_DAY, or null
            
            for (LocalDate date = leave.getStartDate(); !date.isAfter(leave.getEndDate()); date = date.plusDays(1)) {
                if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    // Tìm dayName tương ứng với date
                    for (int i = 0; i < dayNames.length; i++) {
                        if (weekStart.plusDays(i).equals(date)) {
                            String dayName = dayNames[i];
                            result.computeIfAbsent(doctorId, k -> new HashMap<>()).put(dayName, 
                                    shiftType != null ? shiftType : "FULL_DAY");
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }
}
