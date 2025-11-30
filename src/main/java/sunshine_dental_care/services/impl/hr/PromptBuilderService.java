package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        appendSystemResources(prompt, weekStart);
        appendOutputFormat(prompt);
        appendThoughtProcess(prompt);
        appendFinalInstructions(prompt);
        return prompt.toString();
    }

    // Header quan trọng yêu cầu output JSON chuẩn
    private void appendHeader(StringBuilder prompt) {
        prompt.append("You are an AI-powered HR Schedule Generator for Sunshine Dental Care.\n");
        prompt.append("YOUR OUTPUT MUST BE 100% VALID JSON. NOTHING ELSE.\n");
        prompt.append("STRICTLY: Your ONLY output MUST be a perfectly formatted JSON object starting with { and ending with }.\n");
        prompt.append("STRICTLY: DO NOT output any text, explanation, or markdown wrappers.\n\n");
    }

    // Đưa vào yêu cầu người dùng và ngày bắt đầu tuần
    private void appendUserRequest(StringBuilder prompt, String userDescription, LocalDate weekStart) {
        prompt.append("USER REQUEST (HIGHEST PRIORITY)\n");
        prompt.append("USER REQUEST: \"").append(userDescription).append("\"\n");
        prompt.append("WEEK START DATE: ").append(weekStart).append(" (Monday)\n\n");
        prompt.append("IMPORTANT: Fulfill every requirement from the USER REQUEST.\n\n");
    }

    // Liệt kê các tài nguyên và ràng buộc hệ thống
    private void appendSystemResources(StringBuilder prompt, LocalDate weekStart) {
        prompt.append("SYSTEM RESOURCES AND CONSTRAINTS\n");
        
        // Danh sách bác sĩ đang hoạt động và chuyên môn
        List<User> doctors = getActiveDoctors();
        prompt.append("AVAILABLE DOCTORS (IDs and Names):\n");
        Map<Integer, List<String>> doctorSpecialtiesMap = getDoctorSpecialtiesMap(doctors);
        for (User doctor : doctors) {
            String specialties = String.join(", ", doctorSpecialtiesMap.getOrDefault(doctor.getId(), List.of("General")));
            prompt.append(String.format("- ID: %d, Name: %s, Specialty: %s\n", doctor.getId(), doctor.getFullName(), specialties));
        }
        prompt.append("\n");

        // Liệt kê các phòng khám và phòng
        List<sunshine_dental_care.entities.Clinic> clinics = getActiveClinics();
        List<sunshine_dental_care.entities.Room> rooms = getActiveRooms();
        Map<Integer, List<sunshine_dental_care.entities.Room>> roomsByClinic = rooms.stream()
            .filter(r -> r.getClinic() != null)
            .collect(Collectors.groupingBy(r -> r.getClinic().getId()));

        prompt.append("AVAILABLE CLINICS & ROOMS (IDs and Names):\n");
        for (var clinic : clinics) {
            prompt.append(String.format("- Clinic ID: %d, Name: %s\n", clinic.getId(), clinic.getClinicName()));
            List<sunshine_dental_care.entities.Room> clinicRooms = roomsByClinic.get(clinic.getId());
            if (clinicRooms != null && !clinicRooms.isEmpty()) {
                for (var room : clinicRooms) {
                    prompt.append(String.format("  - Room ID: %d, Name: %s\n", room.getId(), room.getRoomName()));
                }
            }
        }
        prompt.append("\n");

        LocalDate weekEnd = weekStart.plusDays(5);
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        
        // Tính ngày lễ trong tuần dựa vào HolidayService
        List<String> exclusionDays = holidayService.calculateHolidaysInWeek(weekStart);
        if (!exclusionDays.isEmpty()) {
            prompt.append("HOLIDAYS - Do not assign schedules on these dates (Vietnam national holidays):\n");
            prompt.append("Do NOT include these days in your JSON output.\n\n");
            for (int i = 0; i < dayNames.length; i++) {
                if (exclusionDays.contains(dayNames[i])) {
                    LocalDate date = weekStart.plusDays(i);
                    String holidayName = holidayService.getHolidayName(date);
                    if (holidayName == null) {
                        holidayName = "Holiday";
                    }
                    prompt.append(String.format("  - %s (%s): %s\n", dayNames[i].toUpperCase(), date, holidayName));
                }
            }
            prompt.append("\n");
            prompt.append("The days mentioned above are holidays and must NOT be included in the JSON output for 'dailyAssignments'.\n");
            prompt.append("\n");
        }
        
        // Liệt kê ngày làm việc (ngoại trừ ngày lễ)
        List<String> workingDays = new ArrayList<>();
        for (int i = 0; i < dayNames.length; i++) {
            if (!exclusionDays.contains(dayNames[i])) {
                workingDays.add(dayNames[i]);
            }
        }
        if (!workingDays.isEmpty()) {
            prompt.append("WORKING DAYS (Must include in JSON):\n");
            for (String day : workingDays) {
                int dayIndex = java.util.Arrays.asList(dayNames).indexOf(day);
                prompt.append(String.format("  - %s (%s)\n", day.toUpperCase(), weekStart.plusDays(dayIndex)));
            }
            prompt.append("\n");
        }
        
        // Đưa vào lịch nghỉ phép đã được phê duyệt
        Map<Integer, List<String>> doctorLeaveDays = getDoctorLeaveDaysMap(doctors, weekStart, weekEnd, dayNames);
        if (!doctorLeaveDays.isEmpty()) {
            prompt.append("APPROVED LEAVE (Doctors OFF these days):\n");
            doctorLeaveDays.forEach((docId, days) -> 
                prompt.append(String.format("  - Doctor ID %d is OFF on: %s\n", docId, String.join(", ", days).toUpperCase()))
            );
            prompt.append("\n");
        }

        // Đưa vào lịch làm việc đã có (không được chồng lặp)
        List<DoctorSchedule> existingSchedules = doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);
        if (!existingSchedules.isEmpty()) {
            prompt.append("EXISTING SCHEDULES (Do not overlap these):\n");
            existingSchedules.forEach(s -> 
                prompt.append(String.format("  - Doctor ID %d on %s (%s-%s) at Clinic %d\n", 
                    s.getDoctor().getId(), s.getWorkDate(), s.getStartTime(), s.getEndTime(), s.getClinic().getId()))
            );
            prompt.append("\n");
        }
    }

    // Hướng dẫn định dạng JSON output bắt buộc
    private void appendOutputFormat(StringBuilder prompt) {
        prompt.append("OUTPUT FORMAT (You must return only this JSON representation):\n");
        prompt.append("{\n");
        prompt.append("  \"dailyAssignments\": {\n");
        prompt.append("    \"monday\": [{\"doctorId\": 1, \"clinicId\": 1, \"roomId\": 1, \"startTime\": \"08:00\", \"endTime\": \"11:00\"}],\n");
        prompt.append("    \"tuesday\": [...]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
    }

    // Hướng dẫn tư duy giải quyết bài toán lập lịch
    private void appendThoughtProcess(StringBuilder prompt) {
        prompt.append("YOUR LOGIC STEPS:\n");
        prompt.append("1. Analyze the user request and all system constraints.\n");
        prompt.append("2. Create a plan that satisfies every rule.\n");
        prompt.append("3. Generate JSON output based on your plan.\n");
        prompt.append("4. Carefully self-review: If anything is missed, start again from step 2.\n\n");
    }

    // Chỉ thị gửi output cuối cùng là JSON
    private void appendFinalInstructions(StringBuilder prompt) {
        prompt.append("FINAL INSTRUCTION:\n");
        prompt.append("Generate the schedule now. RESPONSE MUST be valid JSON object, starting with { and ending with }.\n");
    }

    // Lấy danh sách bác sĩ đang hoạt động
    private List<User> getActiveDoctors() {
        return userRoleRepo.findAll().stream()
            .filter(ur -> ur.getIsActive() && ur.getRole() != null && "DOCTOR".equals(ur.getRole().getRoleName()))
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
        if (doctors.isEmpty()) return new HashMap<>();
        List<Integer> doctorIds = doctors.stream().map(User::getId).collect(Collectors.toList());
        var specialties = doctorSpecialtyRepo.findByDoctorIdInAndIsActiveTrue(doctorIds);
        Map<Integer, List<String>> result = new HashMap<>();
        specialties.forEach(s -> {
            Integer doctorId = s.getDoctor().getId();
            result.computeIfAbsent(doctorId, k -> new ArrayList<>()).add(s.getSpecialtyName());
        });
        return result;
    }

    // Lấy map doctorId -> các ngày nghỉ của bác sĩ trong tuần đó
    private Map<Integer, List<String>> getDoctorLeaveDaysMap(List<User> doctors, LocalDate weekStart, LocalDate weekEnd, String[] dayNames) {
        if (doctors.isEmpty()) return new HashMap<>();
        List<Integer> doctorIds = doctors.stream().map(User::getId).collect(Collectors.toList());
        var leaveRequests = leaveRequestRepo.findApprovedByUserIdsAndDateRange(doctorIds, weekStart, weekEnd);

        Map<Integer, Set<LocalDate>> doctorLeaveDates = new HashMap<>();
        for (var leave : leaveRequests) {
            Set<LocalDate> dates = doctorLeaveDates.computeIfAbsent(leave.getUser().getId(), k -> new HashSet<>());
            for (LocalDate date = leave.getStartDate(); !date.isAfter(leave.getEndDate()); date = date.plusDays(1)) {
                if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                    dates.add(date);
                }
            }
        }

        Map<Integer, List<String>> result = new HashMap<>();
        doctorLeaveDates.forEach((doctorId, leaveDates) -> {
            List<String> leaveDayNames = new ArrayList<>();
            for (int i = 0; i < dayNames.length; i++) {
                if (leaveDates.contains(weekStart.plusDays(i))) {
                    leaveDayNames.add(dayNames[i]);
                }
            }
            if (!leaveDayNames.isEmpty()) {
                result.put(doctorId, leaveDayNames);
            }
        });
        return result;
    }
}
