package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.HrDocDto;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.HrService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrServiceImpl implements HrService {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final EntityManager entityManager;
    private final UserRepo userRepo;
    private final DepartmentRepo departmentRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;

    @Override
    @Transactional
    // PHƯƠNG THỨC QUAN TRỌNG: Tạo phân lịch cho 1 tuần, xác thực theo luật cho trước
    public List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request) {
        log.info("Creating weekly schedule for week starting: {}", request.getWeekStart());

        // Xác thực request trước khi tạo lịch
        ValidationResultDto validation = validateSchedule(request);
        if (!validation.isValid()) {
            log.warn("Schedule validation failed: {}", validation.getErrors());
            throw new ScheduleValidationException(String.join(", ", validation.getErrors()));
        }

        List<DoctorSchedule> schedules = new ArrayList<>();

        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments =
                    request.getDailyAssignments().get(dayName);

            if (dayAssignments != null) {
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    DoctorSchedule schedule = new DoctorSchedule();

                    // ÁNH XẠ THÔNG TIN CƠ BẢN, tạo entity với id để tránh lỗi serialize proxy
                    User doctor = new User();
                    doctor.setId(assignment.getDoctorId());
                    schedule.setDoctor(doctor);

                    Clinic clinic = new Clinic();
                    clinic.setId(assignment.getClinicId());
                    schedule.setClinic(clinic);

                    if (assignment.getRoomId() != null && assignment.getRoomId() > 0) {
                        Room room = new Room();
                        room.setId(assignment.getRoomId());
                        schedule.setRoom(room);
                    } else {
                        schedule.setRoom(null);
                    }

                    // Chair luôn null, sẽ chọn sau khi đặt lịch hẹn
                    schedule.setChair(null);

                    schedule.setWorkDate(workDate);
                    schedule.setStartTime(assignment.getStartTime());
                    schedule.setEndTime(assignment.getEndTime());
                    schedule.setStatus("ACTIVE");
                    schedule.setNote(assignment.getNote());
                    schedule.setCreatedAt(java.time.Instant.now());
                    schedule.setUpdatedAt(java.time.Instant.now());

                    schedules.add(schedule);
                }
            }
        }

        List<DoctorSchedule> savedSchedules = doctorScheduleRepo.saveAll(schedules);
        return savedSchedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getCurrentWeekSchedule() {
        LocalDate today = LocalDate.now();
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(today);
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getScheduleByWeek(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(5);
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);
        log.debug("Fetching schedules for week {} to {}, found {} schedules", weekStart, weekEnd, schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getNextWeekSchedule() {
        LocalDate today = LocalDate.now();
        int dayOfWeek = today.getDayOfWeek().getValue();
        int daysUntilNextMonday = (8 - dayOfWeek) % 7;
        if (daysUntilNextMonday == 0) {
            daysUntilNextMonday = 7;
        }
        LocalDate nextWeekStart = today.plusDays(daysUntilNextMonday);
        LocalDate nextWeekEnd = nextWeekStart.plusDays(5);

        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWeekRange(nextWeekStart, nextWeekEnd);
        log.debug("Fetching next week schedules from {} to {}, found {} schedules", nextWeekStart, nextWeekEnd, schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    // PHƯƠNG THỨC QUAN TRỌNG: Xác thực phân công tuần (luật phức tạp, đảm bảo hợp lệ)
    public ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request) {
        ValidationResultDto result = new ValidationResultDto();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = request.getWeekStart();
        LocalDate weekEnd = request.getWeekStart().plusDays(5);

        int dayOfWeek = today.getDayOfWeek().getValue();
        int daysFromMonday = (dayOfWeek == 1) ? 0 : (dayOfWeek == 7) ? 6 : dayOfWeek - 1;
        LocalDate currentWeekMonday = today.minusDays(daysFromMonday);
        LocalDate nextWeekMonday = currentWeekMonday.plusWeeks(1);

        // Không cho phép tạo lịch cho tuần đã qua
        if (weekEnd.isBefore(today)) {
            result.addError("Cannot create schedule for past week. Week end date (" + weekEnd + ") is before today (" + today + ")");
        }

        // Chỉ cho phép tạo cho tuần tiếp theo trở đi
        if (weekStart.equals(currentWeekMonday) || weekStart.isBefore(nextWeekMonday)) {
            result.addError("Cannot create schedule for current week. Only next week and future weeks are allowed. Current week Monday: " + currentWeekMonday + ", Selected: " + weekStart);
        }

        // Ngày bắt đầu phải là thứ Hai
        if (weekStart.getDayOfWeek().getValue() != 1) {
            result.addError("Week start date must be a Monday. Selected date: " + weekStart + " (day: " + weekStart.getDayOfWeek() + ")");
        }

        if (request.getDailyAssignments() == null || request.getDailyAssignments().isEmpty()) {
            result.addError("No daily assignments provided for the week");
        }

        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments =
                    request.getDailyAssignments().get(dayName);

            // Nếu ngày nghỉ thì bỏ qua không cần validate
            if (dayAssignments == null || dayAssignments.isEmpty()) {
                continue;
            }

            Map<Integer, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> doctorAssignments = new HashMap<>();

            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                doctorAssignments.putIfAbsent(assignment.getDoctorId(), new ArrayList<>());
                doctorAssignments.get(assignment.getDoctorId()).add(assignment);
            }

            for (Map.Entry<Integer, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> entry : doctorAssignments.entrySet()) {
                Integer doctorId = entry.getKey();
                List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = entry.getValue();

                if (assignments.size() > 1) {
                    result.addError("Doctor ID " + doctorId + " has " + assignments.size() + " assignments on " + dayName + ". Each doctor should have only 1 assignment (full day) per day");
                }

                for (int i = 0; i < assignments.size(); i++) {
                    CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment1 = assignments.get(i);
                    if (assignment1.getStartTime().isAfter(assignment1.getEndTime())) {
                        result.addError("Start time must be before end time for doctor ID " + doctorId + " on " + dayName);
                    }
                    for (int j = i + 1; j < assignments.size(); j++) {
                        CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment2 = assignments.get(j);
                        boolean overlaps = assignment1.getStartTime().isBefore(assignment2.getEndTime())
                                && assignment2.getStartTime().isBefore(assignment1.getEndTime());
                        if (overlaps) {
                            result.addError("Doctor ID " + doctorId + " has overlapping time slots on " + dayName
                                    + " (" + assignment1.getStartTime() + "-" + assignment1.getEndTime()
                                    + " and " + assignment2.getStartTime() + "-" + assignment2.getEndTime() + ")");
                        }
                    }
                }
                if (doctorScheduleRepo.existsByDoctorAndDate(doctorId, workDate)) {
                    String errorMsg = "Doctor ID " + doctorId + " is already assigned on " + workDate;
                    result.addError(errorMsg);
                }
            }

            Set<Integer> clinicIdsInDay = new HashSet<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                clinicIdsInDay.add(assignment.getClinicId());
            }

            if (clinicIdsInDay.size() > 2) {
                result.addError("Each day can have at most 2 clinics. Found " + clinicIdsInDay.size() + " clinics on " + dayName);
            } else if (clinicIdsInDay.size() == 2) {
                // LUẬT QUAN TRỌNG: Mỗi department phải có bác sĩ ở cả hai cơ sở nếu 2 clinic hoạt động
                Map<Integer, Map<Integer, Set<Integer>>> deptClinicDoctors = new HashMap<>();

                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    User doctor = userRepo.findById(assignment.getDoctorId()).orElse(null);
                    if (doctor == null) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " not found");
                        continue;
                    }

                    if (!Boolean.TRUE.equals(doctor.getIsActive())) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " (" + doctor.getFullName() + ") is inactive and cannot be assigned to schedule");
                        continue;
                    }

                    Department dept = doctor.getDepartment();
                    if (dept == null) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " does not have a department assigned");
                        continue;
                    }

                    Integer deptId = dept.getId();
                    Integer clinicId = assignment.getClinicId();
                    deptClinicDoctors.putIfAbsent(deptId, new HashMap<>());
                    deptClinicDoctors.get(deptId).putIfAbsent(clinicId, new HashSet<>());
                    deptClinicDoctors.get(deptId).get(clinicId).add(assignment.getDoctorId());
                }

                for (Map.Entry<Integer, Map<Integer, Set<Integer>>> deptEntry : deptClinicDoctors.entrySet()) {
                    Integer deptId = deptEntry.getKey();
                    Map<Integer, Set<Integer>> clinicDoctors = deptEntry.getValue();

                    Department dept = departmentRepo.findById(deptId).orElse(null);
                    String deptName = (dept != null) ? dept.getDepartmentName() : "Department ID " + deptId;

                    if (clinicDoctors.size() != 2) {
                        result.addError("Department \"" + deptName + "\" must have doctors assigned to both clinics on " + dayName + ". Found in " + clinicDoctors.size() + " clinic(s)");
                    } else {
                        for (Integer clinicId : clinicIdsInDay) {
                            if (!clinicDoctors.containsKey(clinicId) || clinicDoctors.get(clinicId).isEmpty()) {
                                result.addError("Department \"" + deptName + "\" must have at least one doctor assigned to clinic ID " + clinicId + " on " + dayName);
                            }
                        }

                        int totalDoctors = 0;
                        int clinic1Count = 0;
                        int clinic2Count = 0;
                        // LUẬT QUAN TRỌNG: Phân bổ đều giữa các clinic nếu nhiều bác sĩ
                        for (Map.Entry<Integer, Set<Integer>> clinicEntry : clinicDoctors.entrySet()) {
                            int count = clinicEntry.getValue().size();
                            totalDoctors += count;
                            if (clinic1Count == 0) {
                                clinic1Count = count;
                            } else {
                                clinic2Count = count;
                            }
                        }

                        if (totalDoctors >= 3) {
                            int diff = Math.abs(clinic1Count - clinic2Count);

                            if (totalDoctors == 3 && diff > 1) {
                                result.addError("Department \"" + deptName + "\" has " + totalDoctors + " doctors but uneven distribution (" + clinic1Count + " vs " + clinic2Count + ") on " + dayName + ". Recommended: 2-1 or 1-2 distribution");
                            } else if (totalDoctors == 4 && diff > 2) {
                                result.addError("Department \"" + deptName + "\" has " + totalDoctors + " doctors but uneven distribution (" + clinic1Count + " vs " + clinic2Count + ") on " + dayName + ". Recommended: 2-2 distribution");
                            } else if (totalDoctors > 4 && diff > (totalDoctors / 2)) {
                                result.addError("Department \"" + deptName + "\" has " + totalDoctors + " doctors but uneven distribution (" + clinic1Count + " vs " + clinic2Count + ") on " + dayName + ". Recommended: more balanced distribution");
                            }
                        }
                    }
                }
            } else if (clinicIdsInDay.size() == 1) {
                // LUẬT QUAN TRỌNG: Nếu chỉ 1 clinic/week, phòng ban có bác sĩ phải có bác sĩ ở clinic đang hoạt động
                Integer workingClinicId = clinicIdsInDay.iterator().next();
                Map<Integer, Set<Integer>> deptDoctors = new HashMap<>();

                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    User doctor = userRepo.findById(assignment.getDoctorId()).orElse(null);
                    if (doctor == null) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " not found");
                        continue;
                    }
                    if (!Boolean.TRUE.equals(doctor.getIsActive())) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " (" + doctor.getFullName() + ") is inactive and cannot be assigned to schedule");
                        continue;
                    }
                    Department dept = doctor.getDepartment();
                    if (dept == null) {
                        result.addError("Doctor ID " + assignment.getDoctorId() + " does not have a department assigned");
                        continue;
                    }
                    Integer deptId = dept.getId();
                    deptDoctors.putIfAbsent(deptId, new HashSet<>());
                    deptDoctors.get(deptId).add(assignment.getDoctorId());
                }

                Set<Integer> departmentsWithDoctors = new HashSet<>(deptDoctors.keySet());
                for (Integer deptId : departmentsWithDoctors) {
                    Department dept = departmentRepo.findById(deptId).orElse(null);
                    if (dept == null) {
                        continue;
                    }
                    Set<Integer> doctorsInDept = deptDoctors.get(deptId);
                    if (doctorsInDept == null || doctorsInDept.isEmpty()) {
                        Clinic clinic = clinicRepo.findById(workingClinicId).orElse(null);
                        String clinicName = clinic != null ? clinic.getClinicName() : "Clinic ID " + workingClinicId;
                        result.addError("Department \"" + dept.getDepartmentName() + "\" must have at least one doctor assigned to " + clinicName + " on " + dayName + " (departments with doctors must have doctors when clinic is working)");
                    }
                }
            }
        }

        return result;
    }

    // CHUYỂN ĐỔI ENTITY SANG DTO, LẤY ĐẦY ĐỦ THÔNG TIN LIÊN QUAN
    private DoctorScheduleDto convertToDto(DoctorSchedule schedule) {
        DoctorScheduleDto dto = new DoctorScheduleDto();
        dto.setId(schedule.getId());

        if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
            User doctor = userRepo.findById(schedule.getDoctor().getId()).orElse(null);
            if (doctor != null) {
                HrDocDto doctorDto = new HrDocDto(
                        doctor.getId(),
                        doctor.getFullName(),
                        doctor.getEmail(),
                        doctor.getPhone(),
                        doctor.getAvatarUrl(),
                        doctor.getCode()
                );
                dto.setDoctor(doctorDto);
            }
        }

        if (schedule.getClinic() != null && schedule.getClinic().getId() != null) {
            Clinic clinic = clinicRepo.findById(schedule.getClinic().getId()).orElse(null);
            if (clinic != null) {
                ClinicResponse clinicDto = new ClinicResponse();
                clinicDto.setId(clinic.getId());
                clinicDto.setClinicName(clinic.getClinicName());
                dto.setClinic(clinicDto);
            }
        }

        if (schedule.getRoom() != null && schedule.getRoom().getId() != null) {
            Room room = roomRepo.findById(schedule.getRoom().getId()).orElse(null);
            if (room != null) {
                RoomResponse roomDto = new RoomResponse();
                roomDto.setId(room.getId());
                roomDto.setRoomName(room.getRoomName());
                if (room.getClinic() != null && room.getClinic().getId() != null) {
                    Clinic roomClinic = clinicRepo.findById(room.getClinic().getId()).orElse(null);
                    if (roomClinic != null) {
                        roomDto.setClinicId(roomClinic.getId());
                        roomDto.setClinicName(roomClinic.getClinicName());
                    }
                }
                dto.setRoom(roomDto);
            }
        }

        dto.setChair(null);

        dto.setWorkDate(schedule.getWorkDate());
        dto.setStartTime(schedule.getStartTime());
        dto.setEndTime(schedule.getEndTime());
        dto.setStatus(schedule.getStatus());
        dto.setNote(schedule.getNote());

        if (schedule.getCreatedAt() != null) {
            dto.setCreatedAt(schedule.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        }
        if (schedule.getUpdatedAt() != null) {
            dto.setUpdatedAt(schedule.getUpdatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        }

        return dto;
    }
}
