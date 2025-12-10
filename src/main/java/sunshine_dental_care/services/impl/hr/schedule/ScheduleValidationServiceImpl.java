package sunshine_dental_care.services.impl.hr.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.DoctorSpecialty;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.ScheduleValidationService;

@Service
@RequiredArgsConstructor
public class ScheduleValidationServiceImpl implements ScheduleValidationService {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final RoomRepo roomRepo;
    private final UserRoleRepo userRoleRepo;
    private final HolidayService holidayService;

    // validate lịch làm việc tuần cho bác sĩ
    @Override
    @Transactional(readOnly = true)
    public ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request) {  
        ValidationResultDto result = new ValidationResultDto();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = request.getWeekStart();
        LocalDate weekEnd = request.getWeekStart().plusDays(5);

        int dayOfWeek = today.getDayOfWeek().getValue();
        int daysFromMonday = (dayOfWeek == 1) ? 0 : (dayOfWeek == 7) ? 6 : dayOfWeek - 1;
        LocalDate currentWeekMonday = today.minusDays(daysFromMonday);
        LocalDate nextWeekMonday = currentWeekMonday.plusWeeks(1);

        if (weekEnd.isBefore(today)) {
            result.addError("Cannot create schedule for past week. Week end date (" + weekEnd + ") is before today (" + today + ")");
        }
        if (weekStart.equals(currentWeekMonday) || weekStart.isBefore(nextWeekMonday)) {
            result.addError("Cannot create schedule for current week. Only next week and future weeks are allowed. Current week Monday: " + currentWeekMonday + ", Selected: " + weekStart);
        }
        if (weekStart.getDayOfWeek().getValue() != 1) {
            result.addError("Week start date must be a Monday. Selected date: " + weekStart + " (day: " + weekStart.getDayOfWeek() + ")");
        }
        if (request.getDailyAssignments() == null || request.getDailyAssignments().isEmpty()) {
            result.addError("No daily assignments provided for the week");
        }

        // thu thập tất cả doctorId, clinicId, roomId để cache, optimize query
        Set<Integer> allDoctorIds = new HashSet<>();
        Set<Integer> allClinicIds = new HashSet<>();
        Set<Integer> allRoomIds = new HashSet<>();
        String[] days = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        for (String dayName : days) {
            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = request.getDailyAssignments().get(dayName);
            if (dayAssignments != null) {
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    allDoctorIds.add(assignment.getDoctorId());
                    allClinicIds.add(assignment.getClinicId());
                    if (assignment.getRoomId() != null) {
                        allRoomIds.add(assignment.getRoomId());
                    }
                }
            }
        }

        // cache doctor, clinic, room
        Map<Integer, User> doctorsCache = new HashMap<>();
        if (!allDoctorIds.isEmpty()) {
            List<User> allDoctors = userRepo.findAllById(allDoctorIds);
            for (User doctor : allDoctors) {
                doctorsCache.put(doctor.getId(), doctor);
            }
        }

        Map<Integer, Clinic> clinicsCache = new HashMap<>();
        if (!allClinicIds.isEmpty()) {
            List<Clinic> allClinics = clinicRepo.findAllById(allClinicIds);
            for (Clinic clinic : allClinics) {
                clinicsCache.put(clinic.getId(), clinic);
            }
        }

        Map<Integer, Room> roomsCache = new HashMap<>();
        if (!allRoomIds.isEmpty()) {
            List<Room> allRooms = roomRepo.findAllById(allRoomIds);
            for (Room room : allRooms) {
                roomsCache.put(room.getId(), room);
            }
        }

        // cache specialty của từng doctor
        Map<Integer, List<String>> doctorSpecialtiesCache = new HashMap<>();
        if (!allDoctorIds.isEmpty()) {
            List<DoctorSpecialty> allDoctorSpecialties = doctorSpecialtyRepo.findByDoctorIdInAndIsActiveTrue(new ArrayList<>(allDoctorIds));
            for (DoctorSpecialty ds : allDoctorSpecialties) {
                Integer doctorId = ds.getDoctor().getId();
                doctorSpecialtiesCache.putIfAbsent(doctorId, new ArrayList<>());
                doctorSpecialtiesCache.get(doctorId).add(ds.getSpecialtyName().trim());
            }
            for (Integer doctorId : allDoctorIds) {
                if (!doctorSpecialtiesCache.containsKey(doctorId)) {
                    User doctor = doctorsCache.get(doctorId);
                    if (doctor != null) {
                        String oldSpecialty = (doctor.getSpecialty() != null && !doctor.getSpecialty().trim().isEmpty())
                                ? doctor.getSpecialty().trim()
                                : "No Specialty";
                        doctorSpecialtiesCache.put(doctorId, Collections.singletonList(oldSpecialty));
                    }
                }
            }
        }

        // cache role của từng doctor (để check phải là DOCTOR)
        Map<Integer, List<UserRole>> userRolesCache = new HashMap<>();
        if (!allDoctorIds.isEmpty()) {
            List<UserRole> allUserRoles = userRoleRepo.findActiveByUserIdIn(new ArrayList<>(allDoctorIds));
            for (UserRole ur : allUserRoles) {
                Integer userId = ur.getUser().getId();
                userRolesCache.putIfAbsent(userId, new ArrayList<>());
                userRolesCache.get(userId).add(ur);
            }
        }

        // cache lịch đã tồn tại trong DB tuần này cho từng doctor + ngày (key=doctorId_ngày)
        Map<String, List<DoctorSchedule>> existingSchedulesCache = new HashMap<>();
        if (!allDoctorIds.isEmpty()) {
            List<DoctorSchedule> allExistingSchedules = doctorScheduleRepo.findByWeekRange(weekStart, weekEnd);
            for (DoctorSchedule schedule : allExistingSchedules) {
                if (allDoctorIds.contains(schedule.getDoctor().getId())) {
                    String key = schedule.getDoctor().getId() + "_" + schedule.getWorkDate();
                    existingSchedulesCache.putIfAbsent(key, new ArrayList<>());
                    existingSchedulesCache.get(key).add(schedule);
                }
            }
        }

        // duyệt theo từng ngày trong tuần
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);

            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = request.getDailyAssignments().get(dayName);

            if (dayAssignments == null || dayAssignments.isEmpty()) {
                continue;
            }

            Map<Integer, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> doctorAssignments = new HashMap<>();
            Set<String> validatedDoctors = new HashSet<>();

            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                doctorAssignments.putIfAbsent(assignment.getDoctorId(), new ArrayList<>());
                doctorAssignments.get(assignment.getDoctorId()).add(assignment);
            }

            // kiểm tra trùng giờ của từng bác sĩ trong cùng ngày, kể cả với lịch đã tồn tại trong DB
            for (Map.Entry<Integer, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> entry : doctorAssignments.entrySet()) {
                Integer doctorId = entry.getKey();
                List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = entry.getValue();

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
                                    + " and " + assignment2.getStartTime() + "-" + assignment2.getEndTime()
                                    + ")");
                        }
                    }
                }
                String scheduleKey = doctorId + "_" + workDate;
                List<DoctorSchedule> existingSchedules = existingSchedulesCache.getOrDefault(scheduleKey, new ArrayList<>());
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest newAssignment : assignments) {
                    for (DoctorSchedule existing : existingSchedules) {
                        boolean overlaps = newAssignment.getStartTime().isBefore(existing.getEndTime())
                                && existing.getStartTime().isBefore(newAssignment.getEndTime());
                        if (overlaps) {
                            String errorMsg = "Doctor ID " + doctorId + " already has an overlapping schedule on "
                                    + workDate + " (" + existing.getStartTime() + "-" + existing.getEndTime() + ")";
                            result.addError(errorMsg);
                            break;
                        }
                    }
                }
            }

            // check có ca nào nằm vào ngày nghỉ lễ ở clinic không
            Set<Integer> holidayClinicIds = new HashSet<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                Integer clinicId = assignment.getClinicId();
                if (holidayService.isHoliday(workDate, clinicId)) {
                    holidayClinicIds.add(clinicId);
                }
            }
            if (!holidayClinicIds.isEmpty()) {
                for (Integer holidayClinicId : holidayClinicIds) {
                    Clinic clinic = clinicsCache.get(holidayClinicId);
                    String clinicName = clinic != null ? clinic.getClinicName() : "Clinic ID " + holidayClinicId;
                    String holidayName = holidayService.getHolidayName(workDate, holidayClinicId);
                    String errorMsg = String.format(
                            "Cannot create schedule on %s - %s is a holiday (%s) at %s. Clinic is inactive during holidays.",
                            workDate, workDate, holidayName != null ? holidayName : "Holiday", clinicName);
                    result.addError(errorMsg);
                }
            }

            // lấy danh sách các clinic active trong ngày đó
            List<Clinic> allClinics = clinicRepo.findAll();
            Set<Integer> activeClinicIds = new HashSet<>();
            for (Clinic clinic : allClinics) {
                if (!holidayService.isHoliday(workDate, clinic.getId())) {
                    activeClinicIds.add(clinic.getId());
                }
            }

            // nếu toàn bộ clinic đều nghỉ thì skip (trên đã báo lỗi nếu vẫn có ca)
            if (activeClinicIds.isEmpty()) {
                continue;
            }

            Set<Integer> clinicIdsInDay = new HashSet<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                clinicIdsInDay.add(assignment.getClinicId());
            }

            // lọc ra các clinic active đã được assign ca
            Set<Integer> activeClinicIdsInDay = new HashSet<>();
            for (Integer clinicId : clinicIdsInDay) {
                if (!holidayService.isHoliday(workDate, clinicId)) {
                    activeClinicIdsInDay.add(clinicId);
                }
            }

            // phải có ít nhất 1 clinic active assigned, và tất cả những clinic assign phải là active
            if (activeClinicIdsInDay.isEmpty()) {
                result.addError("No active clinics are assigned on " + dayName 
                        + ". At least 1 active clinic must be assigned (clinics on holiday cannot be assigned).");
                continue;
            }

            // nếu cả 2 clinic active mà chỉ assign 1 thì báo lỗi
            if (activeClinicIds.size() == 2 && activeClinicIdsInDay.size() == 1) {
                Integer assignedClinicId = activeClinicIdsInDay.iterator().next();
                Clinic assignedClinic = clinicsCache.get(assignedClinicId);
                String assignedClinicName = assignedClinic != null ? assignedClinic.getClinicName() : "Clinic ID " + assignedClinicId;
                Integer missingClinicId = activeClinicIds.stream().filter(id -> !id.equals(assignedClinicId)).findFirst().orElse(null);   
                if (missingClinicId != null) {
                    Clinic missingClinic = clinicsCache.get(missingClinicId);
                    String missingClinicName = missingClinic != null ? missingClinic.getClinicName() : "Clinic ID " + missingClinicId;
                    result.addError("Only 1 clinic (" + assignedClinicName + ") is assigned on " + dayName
                            + ". Both active clinics (" + assignedClinicName + " and " + missingClinicName + ") must be assigned.");
                }
            } else if (activeClinicIdsInDay.size() > 2) {
                result.addError("Too many clinics (" + activeClinicIdsInDay.size() + ") are assigned on " + dayName
                        + ". Maximum 2 clinics can be assigned.");
            }

            // kiểm tra từng ca sáng/chiều tại mỗi clinic tối thiểu 2 bác sĩ
            Map<Integer, Map<String, Set<Integer>>> clinicShiftDoctors = new HashMap<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                String doctorDayKey = assignment.getDoctorId() + "_" + dayName;
                if (!validateAssignment(assignment, result, dayName, doctorsCache, clinicsCache, roomsCache,
                        doctorSpecialtiesCache, userRolesCache, validatedDoctors, doctorDayKey)) {
                    continue;
                }

                Integer clinicId = assignment.getClinicId();
                clinicShiftDoctors.putIfAbsent(clinicId, new HashMap<>());

                LocalTime startTime = assignment.getStartTime();
                String shift = null;
                if (startTime != null) {
                    if (startTime.getHour() >= 8 && startTime.getHour() < 12) {
                        shift = "morning";
                    } else if (startTime.getHour() >= 13 && startTime.getHour() < 19) {
                        shift = "afternoon";
                    }
                }

                if (shift != null) {
                    clinicShiftDoctors.get(clinicId).putIfAbsent(shift, new HashSet<>());
                    clinicShiftDoctors.get(clinicId).get(shift).add(assignment.getDoctorId());
                }
            }

            // kiểm tra lại số lượng bác sĩ cho từng ca
            for (Integer clinicId : clinicIdsInDay) {
                Map<String, Set<Integer>> shiftDoctors = clinicShiftDoctors.getOrDefault(clinicId, new HashMap<>());
                Clinic clinic = clinicsCache.get(clinicId);
                String clinicName = clinic != null ? clinic.getClinicName() : "Clinic ID " + clinicId;

                Set<Integer> morningDoctors = shiftDoctors.getOrDefault("morning", new HashSet<>());
                if (morningDoctors.size() < 2) {
                    result.addError(
                            clinicName + " must have at least 2 doctors assigned in the morning shift (08:00-11:00) on "
                                    + dayName + ". Found " + morningDoctors.size() + " doctor(s)");
                }

                Set<Integer> afternoonDoctors = shiftDoctors.getOrDefault("afternoon", new HashSet<>());
                if (afternoonDoctors.size() < 2) {
                    result.addError(clinicName
                            + " must have at least 2 doctors assigned in the afternoon shift (13:00-18:00) on "
                            + dayName + ". Found " + afternoonDoctors.size() + " doctor(s)");
                }
            }

            // kiểm tra mỗi specialty phải có ở cả 2 clinic trong ngày
            Set<String> allSpecialties = new HashSet<>();
            Map<String, Map<Integer, Set<Integer>>> specialtyClinicDoctors = new HashMap<>();

            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                String doctorDayKey = assignment.getDoctorId() + "_" + dayName;
                if (!validateAssignment(assignment, result, dayName, doctorsCache, clinicsCache, roomsCache,
                        doctorSpecialtiesCache, userRolesCache, validatedDoctors, doctorDayKey)) {
                    continue;
                }

                User doctor = doctorsCache.get(assignment.getDoctorId());
                if (doctor == null)
                    continue;

                List<String> doctorSpecialties = doctorSpecialtiesCache.getOrDefault(assignment.getDoctorId(), new ArrayList<>());
                allSpecialties.addAll(doctorSpecialties);

                Integer clinicId = assignment.getClinicId();
                for (String specialty : doctorSpecialties) {
                    specialtyClinicDoctors.putIfAbsent(specialty, new HashMap<>());
                    specialtyClinicDoctors.get(specialty).putIfAbsent(clinicId, new HashSet<>());
                    specialtyClinicDoctors.get(specialty).get(clinicId).add(assignment.getDoctorId());
                }
            }

            for (String specialty : allSpecialties) {
                Map<Integer, Set<Integer>> clinicDoctors = specialtyClinicDoctors.get(specialty);
                if (clinicDoctors == null || clinicDoctors.size() != 2) {
                    result.addError("Specialty \"" + specialty + "\" must have doctors assigned to both clinics on "
                            + dayName + ". Found in " + (clinicDoctors != null ? clinicDoctors.size() : 0)
                            + " clinic(s)");
                } else {
                    for (Integer clinicId : clinicIdsInDay) {
                        if (!clinicDoctors.containsKey(clinicId) || clinicDoctors.get(clinicId).isEmpty()) {
                            Clinic clinic = clinicsCache.get(clinicId);
                            String clinicName = clinic != null ? clinic.getClinicName() : "Clinic ID " + clinicId;
                            result.addError("Specialty \"" + specialty + "\" must have at least one doctor assigned to "
                                    + clinicName + " on " + dayName);
                        }
                    }
                }
            }
        }

        return result;
    }

    // validate từng assignment giao ca cho bác sĩ
    private boolean validateAssignment(
            CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment,
            ValidationResultDto result,
            String dayName,
            Map<Integer, User> doctorsCache,
            Map<Integer, Clinic> clinicsCache,
            Map<Integer, Room> roomsCache,
            Map<Integer, List<String>> doctorSpecialtiesCache,
            Map<Integer, List<UserRole>> userRolesCache,
            Set<String> validatedDoctors,
            String doctorDayKey) {

        boolean alreadyValidated = validatedDoctors.contains(doctorDayKey);

        User doctor = doctorsCache.get(assignment.getDoctorId());
        if (doctor == null) {
            if (!alreadyValidated) {
                result.addError("Doctor ID " + assignment.getDoctorId() + " not found on " + dayName);
                validatedDoctors.add(doctorDayKey);
            }
            return false;
        }

        if (!Boolean.TRUE.equals(doctor.getIsActive())) {
            if (!alreadyValidated) {
                result.addError("Doctor ID " + assignment.getDoctorId() + " (" + doctor.getFullName()
                        + ") is inactive and cannot be assigned to schedule on " + dayName);
                validatedDoctors.add(doctorDayKey);
            }
            return false;
        }

        // check phải là doctor
        List<UserRole> userRoles = userRolesCache.getOrDefault(doctor.getId(), new ArrayList<>());
        boolean isDoctor = userRoles.stream()
                .anyMatch(ur -> {
                    if (ur == null || ur.getRole() == null)
                        return false;
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null &&
                            (roleName.equalsIgnoreCase("DOCTOR") ||
                                    roleName.equalsIgnoreCase("BÁC SĨ"));
                });

        if (!isDoctor) {
            if (!alreadyValidated) {
                result.addError("User ID " + assignment.getDoctorId() + " (" + doctor.getFullName() +
                        ") is not a DOCTOR and cannot be assigned to schedule on " + dayName);
                validatedDoctors.add(doctorDayKey);
            }
            return false;
        }

        // check phải có department
        Department dept = doctor.getDepartment();
        if (dept == null) {
            if (!alreadyValidated) {
                result.addError("Doctor ID " + assignment.getDoctorId() + " (" + doctor.getFullName() +
                        ") does not have a department assigned on " + dayName);
                validatedDoctors.add(doctorDayKey);
            }
            return false;
        }

        validatedDoctors.add(doctorDayKey);

        // check clinic có tồn tại
        Clinic clinic = clinicsCache.get(assignment.getClinicId());
        if (clinic == null) {
            result.addError("Clinic ID " + assignment.getClinicId() + " not found on " + dayName);
            return false;
        }

        // check room (nếu có)
        if (assignment.getRoomId() != null && assignment.getRoomId() > 0) {
            Room room = roomsCache.get(assignment.getRoomId());
            if (room == null) {
                result.addError("Room ID " + assignment.getRoomId() + " not found on " + dayName);
                return false;
            }

            if (room.getClinic() == null || !room.getClinic().getId().equals(assignment.getClinicId())) {
                result.addError("Room ID " + assignment.getRoomId() + " (" + room.getRoomName() +
                        ") does not belong to Clinic ID " + assignment.getClinicId() +
                        " (" + clinic.getClinicName() + ") on " + dayName);
                return false;
            }
        }

        return true;
    }
}
