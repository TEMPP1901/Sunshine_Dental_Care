package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import sunshine_dental_care.dto.hrDTO.RuleBasedScheduleData;
import sunshine_dental_care.dto.hrDTO.ScheduleRequirements;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.DoctorSpecialty;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.AiScheduleGenerationService;
import sunshine_dental_care.services.interfaces.hr.HrService;
import sunshine_dental_care.services.interfaces.hr.ScheduleValidationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrServiceImpl implements HrService {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;
    private final UserRoleRepo userRoleRepo;
    private final AiScheduleGenerationService aiScheduleGenerationService;
    private final ScheduleValidationService scheduleValidationService;

    @Override
    @Transactional
    // PHƯƠNG THỨC QUAN TRỌNG: Tạo phân lịch cho 1 tuần, xác thực theo luật cho trước
    public List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request) {
        log.info("Creating weekly schedule for week starting: {}", request.getWeekStart());

        // Xác thực request trước khi tạo lịch
        ValidationResultDto validation = scheduleValidationService.validateSchedule(request);
        if (!validation.isValid()) {
            log.warn("Schedule validation failed: {}", validation.getErrors());
            throw new ScheduleValidationException(String.join(", ", validation.getErrors()));
        }

        List<DoctorSchedule> schedules = new ArrayList<>();
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = request.getDailyAssignments().get(dayName);

            if (dayAssignments != null) {
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
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

    // validateSchedule và validateAssignment đã được chuyển sang ScheduleValidationServiceImpl

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
                        List.of()
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

    @Override
    @Deprecated
    @Transactional(readOnly = true)
    public CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description) {
        // PHƯƠNG THỨC QUAN TRỌNG: sinh lịch AI từ description
        return aiScheduleGenerationService.generateScheduleFromDescription(weekStart, description);
    }

    @Override
    // PHƯƠNG THỨC QUAN TRỌNG: sinh lịch tuần dựa theo mô tả văn bản bằng rule-based
    public CreateWeeklyScheduleRequest generateScheduleFromDescriptionRuleBased(LocalDate weekStart, String description) {
        log.info("Using rule-based logic to generate schedule from description: {}", description);

        RuleBasedScheduleData data = loadDataForRuleBased();
        ScheduleRequirements requirements = parseDescription(description, data);

        return generateDayAssignments(weekStart, data, requirements);
    }

    // PHƯƠNG THỨC QUAN TRỌNG: tải dữ liệu phục vụ sinh lịch rule-based
    private RuleBasedScheduleData loadDataForRuleBased() {
        List<User> allUsers = userRepo.findAll();

        Map<Integer, List<UserRole>> userRolesMap = new HashMap<>();
        List<UserRole> allUserRoles = userRoleRepo.findAll().stream()
            .filter(ur -> Boolean.TRUE.equals(ur.getIsActive()))
            .collect(Collectors.toList());
        for (UserRole ur : allUserRoles) {
            if (ur.getUser() != null && ur.getUser().getId() != null) {
                Integer userId = ur.getUser().getId();
                userRolesMap.putIfAbsent(userId, new ArrayList<>());
                userRolesMap.get(userId).add(ur);
            }
        }

        List<User> allDoctors = allUsers.stream()
            .filter(user -> {
                if (!Boolean.TRUE.equals(user.getIsActive())) return false;
                List<UserRole> roles = userRolesMap.getOrDefault(user.getId(), new ArrayList<>());
                return roles.stream().anyMatch(ur -> {
                    if (ur == null || ur.getRole() == null) return false;
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null &&
                            (roleName.equalsIgnoreCase("DOCTOR") || roleName.equalsIgnoreCase("BÁC SĨ"));
                });
            })
            .collect(Collectors.toList());

        List<Clinic> allClinics = clinicRepo.findAll().stream()
            .filter(c -> c.getIsActive() != null && c.getIsActive())
            .collect(Collectors.toList());

        List<Room> allRooms = roomRepo.findByIsActiveTrueOrderByRoomNameAsc();

        Set<Integer> doctorIds = allDoctors.stream().map(User::getId).collect(Collectors.toSet());
        Map<Integer, List<String>> doctorSpecialtiesMap = new HashMap<>();
        if (!doctorIds.isEmpty()) {
            List<DoctorSpecialty> allDoctorSpecialties = doctorSpecialtyRepo.findByDoctorIdInAndIsActiveTrue(new ArrayList<>(doctorIds));
            for (DoctorSpecialty ds : allDoctorSpecialties) {
                Integer doctorId = ds.getDoctor().getId();
                doctorSpecialtiesMap.putIfAbsent(doctorId, new ArrayList<>());
                doctorSpecialtiesMap.get(doctorId).add(ds.getSpecialtyName().trim());
            }
        }

        Map<String, List<User>> doctorsBySpecialty = new HashMap<>();
        for (User doctor : allDoctors) {
            List<String> doctorSpecialties = doctorSpecialtiesMap.getOrDefault(doctor.getId(), new ArrayList<>());
            if (doctorSpecialties.isEmpty()) {
                String oldSpecialty = (doctor.getSpecialty() != null && !doctor.getSpecialty().trim().isEmpty())
                        ? doctor.getSpecialty().trim()
                        : "No Specialty";
                doctorSpecialties = Collections.singletonList(oldSpecialty);
                doctorSpecialtiesMap.put(doctor.getId(), doctorSpecialties);
            }
            for (String specialty : doctorSpecialties) {
                doctorsBySpecialty.putIfAbsent(specialty, new ArrayList<>());
                if (!doctorsBySpecialty.get(specialty).contains(doctor)) {
                    doctorsBySpecialty.get(specialty).add(doctor);
                }
            }
        }

        return new RuleBasedScheduleData(allDoctors, allClinics, allRooms, doctorsBySpecialty);
    }

    // PHƯƠNG THỨC QUAN TRỌNG: phân tích description để lấy các yêu cầu về lịch
    private ScheduleRequirements parseDescription(String description, RuleBasedScheduleData data) {
        String descLower = description.toLowerCase();

        boolean bothClinicsActive = descLower.contains("cả hai cơ sở") || descLower.contains("both clinics")
                || descLower.contains("hai cơ sở") || descLower.contains("cả hai");
        boolean allSpecialties = descLower.contains("tất cả chuyên khoa") || descLower.contains("all specialties")
                || descLower.contains("tất cả chuyên") || descLower.contains("all specialty");

        Set<String> mentionedSpecialties = new HashSet<>();
        if (!allSpecialties) {
            for (String specialty : data.getDoctorsBySpecialty().keySet()) {
                if (descLower.contains(specialty.toLowerCase())) {
                    mentionedSpecialties.add(specialty);
                }
            }
        }

        Set<Integer> mentionedClinicIds = new HashSet<>();
        for (Clinic clinic : data.getAllClinics()) {
            String clinicName = clinic.getClinicName().toLowerCase();
            if (descLower.contains(clinicName.toLowerCase()) ||
                    descLower.contains("clinic " + clinic.getId()) ||
                    descLower.contains("q" + clinic.getId())) {
                mentionedClinicIds.add(clinic.getId());
            }
        }

        if (bothClinicsActive || mentionedClinicIds.isEmpty()) {
            mentionedClinicIds.addAll(data.getAllClinics().stream().map(Clinic::getId).collect(Collectors.toSet()));
        }

        if (bothClinicsActive && mentionedClinicIds.size() < 2 && data.getAllClinics().size() >= 2) {
            mentionedClinicIds.clear();
            mentionedClinicIds.addAll(data.getAllClinics().stream()
                    .limit(2)
                    .map(Clinic::getId)
                    .collect(Collectors.toSet()));
        }

        Map<String, String> dayMapping = new HashMap<>();
        dayMapping.put("monday", "monday");
        dayMapping.put("thứ 2", "monday");
        dayMapping.put("thứ hai", "monday");
        dayMapping.put("tuesday", "tuesday");
        dayMapping.put("thứ 3", "tuesday");
        dayMapping.put("thứ ba", "tuesday");
        dayMapping.put("wednesday", "wednesday");
        dayMapping.put("thứ 4", "wednesday");
        dayMapping.put("thứ tư", "wednesday");
        dayMapping.put("thursday", "thursday");
        dayMapping.put("thứ 5", "thursday");
        dayMapping.put("thứ năm", "thursday");
        dayMapping.put("friday", "friday");
        dayMapping.put("thứ 6", "friday");
        dayMapping.put("thứ sáu", "friday");
        dayMapping.put("saturday", "saturday");
        dayMapping.put("thứ 7", "saturday");
        dayMapping.put("thứ bảy", "saturday");

        Set<String> mentionedDays = new HashSet<>();
        for (Map.Entry<String, String> entry : dayMapping.entrySet()) {
            if (descLower.contains(entry.getKey())) {
                mentionedDays.add(entry.getValue());
            }
        }

        if (mentionedDays.isEmpty()) {
            mentionedDays.addAll(Set.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday"));
        }

        boolean hasMorning = descLower.contains("sáng") || descLower.contains("morning");
        boolean hasAfternoon = descLower.contains("chiều") || descLower.contains("afternoon");

        if (!hasMorning && !hasAfternoon) {
            hasMorning = true;
            hasAfternoon = true;
        }

        Set<String> specialtiesToCover = allSpecialties
                ? new HashSet<>(data.getDoctorsBySpecialty().keySet())
                : (mentionedSpecialties.isEmpty() ? new HashSet<>(data.getDoctorsBySpecialty().keySet()) : mentionedSpecialties);

        // Parse doctors to exclude (ví dụ: "doc5 nghỉ cả tuần")
        Set<Integer> doctorsToExclude = new HashSet<>();
        // Note: Rule-based logic không parse doctors to exclude, để trống
        // AI service sẽ xử lý việc này

        return new ScheduleRequirements(
                mentionedClinicIds,
                mentionedDays,
                specialtiesToCover,
                hasMorning,
                hasAfternoon,
                bothClinicsActive,
                doctorsToExclude
        );
    }

    // PHƯƠNG THỨC QUAN TRỌNG: Sinh dayAssignments dựa trên requirements phân tích được
    private CreateWeeklyScheduleRequest generateDayAssignments(
            LocalDate weekStart,
            RuleBasedScheduleData data,
            ScheduleRequirements requirements
    ) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = new HashMap<>();
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        LocalTime morningStart = LocalTime.of(8, 0);
        LocalTime morningEnd = LocalTime.of(11, 0);
        LocalTime afternoonStart = LocalTime.of(13, 0);
        LocalTime afternoonEnd = LocalTime.of(18, 0);

        Map<String, Integer> specialtyRotationIndex = new HashMap<>();
        Map<Integer, Integer> doctorAssignmentCount = new HashMap<>();

        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            if (!requirements.getDays().contains(dayName)) {
                continue;
            }

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = new ArrayList<>();

            List<Integer> clinicIds = new ArrayList<>(requirements.getClinicIds());
            if (requirements.isBothClinicsActive() && clinicIds.size() >= 2) {
                clinicIds = clinicIds.subList(0, Math.min(2, clinicIds.size()));
            }

            Map<Integer, Map<String, Boolean>> clinicShiftCoverage = new HashMap<>();
            for (Integer clinicId : clinicIds) {
                Map<String, Boolean> shifts = new HashMap<>();
                shifts.put("morning", false);
                shifts.put("afternoon", false);
                clinicShiftCoverage.put(clinicId, shifts);
            }

            // Lấy danh sách bác sĩ nghỉ (không được assign)
            Set<Integer> doctorsToExclude = requirements.getDoctorsToExclude();
            
            for (String specialty : requirements.getSpecialtiesToCover()) {
                List<User> specialtyDoctors = data.getDoctorsBySpecialty().get(specialty);
                if (specialtyDoctors == null || specialtyDoctors.isEmpty()) {
                    continue;
                }
                
                // Loại trừ bác sĩ nghỉ
                specialtyDoctors = specialtyDoctors.stream()
                    .filter(doc -> !doctorsToExclude.contains(doc.getId()))
                    .collect(Collectors.toList());
                
                if (specialtyDoctors.isEmpty()) {
                    log.warn("No available doctors for specialty {} after excluding doctors on {}", specialty, dayName);
                    continue;
                }

                int rotationIdx = specialtyRotationIndex.getOrDefault(specialty, 0);

                for (Integer clinicId : clinicIds) {
                    // Chọn room theo clinic (không cần filter theo specialty nữa)
                    Room suitableRoom = data.getAllRooms().stream()
                            .filter(r -> r.getClinic() != null && r.getClinic().getId().equals(clinicId))
                            .findFirst()
                            .orElse(null);

                    if (suitableRoom == null) {
                        continue;
                    }

                    User selectedDoctor = specialtyDoctors.get(rotationIdx % specialtyDoctors.size());

                    if (requirements.hasMorning() && !clinicShiftCoverage.get(clinicId).get("morning")) {
                        CreateWeeklyScheduleRequest.DoctorAssignmentRequest morningAssignment =
                                new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                        morningAssignment.setDoctorId(selectedDoctor.getId());
                        morningAssignment.setClinicId(clinicId);
                        morningAssignment.setRoomId(suitableRoom.getId());
                        morningAssignment.setStartTime(morningStart);
                        morningAssignment.setEndTime(morningEnd);
                        dayAssignments.add(morningAssignment);
                        clinicShiftCoverage.get(clinicId).put("morning", true);
                        doctorAssignmentCount.put(selectedDoctor.getId(),
                                doctorAssignmentCount.getOrDefault(selectedDoctor.getId(), 0) + 1);
                    }

                    if (requirements.hasAfternoon() && !clinicShiftCoverage.get(clinicId).get("afternoon")) {
                        CreateWeeklyScheduleRequest.DoctorAssignmentRequest afternoonAssignment =
                                new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                        afternoonAssignment.setDoctorId(selectedDoctor.getId());
                        afternoonAssignment.setClinicId(clinicId);
                        afternoonAssignment.setRoomId(suitableRoom.getId());
                        afternoonAssignment.setStartTime(afternoonStart);
                        afternoonAssignment.setEndTime(afternoonEnd);
                        dayAssignments.add(afternoonAssignment);
                        clinicShiftCoverage.get(clinicId).put("afternoon", true);
                        doctorAssignmentCount.put(selectedDoctor.getId(),
                                doctorAssignmentCount.getOrDefault(selectedDoctor.getId(), 0) + 1);
                    }
                }

                specialtyRotationIndex.put(specialty, rotationIdx + 1);
            }

            for (Integer clinicId : clinicIds) {
                Map<String, Boolean> shifts = clinicShiftCoverage.get(clinicId);

                if (!shifts.get("morning") || !shifts.get("afternoon")) {
                    User fillDoctor = data.getAllDoctors().stream()
                            .min((d1, d2) -> Integer.compare(
                                    doctorAssignmentCount.getOrDefault(d1.getId(), 0),
                                    doctorAssignmentCount.getOrDefault(d2.getId(), 0)
                            ))
                            .orElse(null);

                    if (fillDoctor != null) {
                        Room fillRoom = data.getAllRooms().stream()
                                .filter(r -> r.getClinic() != null && r.getClinic().getId().equals(clinicId))
                                .findFirst()
                                .orElse(null);

                        if (fillRoom != null) {
                            if (!shifts.get("morning") && requirements.hasMorning()) {
                                CreateWeeklyScheduleRequest.DoctorAssignmentRequest morningAssignment =
                                        new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                                morningAssignment.setDoctorId(fillDoctor.getId());
                                morningAssignment.setClinicId(clinicId);
                                morningAssignment.setRoomId(fillRoom.getId());
                                morningAssignment.setStartTime(morningStart);
                                morningAssignment.setEndTime(morningEnd);
                                dayAssignments.add(morningAssignment);
                                clinicShiftCoverage.get(clinicId).put("morning", true);
                            }

                            if (!shifts.get("afternoon") && requirements.hasAfternoon()) {
                                CreateWeeklyScheduleRequest.DoctorAssignmentRequest afternoonAssignment =
                                        new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
                                afternoonAssignment.setDoctorId(fillDoctor.getId());
                                afternoonAssignment.setClinicId(clinicId);
                                afternoonAssignment.setRoomId(fillRoom.getId());
                                afternoonAssignment.setStartTime(afternoonStart);
                                afternoonAssignment.setEndTime(afternoonEnd);
                                dayAssignments.add(afternoonAssignment);
                                clinicShiftCoverage.get(clinicId).put("afternoon", true);
                            }
                        }
                    }
                }
            }

            if (!dayAssignments.isEmpty()) {
                dailyAssignments.put(dayName, dayAssignments);
            }
        }

        CreateWeeklyScheduleRequest request = new CreateWeeklyScheduleRequest();
        request.setWeekStart(weekStart);
        request.setDailyAssignments(dailyAssignments);
        request.setNote("Auto-generated by rule-based logic from description");

        return request;
    }
}
