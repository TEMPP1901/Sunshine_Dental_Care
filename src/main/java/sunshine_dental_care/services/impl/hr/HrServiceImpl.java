package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.HrDocDto;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.HrService;
import sunshine_dental_care.services.interfaces.hr.ScheduleValidationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrServiceImpl implements HrService {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;
    private final ScheduleValidationService scheduleValidationService;
    private final sunshine_dental_care.repositories.hr.LeaveRequestRepo leaveRequestRepo;

    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);

    @Override
    @Transactional
    // PHƯƠNG THỨC QUAN TRỌNG: Tạo phân lịch cho 1 tuần, xác thực theo luật cho
    // trước
    public List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request) {
        log.info("Creating weekly schedule for week starting: {}", request.getWeekStart());

        // Xác thực request trước khi tạo lịch
        ValidationResultDto validation = scheduleValidationService.validateSchedule(request);
        if (!validation.isValid()) {
            log.warn("Schedule validation failed: {}", validation.getErrors());
            throw new ScheduleValidationException(String.join(", ", validation.getErrors()));
        }

        List<DoctorSchedule> schedules = new ArrayList<>();
        String[] days = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = request.getDailyAssignments()
                    .get(dayName);

            if (dayAssignments != null) {
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    // CHECK: Nếu bác sĩ có leave request approved trong ngày và ca này → không tạo
                    // schedule
                    // Xác định shiftType dựa vào startTime
                    String shiftType = null;
                    if (assignment.getStartTime() != null) {
                        LocalTime startTime = assignment.getStartTime();
                        if (startTime.isBefore(LocalTime.of(11, 0))) {
                            shiftType = "MORNING";
                        } else {
                            shiftType = "AFTERNOON";
                        }
                    }

                    // Check leave request theo ca (nếu có shiftType) hoặc theo ngày (nếu không có)
                    boolean hasApprovedLeave;
                    if (shiftType != null) {
                        hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDateAndShift(
                                assignment.getDoctorId(), workDate, shiftType);
                    } else {
                        hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(
                                assignment.getDoctorId(), workDate);
                    }

                    if (hasApprovedLeave) {
                        log.info(
                                "Skipping schedule creation for doctor {} on {} shift {} - has approved leave request for this shift",
                                assignment.getDoctorId(), workDate, shiftType != null ? shiftType : "FULL_DAY");
                        continue; // Bỏ qua, không tạo schedule cho ca nghỉ
                    }

                    DoctorSchedule schedule = new DoctorSchedule();

                    // ÁNH XẠ THÔNG TIN CƠ BẢN: tạo entity với id để tránh lỗi serialize proxy
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

        // Sau khi lưu schedule: Nếu có schedule MORNING → Set ACTIVE cho tất cả
        // schedule AFTERNOON của doctor trong ngày
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            // Nhóm schedule theo doctor
            Map<Integer, List<DoctorSchedule>> schedulesByDoctor = savedSchedules.stream()
                    .filter(s -> s.getWorkDate().equals(workDate))
                    .collect(Collectors.groupingBy(s -> s.getDoctor().getId()));

            for (Map.Entry<Integer, List<DoctorSchedule>> entry : schedulesByDoctor.entrySet()) {
                Integer doctorId = entry.getKey();
                List<DoctorSchedule> doctorSchedules = entry.getValue();

                // Kiểm tra xem có schedule MORNING không
                boolean hasMorningSchedule = doctorSchedules.stream()
                        .anyMatch(s -> s.getStartTime() != null && s.getStartTime().isBefore(LUNCH_BREAK_START));

                if (hasMorningSchedule) {
                    // Nếu có schedule MORNING → Set ACTIVE cho tất cả schedule AFTERNOON của doctor
                    // trong ngày
                    for (DoctorSchedule schedule : doctorSchedules) {
                        if (schedule.getStartTime() != null && schedule.getStartTime().isAfter(LUNCH_BREAK_START)) {
                            // Đây là schedule AFTERNOON
                            if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                                schedule.setStatus("ACTIVE");
                                doctorScheduleRepo.save(schedule);
                                log.info(
                                        "Set schedule {} (AFTERNOON) to ACTIVE for doctor {} on {} (has MORNING schedule - default ACTIVE)",
                                        schedule.getId(), doctorId, workDate);
                            }
                        }
                    }
                }
            }
        }
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
        log.debug("Fetching next week schedules from {} to {}, found {} schedules", nextWeekStart, nextWeekEnd,
                schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getScheduleByDate(LocalDate date) {
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(date);
        log.debug("Fetching schedules for date {}, found {} schedules", date, schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request) {
        return scheduleValidationService.validateSchedule(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getMySchedule(Integer userId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(5); // Monday to Saturday
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndDateRange(userId, weekStart, weekEnd);
        log.debug("Fetching my schedule for user {} from {} to {}, found {} schedules", userId, weekStart, weekEnd,
                schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getMyScheduleByDate(Integer userId, LocalDate date) {
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, date);
        log.debug("Fetching my schedule for user {} on date {}, found {} schedules", userId, date, schedules.size());
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // validateSchedule và validateAssignment đã được chuyển sang
    // ScheduleValidationServiceImpl

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
                        doctor.getCode(),
                        doctor.getSpecialty(), // Specialty
                        null, // DepartmentResponse (Cần phải map nếu muốn truyền)
                        List.of());
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
