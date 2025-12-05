package sunshine_dental_care.services.impl.hr.schedule;

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

    // Build prompt cho AI dựa trên mô tả người dùng và ngày bắt đầu tuần
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
        prompt.append(
                "  - If user asks a doctor to take ONE day off → that doctor should NOT work ONLY that ONE day.\n");
        prompt.append(
                "    * DO NOT extend to multiple days. If user says 'off Monday', doctor works Tuesday-Saturday.\n");
        prompt.append(
                "  - If user asks a doctor to work only morning OR only afternoon → follow that request EXACTLY.\n");
        prompt.append("    * DO NOT change it to both shifts. If user says 'only morning', assign ONLY morning.\n");
        prompt.append(
                "  - If user asks for specific assignments → prioritize those assignments EXACTLY as requested.\n");
        prompt.append("  - DO NOT interpret or expand user requests. Follow them literally and precisely.\n");
        prompt.append(
                "  - Only apply default rules (e.g., both shifts, all days) when user request doesn't specify otherwise.\n");
        prompt.append("\n");
    }

    // Lấy resource data cơ bản cho prompt AI (doctors, clinics, holidays, workingDays...)
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

        List<sunshine_dental_care.entities.Clinic> activeClinics = getActiveClinics();
        List<sunshine_dental_care.entities.Room> rooms = getActiveRooms();
        Map<Integer, List<sunshine_dental_care.entities.Room>> roomsByClinic = rooms.stream()
                .filter(r -> r.getClinic() != null)
                .collect(Collectors.groupingBy(r -> r.getClinic().getId()));

        prompt.append("AVAILABLE CLINICS & ROOMS (ONLY active clinics - you can ONLY assign to these clinics):\n");
        if (activeClinics.isEmpty()) {
            prompt.append("WARNING: No active clinics available for scheduling!\n");
        } else {
            for (var clinic : activeClinics) {
                prompt.append(String.format("- Clinic ID: %d, Name: %s\n", clinic.getId(), clinic.getClinicName()));
                List<sunshine_dental_care.entities.Room> clinicRooms = roomsByClinic.get(clinic.getId());
                if (clinicRooms != null) {
                    for (var room : clinicRooms) {
                        prompt.append(String.format("  - Room ID: %d, Name: %s\n", room.getId(), room.getRoomName()));
                    }
                }
            }
        }
        prompt.append("\n");
        prompt.append("IMPORTANT: Only clinics listed above are available for scheduling.\n");
        prompt.append("If a clinic is NOT in the list above, it means that clinic is INACTIVE and you CANNOT assign to it.\n");
        prompt.append("\n");

        // Duyệt tất cả clinic để thống kê ngày holiday, dùng để AI không phân công nhầm
        List<sunshine_dental_care.entities.Clinic> allClinics = clinicRepo.findAll();
        prompt.append("=== CRITICAL: CLINIC HOLIDAYS (MANDATORY - DO NOT SCHEDULE) ===\n");
        prompt.append("ABSOLUTE RULE: DO NOT SCHEDULE ANY DOCTOR FOR ANY SHIFT AT THESE CLINICS ON THESE DATES:\n");
        prompt.append("IMPORTANT LOGIC:\n");
        prompt.append("- When a clinic has a holiday on a specific date, that clinic becomes INACTIVE ONLY during the holiday period.\n");
        prompt.append("- Clinic is INACTIVE from the holiday start date to the holiday end date (inclusive).\n");
        prompt.append("- Clinic remains ACTIVE before the holiday starts and automatically becomes ACTIVE again after the holiday ends.\n");
        prompt.append("- If a clinic has holiday on a date, it is REMOVED from 'AVAILABLE CLINICS' list above for that date.\n");
        prompt.append("- If a clinic has holiday on a date, you CANNOT and MUST NOT assign anyone to that clinic on that date.\n");
        prompt.append("- Example: If Clinic 1 has holiday from Dec 29 to Jan 2, it is INACTIVE on Dec 29-30-31 and Jan 1-2, but ACTIVE on Dec 28 and Jan 3+.\n\n");
        boolean hasHolidays = false;
        for (var clinic : allClinics) {
            List<String> clinicHolidays = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                LocalDate date = weekStart.plusDays(i);
                if (holidayService.isHoliday(date, clinic.getId())) {
                    String holidayName = holidayService.getHolidayName(date, clinic.getId());
                    clinicHolidays.add(date.toString() + " (" + date.getDayOfWeek() + ")" 
                            + (holidayName != null ? " - " + holidayName : ""));
                }
            }
            if (!clinicHolidays.isEmpty()) {
                hasHolidays = true;
                boolean isActive = activeClinics.stream().anyMatch(c -> c.getId().equals(clinic.getId()));
                boolean hasHolidayThisWeek = false;
                for (int i = 0; i < 6; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    if (holidayService.isHoliday(date, clinic.getId())) {
                        hasHolidayThisWeek = true;
                        break;
                    }
                }
                
                if (hasHolidayThisWeek && !isActive) {
                    prompt.append(String.format("Clinic ID %d (%s) - INACTIVE due to holidays this week\n", 
                            clinic.getId(), clinic.getClinicName()));
                    prompt.append(String.format("   HOLIDAY DATES THIS WEEK: %s\n", String.join(", ", clinicHolidays)));
                    prompt.append(String.format("   → This clinic is INACTIVE and NOT in 'AVAILABLE CLINICS' list.\n"));
                    prompt.append(String.format("   → DO NOT assign ANY doctor to Clinic %d on these holiday dates.\n", clinic.getId()));
                    prompt.append(String.format("   → You CANNOT assign to this clinic because it is inactive during holidays.\n"));
                } else if (hasHolidayThisWeek && isActive) {
                    prompt.append(String.format(" Clinic ID %d (%s) - ACTIVE but has holidays on specific dates\n", 
                            clinic.getId(), clinic.getClinicName()));
                    prompt.append(String.format("   HOLIDAY DATES THIS WEEK: %s\n", String.join(", ", clinicHolidays)));
                    prompt.append(String.format("   → DO NOT assign ANY doctor to Clinic %d on these specific holiday dates.\n", clinic.getId()));
                    prompt.append(String.format("   → You CAN assign doctors to Clinic %d on other dates (non-holiday dates) this week.\n", clinic.getId()));
                } else {
                    prompt.append(String.format(" Clinic ID %d (%s) - Has holidays but NOT this week\n", 
                            clinic.getId(), clinic.getClinicName()));
                    prompt.append(String.format("   → This clinic is available for scheduling this week.\n"));
                }
            }
        }
        if (!hasHolidays) {
            prompt.append("None - All clinics are active for all working days.\n");
        }
        prompt.append("\n");

        LocalDate weekEnd = weekStart.plusDays(5);
        String[] dayNames = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        List<String> workingDays = new ArrayList<>();
        prompt.append("WORKING DAYS (MUST SCHEDULE - Only days with at least 1 active clinic):\n");
        for (int i = 0; i < dayNames.length; i++) {
            LocalDate date = weekStart.plusDays(i);
            boolean allClinicsOnHoliday = true;
            for (sunshine_dental_care.entities.Clinic clinic : allClinics) {
                if (!holidayService.isHoliday(date, clinic.getId())) {
                    allClinicsOnHoliday = false;
                    break;
                }
            }
            if (!allClinicsOnHoliday) {
                workingDays.add(dayNames[i]);
                String dayName = dayNames[i].toUpperCase();
                String emphasis = "";
                if (dayName.equals("FRIDAY") || dayName.equals("SATURDAY")) {
                    emphasis += " - WORKING DAY (MUST schedule)";
                }
                prompt.append(String.format("  - %s (%s)%s\n", dayName, date, emphasis));
            } else {
                String dayName = dayNames[i].toUpperCase();
                prompt.append(String.format("  - %s (%s) - SKIPPED (ALL clinics on holiday - DO NOT create schedule for this day)\n", dayName, date));
            }
        }
        prompt.append("\n");

        Map<Integer, Map<String, String>> doctorLeaveDaysWithShift = getDoctorLeaveDaysWithShiftMap(doctors, weekStart,
                weekEnd, dayNames);
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
            prompt.append(
                    "CRITICAL: If a doctor has leave for MORNING shift on a day, you CAN assign them for AFTERNOON shift on that same day.\n");
            prompt.append(
                    "CRITICAL: If a doctor has leave for AFTERNOON shift on a day, you CAN assign them for MORNING shift on that same day.\n");
            prompt.append(
                    "CRITICAL: If a doctor has leave for FULL_DAY or no shift specified, you CANNOT assign them for ANY shift on that day.\n");
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

    // Yêu cầu cứng của lịch (luật bắt buộc)
    private void appendHardConstraints(StringBuilder prompt) {
        prompt.append("=== BLOCK 1: HARD CONSTRAINTS (MUST FOLLOW) ===\n");
        prompt.append("Violating these = REJECTION\n\n");
        prompt.append(
                "1. NO OVERLAP (ABSOLUTE): A doctor CANNOT be in two places at once. NEVER assign the same doctor to multiple clinics/rooms at the same time.\n");
        prompt.append("2. CLINIC: Exactly 2 clinics active every working day.\n");
        prompt.append(
                "3. STAFFING: Morning (08:00-11:00) & Afternoon (13:00-18:00): Min 2 doctors per clinic per shift (IF AVAILABLE).\n");
        prompt.append(
                "4. SPECIALTY: Every specialty must be present in BOTH clinics daily (IF POSSIBLE). A doctor covers ALL their listed specialties.\n");
        prompt.append("5. LEAVE/HOLIDAYS (STRICT - ABSOLUTE RULE): No assignments on approved leave days or CLINIC HOLIDAYS.\n");
        prompt.append("   - CHECK 'CLINIC HOLIDAYS' LIST ABOVE FIRST - THIS IS MANDATORY.\n");
        prompt.append(
                "   - If a date is listed for a clinic in 'CLINIC HOLIDAYS', YOU MUST NOT schedule ANY doctor for that clinic on that date.\n");
        prompt.append("   - Example: If Clinic 1 has holiday on 2025-05-01, NO ONE works at Clinic 1 on 2025-05-01.\n");
        prompt.append("   - If Clinic 1 has holiday on Monday, you CAN assign doctors to Clinic 2 on Monday, but NOT to Clinic 1.\n");
        prompt.append("   - CRITICAL: When a clinic has a holiday, that clinic is INACTIVE. Skip that clinic entirely for that date.\n");
        prompt.append(
                "   - If a doctor has leave for MORNING shift → DO NOT assign them for MORNING, but CAN assign for AFTERNOON.\n");
        prompt.append(
                "   - If a doctor has leave for AFTERNOON shift → DO NOT assign them for AFTERNOON, but CAN assign for MORNING.\n");
        prompt.append("   - If a doctor has leave for FULL_DAY → DO NOT assign them for ANY shift on that day.\n");
        prompt.append("6. VALID IDs: Use ONLY Doctor IDs from 'AVAILABLE DOCTORS' list.\n");
        prompt.append("7. COVERAGE: Schedule EVERY date in 'WORKING DAYS' (including Friday/Saturday if listed).\n");

    }

    // Yêu cầu mềm (ưu tiên nếu có thể)
    private void appendSoftConstraints(StringBuilder prompt) {
        prompt.append("=== BLOCK 2: SOFT CONSTRAINTS (PREFERRED) ===\n");
        prompt.append("These improve quality but won't cause rejection if violated.\n");
        prompt.append("NOTE: USER REQUEST overrides these preferences.\n\n");
        prompt.append("1. FAIRNESS: Assign ALL doctors to ALL working days (no one idle).\n");
        prompt.append("   - Exception: If USER REQUEST asks a doctor to take time off, follow that request.\n");
        prompt.append("2. FULL DAY (DEFAULT): By default, assign each doctor to BOTH Morning AND Afternoon shifts.\n");
        prompt.append(
                "   - Exception: If USER REQUEST asks for only morning OR only afternoon, follow that request.\n");
        prompt.append(
                "   - Exception: If USER REQUEST asks a doctor to take a day off, that doctor should NOT work that day.\n");
        prompt.append(
                "3. ROTATION: If working both shifts, switch clinics (Morning Clinic 1 -> Afternoon Clinic 2).\n");
        prompt.append(
                "4. BALANCE: Distribute work evenly - avoid some doctors working all shifts while others work few.\n");
        prompt.append("   - Exception: If USER REQUEST specifies different distribution, follow that request.\n\n");
    }

    // Các lỗi cấm tuyệt đối
    private void appendForbiddenMistakes(StringBuilder prompt) {
        prompt.append("=== BLOCK 3: FORBIDDEN MISTAKES ===\n");
        prompt.append("Common errors that cause rejection:\n\n");
        prompt.append("1. ASSIGNING TO CLINICS ON HOLIDAY DATES (CRITICAL):\n");
        prompt.append("   - If a clinic has holiday on a date (listed in 'CLINIC HOLIDAYS'), you MUST NOT assign ANY doctor to that clinic on that date.\n");
        prompt.append("   - This is an ABSOLUTE RULE - violating this will cause immediate rejection.\n");
        prompt.append("   - Example: If Clinic 1 has holiday on Monday, NO assignments should have clinicId=1 on Monday.\n");
        prompt.append("2. Invalid Doctor IDs (using IDs not in 'AVAILABLE DOCTORS' list).\n");
        prompt.append("3. Skipping Friday/Saturday if they are in 'WORKING DAYS'.\n");
        prompt.append("4. Same clinic for both shifts of same doctor (must rotate).\n");
        prompt.append("5. Markdown/text outside JSON (only return pure JSON).\n");
        prompt.append("6. Missing days from 'WORKING DAYS' list.\n");
        prompt.append("7. Less than 2 doctors per clinic per shift (unless impossible or clinic is on holiday).\n");
        prompt.append("8. DOUBLE BOOKING: Assigning the same doctor to multiple slots at the same time.\n\n");
    }

    // Chỉ cần ví dụ output format JSON và chú thích giải thích mẫu nào là đúng 
    private void appendOutputFormat(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== BLOCK 4: OUTPUT FORMAT ===\n");
        prompt.append("Return valid JSON only.\n\n");

        prompt.append("FORMAT A: SUCCESS (Complete schedule for ALL working days)\n");
        prompt.append("IMPORTANT: You MUST include ALL ").append(workingDays != null ? workingDays.size() : "X")
                .append(" working days listed above.\n");

        prompt.append("{\n");
        prompt.append("  \"dailyAssignments\": {\n");

        if (workingDays != null && !workingDays.isEmpty()) {
            boolean hasFriday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("friday"));
            boolean hasSaturday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("saturday"));

            for (int i = 0; i < workingDays.size(); i++) {
                String day = workingDays.get(i);
                boolean isFridayOrSaturday = day.equalsIgnoreCase("friday") || day.equalsIgnoreCase("saturday");

                prompt.append("    \"").append(day).append("\": [\n");

                // Ghi chi tiết ví dụ cho ngày đầu tuần và thứ 6, 7 nếu có
                if (i == 0 || (isFridayOrSaturday && (hasFriday || hasSaturday))) {
                    if (isFridayOrSaturday) {
                        prompt.append("      // ").append(day.toUpperCase())
                                .append(" IS A WORKING DAY - MUST INCLUDE\n");
                    }
                    prompt.append(
                            "      {\"doctorId\": 21, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 22, \"clinicId\": 1, \"roomId\": 2, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 23, \"clinicId\": 2, \"roomId\": 5, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 24, \"clinicId\": 2, \"roomId\": 6, \"startTime\": \"08:00\", \"endTime\": \"11:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 25, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
                    prompt.append(
                            "      {\"doctorId\": 26, \"clinicId\": 1, \"roomId\": 2, \"startTime\": \"13:00\", \"endTime\": \"18:00\"},\n");
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

    // Nhấn mạnh logic kiểm tra trước khi tạo JSON cuối cùng
    private void appendThoughtProcess(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== BLOCK 5: LOGIC STEPS ===\n");
        prompt.append("1. Read USER REQUEST first - understand what the user wants EXACTLY:\n");
        prompt.append("   - Does user ask any doctor to take time off? → Count HOW MANY days user mentioned.\n");
        prompt.append(
                "     * If user says 'doctor X off Monday' → doctor X works Tuesday-Saturday (ONLY Monday off).\n");
        prompt.append(
                "     * If user says 'doctor X off Monday and Tuesday' → doctor X works Wednesday-Saturday (ONLY those 2 days off).\n");
        prompt.append("     * DO NOT extend beyond what user specified. If user says 1 day, it's ONLY 1 day.\n");
        prompt.append(
                "   - Does user ask any doctor to work only morning OR only afternoon? → Follow that request EXACTLY.\n");
        prompt.append("     * DO NOT change it to both shifts. Follow the request precisely.\n");
        prompt.append("   - Does user specify any special assignments? → Prioritize those EXACTLY as requested.\n");
        prompt.append("2. Check 'CLINIC HOLIDAYS' section FIRST - identify which clinics are inactive on which dates:\n");
        prompt.append("   - For each clinic with holidays, mark those dates as 'NO ASSIGNMENTS' for that clinic.\n");
        prompt.append("   - Example: If Clinic 1 has holiday on Monday, you CANNOT assign anyone to Clinic 1 on Monday.\n");
        prompt.append("   - But you CAN still assign doctors to Clinic 2 on Monday (if Clinic 2 is not on holiday).\n");
        prompt.append("3. Read 'WORKING DAYS' list - count how many days you need to schedule.\n");
        prompt.append("   - Remember: If a clinic has holiday on a day, skip that clinic for that day entirely.\n");
        prompt.append("4. Check 'APPROVED LEAVE' section - understand which doctors are off and for which shifts:\n");
        prompt.append(
                "   - If a doctor has leave for MORNING shift on a day → They CAN work AFTERNOON shift on that day.\n");
        prompt.append(
                "   - If a doctor has leave for AFTERNOON shift on a day → They CAN work MORNING shift on that day.\n");
        prompt.append("   - If a doctor has leave for FULL_DAY on a day → They CANNOT work ANY shift on that day.\n");
        prompt.append("5. Check if schedule is POSSIBLE:\n");
        prompt.append("   - Do you have at least 4 doctors available? (Need 2 per clinic × 2 clinics)\n");
        prompt.append("   - Are enough doctors available after accounting for leave and USER REQUEST?\n");
        prompt.append("   - Remember: Doctors with MORNING leave can still work AFTERNOON, and vice versa.\n");
        prompt.append("   - Account for holidays: If a clinic has holiday on a day, you cannot assign anyone to that clinic on that day.\n");
        prompt.append("   - If NO → Return FORMAT B (status: fail) with reason.\n");
        prompt.append("   - If YES → Continue to step 6.\n");
        prompt.append("6. Plan assignments for ALL working days:\n");
        prompt.append("   - CRITICAL: You MUST create schedule for ALL days listed in 'WORKING DAYS' section above.\n");
        prompt.append("   - Count the working days: ").append(workingDays != null ? workingDays.size() : "X")
                .append(" days - ALL must be included in your JSON response.\n");
        prompt.append("   - IMPORTANT: Only days in 'WORKING DAYS' list need to be scheduled. Days where ALL clinics are on holiday are NOT in this list.\n");
        prompt.append("   - Apply USER REQUEST first - but ONLY what user specified:\n");
        prompt.append("     * If user asks doctor to take 1 day off → that doctor works on ALL OTHER days.\n");
        prompt.append("     * If user asks doctor to take 2 days off → that doctor works on ALL OTHER days.\n");
        prompt.append("     * DO NOT extend user's request. If user says 1 day, it's ONLY 1 day.\n");
        prompt.append("   - For doctors not mentioned in USER REQUEST, apply default rules (both shifts, all days in 'WORKING DAYS' list).\n");
        prompt.append("   - FIRST: Check 'CLINIC HOLIDAYS' - identify which clinics are ACTIVE on each day:\n");
        prompt.append("     * If Clinic 1 has holiday on Monday but Clinic 2 is active → Monday is in 'WORKING DAYS', create schedule for Monday.\n");
        prompt.append("       → DO NOT assign anyone to Clinic 1 on Monday (it's on holiday).\n");
        prompt.append("       → DO assign doctors to Clinic 2 on Monday (it's active).\n");
        prompt.append("     * If Clinic 2 has holiday on Tuesday but Clinic 1 is active → Tuesday is in 'WORKING DAYS', create schedule for Tuesday.\n");
        prompt.append("       → DO NOT assign anyone to Clinic 2 on Tuesday (it's on holiday).\n");
        prompt.append("       → DO assign doctors to Clinic 1 on Tuesday (it's active).\n");
        prompt.append("     * For each day in 'WORKING DAYS', identify which clinics are ACTIVE (not on holiday) - create schedule for those clinics.\n");
        prompt.append("     * As long as a clinic is ACTIVE (not on holiday), you CAN and SHOULD create schedule for that clinic on that day.\n");
        prompt.append("     * You can assign doctors to any ACTIVE clinic on any day.\n");
        prompt.append("     * CRITICAL: If a clinic has holiday on a day, skip that clinic for that day, but still create schedule for other active clinics.\n");
        prompt.append("     * Example: Monday is in 'WORKING DAYS' → At least 1 clinic is active on Monday → Create schedule for Monday (assign to active clinics only).\n");
        prompt.append("     * Example: If ALL clinics have holiday on a day → That day is NOT in 'WORKING DAYS' list → DO NOT include that day in your JSON.\n");
        prompt.append("   - THEN: Check 'APPROVED LEAVE' - respect leave requests by shift:\n");
        prompt.append(
                "     * If doctor has MORNING leave on a day → DO NOT assign them for MORNING, but CAN assign for AFTERNOON.\n");
        prompt.append(
                "     * If doctor has AFTERNOON leave on a day → DO NOT assign them for AFTERNOON, but CAN assign for MORNING.\n");
        prompt.append("     * If doctor has FULL_DAY leave on a day → DO NOT assign them for ANY shift on that day.\n");

        prompt.append("7. Generate JSON following FORMAT A.\n");
        prompt.append("8. VERIFY before submitting:\n");
        if (workingDays != null && !workingDays.isEmpty()) {
            prompt.append("   a. CRITICAL - Count keys: You need EXACTLY ").append(workingDays.size()).append(" keys: ")
                    .append(workingDays.toString()).append("\n");
            prompt.append("      - Check each day: Do you have 'monday'? 'tuesday'? 'wednesday'? 'thursday'? 'friday'? 'saturday'?\n");
            prompt.append("      - If ANY day is missing, ADD IT NOW (even if empty array []).\n");
            boolean hasFriday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("friday"));
            boolean hasSaturday = workingDays.stream().anyMatch(d -> d.equalsIgnoreCase("saturday"));

            if (hasFriday || hasSaturday) {
                if (hasFriday) {
                    prompt.append("   b. Do you have 'friday' key? If NO, add it.\n");
                }
                if (hasSaturday) {
                    prompt.append("   b. Do you have 'saturday' key? If NO, add it.\n");
                }
            } else {
                if (hasFriday) {
                    prompt.append("   b. Do you have 'friday' key? If NO, add it.\n");
                }
                if (hasSaturday) {
                    prompt.append("   b. Do you have 'saturday' key? If NO, add it.\n");
                }
            }
        }
        prompt.append("   c. Every day has assignments (or empty array [] if all clinics on holiday)?\n");
        prompt.append("      - Minimum: 8 assignments per day (2 doctors × 2 clinics × 2 shifts) when clinics are available.\n");
        prompt.append("      - If all clinics have holiday on a day, that day can have empty array [].\n");
        prompt.append("   d. All Doctor IDs are valid?\n");
        prompt.append("   e. HOLIDAY CHECK (CRITICAL - CHECK FIRST):\n");
        prompt.append("      - Check 'CLINIC HOLIDAYS' section above - verify you did NOT assign ANY doctor to clinics on holiday dates:\n");
        prompt.append("        * For each date listed in 'CLINIC HOLIDAYS', verify NO assignments exist for that clinic on that date.\n");
        prompt.append("        * Example: If Clinic 1 has holiday on Monday, verify NO assignments have clinicId=1 on Monday.\n");
        prompt.append("        * If Clinic 2 has holiday on Tuesday, verify NO assignments have clinicId=2 on Tuesday.\n");
        prompt.append("      - CRITICAL: If you find ANY assignment to a clinic on its holiday date, REMOVE IT immediately.\n");
        prompt.append("   f. LEAVE REQUEST CHECK (CRITICAL):\n");
        prompt.append(
                "      - Check 'APPROVED LEAVE' section - verify you did NOT assign doctors on their leave shifts:\n");
        prompt.append(
                "        * If doctor has MORNING leave on a day → verify they are NOT assigned for MORNING (08:00-11:00) on that day.\n");
        prompt.append(
                "        * If doctor has AFTERNOON leave on a day → verify they are NOT assigned for AFTERNOON (13:00-18:00) on that day.\n");
        prompt.append(
                "        * If doctor has FULL_DAY leave on a day → verify they are NOT assigned for ANY shift on that day.\n");
        prompt.append("      - Remember: Doctors with MORNING leave CAN work AFTERNOON, and vice versa.\n");
        prompt.append("   g. Check assignments per doctor:\n");
        prompt.append(
                "      * For doctors NOT mentioned in USER REQUEST: They should have BOTH morning AND afternoon on EVERY day.\n");
        prompt.append(
                "      * For doctors mentioned in USER REQUEST: Follow the user's request EXACTLY - DO NOT expand:\n");
        prompt.append(
                "        - If user asks doctor to take 1 day off → that doctor should NOT work ONLY that 1 day (works all other days).\n");
        prompt.append(
                "        - If user asks doctor to take 2 days off → that doctor should NOT work ONLY those 2 days (works all other days).\n");
        prompt.append(
                "        - If user asks doctor to work only morning → that doctor should work ONLY morning (not afternoon).\n");
        prompt.append(
                "        - If user asks doctor to work only afternoon → that doctor should work ONLY afternoon (not morning).\n");
        prompt.append("        - CRITICAL: Count the days user mentioned. DO NOT add extra days off.\n\n");
    }

    // Lệnh cuối kiểm tra đầy đủ trước khi trả JSON
    private void appendFinalInstructions(StringBuilder prompt, List<String> workingDays) {
        prompt.append("=== FINAL INSTRUCTION ===\n");
        prompt.append("Generate the JSON now. Priority order:\n");
        prompt.append(
                "1. USER REQUEST (HIGHEST) - Follow exactly what user asks (e.g., doctor off, only morning, etc.).\n");
        prompt.append("2. CLINIC HOLIDAYS (MANDATORY) - DO NOT assign ANY doctor to clinics on their holiday dates.\n");
        prompt.append("   - Check 'CLINIC HOLIDAYS' list above - if a clinic has holiday on a date, skip that clinic for that date.\n");
        prompt.append("   - You can still assign doctors to other clinics (if they're not on holiday).\n");
        prompt.append("3. ALL working days must be covered (only days with at least 1 active clinic).\n");
        prompt.append("   - CRITICAL: You MUST include ALL days listed in 'WORKING DAYS' section above in your JSON.\n");
        prompt.append("   - IMPORTANT: Days where ALL clinics are on holiday are NOT in 'WORKING DAYS' list - DO NOT create schedule for those days.\n");
        prompt.append("   - DO NOT include days in your JSON if they are NOT in the 'WORKING DAYS' list above.\n");
        prompt.append("   - For each day in 'WORKING DAYS', check which clinics are ACTIVE (not on holiday) and create schedule for those clinics.\n");
        prompt.append("   - If a clinic is ACTIVE on a day (not on holiday), you CAN and SHOULD create schedule for that clinic on that day.\n");
        prompt.append("   - If a clinic has holiday on a day, that clinic is INACTIVE and you CANNOT assign to it on that day.\n");
        prompt.append("   - Example: If Clinic 1 has holiday on Monday but Clinic 2 is active → Create schedule for Monday and assign doctors to Clinic 2.\n");
        prompt.append("   - Example: If BOTH Clinic 1 and Clinic 2 have holiday on Monday → Monday is NOT in 'WORKING DAYS' list, DO NOT include Monday in your JSON.\n");
        prompt.append("   - IMPORTANT: Only create schedule for days listed in 'WORKING DAYS' section.\n");
        prompt.append("4. For doctors NOT in USER REQUEST: work BOTH morning AND afternoon on EVERY working day.\n");
        prompt.append("   - This applies to days when clinics are available (not on holiday).\n");
        prompt.append("5. Each clinic-shift combination needs at least 2 doctors (when clinic is active, not on holiday).\n");
        prompt.append("6. Use ONLY valid Doctor IDs from 'AVAILABLE DOCTORS' list.\n");
        if (workingDays != null && !workingDays.isEmpty()) {
            prompt.append("\n");
            prompt.append("=== FINAL CHECK - CRITICAL - DO THIS BEFORE SUBMITTING ===\n");
            prompt.append("STEP 1: Count your JSON keys. You need EXACTLY ").append(workingDays.size())
                    .append(" keys. Current required keys: ").append(workingDays.toString()).append("\n");
            prompt.append("STEP 2: Verify each required key exists in your JSON:\n");
            for (String day : workingDays) {
                prompt.append("   - Check: Do you have \"").append(day).append("\" key? If NO, ADD IT NOW.\n");
            }
            prompt.append("STEP 3: Verify you did NOT include days that are NOT in the 'WORKING DAYS' list above.\n");
            prompt.append("   - If a day is NOT in 'WORKING DAYS' list (all clinics on holiday), DO NOT include it in your JSON.\n");
            prompt.append("STEP 4: If ANY required key is missing, your response will be REJECTED. Add missing keys NOW.\n");
            prompt.append("STEP 5: Your JSON structure should look like this (only include days from 'WORKING DAYS' list):\n");
            prompt.append("   {\n");
            prompt.append("     \"dailyAssignments\": {\n");
            for (String day : workingDays) {
                prompt.append("       \"").append(day).append("\": [ ... assignments ... ],\n");
            }
            prompt.append("     }\n");
            prompt.append("   }\n");
            prompt.append("DO NOT submit until you have verified ALL ").append(workingDays.size()).append(" required keys are present.\n");
        }
    }

    // Trả về danh sách bác sĩ đang hoạt động
    private List<User> getActiveDoctors() {
        return userRoleRepo.findAll().stream()
                .filter(ur -> ur.getIsActive() && ur.getRole() != null && ur.getRole().getId() == 3)
                .map(UserRole::getUser)
                .filter(u -> u != null && u.getIsActive())
                .distinct()
                .collect(Collectors.toList());
    }

    // Trả về danh sách phòng khám đang hoạt động
    private List<sunshine_dental_care.entities.Clinic> getActiveClinics() {
        return clinicRepo.findAll().stream()
                .filter(c -> c.getIsActive() != null && c.getIsActive())
                .collect(Collectors.toList());
    }

    // Trả về danh sách phòng đang hoạt động
    private List<sunshine_dental_care.entities.Room> getActiveRooms() {
        return roomRepo.findByIsActiveTrueOrderByRoomNameAsc();
    }

    // Map doctorId -> list chuyên môn của bác sĩ (chỉ chuyên môn đang active)
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

    // Map doctorId -> các ngày nghỉ của bác sĩ trong tuần đó (bao gồm loại ca)
    private Map<Integer, Map<String, String>> getDoctorLeaveDaysWithShiftMap(List<User> doctors, LocalDate weekStart,
            LocalDate weekEnd,
            String[] dayNames) {
        if (doctors.isEmpty())
            return new HashMap<>();
        List<Integer> doctorIds = doctors.stream().map(User::getId).collect(Collectors.toList());
        var leaveRequests = leaveRequestRepo.findApprovedByUserIdsAndDateRange(doctorIds, weekStart, weekEnd);

        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (var leave : leaveRequests) {
            Integer doctorId = leave.getUser().getId();
            String shiftType = leave.getShiftType();

            for (LocalDate date = leave.getStartDate(); !date.isAfter(leave.getEndDate()); date = date.plusDays(1)) {
                if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
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
