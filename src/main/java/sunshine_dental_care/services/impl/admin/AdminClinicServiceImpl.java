package sunshine_dental_care.services.impl.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.dto.adminDTO.ClinicStaffDetailDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.ClinicNotFoundException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.interfaces.admin.AdminClinicService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminClinicServiceImpl implements AdminClinicService {

    private final ClinicRepo clinicRepo;
    private final UserRepo userRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;

    // Lấy tất cả phòng khám (Clinic)
    @Override
    @Transactional(readOnly = true)
    public List<AdminClinicDto> getAllClinics() {
        return clinicRepo.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // Cập nhật trạng thái kích hoạt phòng khám
    @Override
    @Transactional
    public void updateActivation(Integer clinicId, boolean active) {
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new ClinicNotFoundException(clinicId));
        clinic.setIsActive(active);
        clinicRepo.save(clinic);
    }

    // Chuyển Clinic sang DTO
    private AdminClinicDto convertToDto(Clinic clinic) {
        AdminClinicDto dto = new AdminClinicDto();
        dto.setId(clinic.getId());
        dto.setClinicCode(clinic.getClinicCode());
        dto.setClinicName(clinic.getClinicName());
        dto.setAddress(clinic.getAddress());
        dto.setPhone(clinic.getPhone());
        dto.setEmail(clinic.getEmail());
        dto.setOpeningHours(clinic.getOpeningHours());
        dto.setActive(Boolean.TRUE.equals(clinic.getIsActive()));
        dto.setCreatedAt(clinic.getCreatedAt());
        dto.setUpdatedAt(clinic.getUpdatedAt());
        
        // Tính số bác sĩ và nhân viên đang hoạt động
        Integer clinicId = clinic.getId();
        int doctorsCount = countActiveDoctorsByClinic(clinicId);
        int employeesCount = countActiveEmployeesByClinic(clinicId);
        
        dto.setActiveDoctorsCount(doctorsCount);
        dto.setActiveEmployeesCount(employeesCount);
        
        return dto;
    }

    // Đếm số bác sĩ đang hoạt động tại clinic
    private int countActiveDoctorsByClinic(Integer clinicId) {
        try {
            List<User> doctors = userRepo.findDoctorsByClinicId(clinicId);
            return doctors != null ? doctors.size() : 0;
        } catch (Exception e) {
            log.warn("Error counting doctors for clinic {}: {}", clinicId, e.getMessage());
            return 0;
        }
    }

    // Đếm số nhân viên đang hoạt động tại clinic (bao gồm cả bác sĩ)
    private int countActiveEmployeesByClinic(Integer clinicId) {
        try {
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByClinicId(clinicId);
            if (assignments == null || assignments.isEmpty()) {
                return 0;
            }
            
            // Lọc các user đang active và assignment không có endDate hoặc endDate trong tương lai
            long count = assignments.stream()
                    .filter(assignment -> {
                        User user = assignment.getUser();
                        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                            return false;
                        }
                        // Kiểm tra endDate nếu có
                        if (assignment.getEndDate() != null) {
                            return assignment.getEndDate().isAfter(java.time.LocalDate.now()) 
                                || assignment.getEndDate().equals(java.time.LocalDate.now());
                        }
                        return true;
                    })
                    .map(assignment -> assignment.getUser().getId())
                    .distinct()
                    .count();
            
            return (int) count;
        } catch (Exception e) {
            log.warn("Error counting employees for clinic {}: {}", clinicId, e.getMessage());
            return 0;
        }
    }

    // Lấy danh sách chi tiết nhân sự theo clinic và ngày
    @Override
    @Transactional(readOnly = true)
    public List<ClinicStaffDetailDto> getClinicStaffDetails(Integer clinicId, LocalDate date) {
        // Kiểm tra clinic tồn tại
        clinicRepo.findById(clinicId)
                .orElseThrow(() -> new ClinicNotFoundException(clinicId));

        // Lọc theo ngày nếu có
        LocalDate checkDate = date != null ? date : LocalDate.now();

        List<ClinicStaffDetailDto> result = new java.util.ArrayList<>();

        // 1. Lấy bác sĩ từ schedule trong ngày
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByClinicAndDate(clinicId, checkDate);
        Set<Integer> doctorIdsFromSchedule = schedules.stream()
                .map(schedule -> schedule.getDoctor().getId())
                .collect(Collectors.toSet());

        for (DoctorSchedule schedule : schedules) {
            User doctor = schedule.getDoctor();
            if (doctor == null || !Boolean.TRUE.equals(doctor.getIsActive())) {
                continue;
            }

            ClinicStaffDetailDto dto = new ClinicStaffDetailDto();
            dto.setUserId(doctor.getId());
            dto.setFullName(doctor.getFullName());
            dto.setEmail(doctor.getEmail());
            dto.setPhone(doctor.getPhone());
            dto.setIsActive(doctor.getIsActive());
            dto.setIsDoctor(true);

            // Lấy role chính của user
            List<UserRole> userRoles = userRoleRepo.findActiveByUserId(doctor.getId());
            if (!userRoles.isEmpty()) {
                Role role = userRoles.get(0).getRole();
                if (role != null) {
                    dto.setRoleName(role.getRoleName());
                }
            }

            // Lấy roleAtClinic từ assignment nếu có
            UserClinicAssignment assignment = userClinicAssignmentRepo
                    .findByUserIdAndClinicId(doctor.getId(), clinicId)
                    .orElse(null);
            if (assignment != null) {
                dto.setRoleAtClinic(assignment.getRoleAtClinic());
                dto.setStartDate(assignment.getStartDate());
                dto.setEndDate(assignment.getEndDate());
            }

            result.add(dto);
        }

        // 2. Lấy nhân viên từ assignments (loại trừ bác sĩ đã có trong schedule)
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByClinicId(clinicId);
        for (UserClinicAssignment assignment : assignments) {
            User user = assignment.getUser();
            if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
                continue;
            }

            // Bỏ qua nếu đã có trong schedule (đã là bác sĩ)
            if (doctorIdsFromSchedule.contains(user.getId())) {
                continue;
            }

            // Kiểm tra ngày có nằm trong khoảng startDate và endDate không
            if (assignment.getStartDate() != null && assignment.getStartDate().isAfter(checkDate)) {
                continue;
            }
            if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(checkDate)) {
                continue;
            }

            ClinicStaffDetailDto dto = new ClinicStaffDetailDto();
            dto.setUserId(user.getId());
            dto.setFullName(user.getFullName());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setRoleAtClinic(assignment.getRoleAtClinic());
            dto.setStartDate(assignment.getStartDate());
            dto.setEndDate(assignment.getEndDate());
            dto.setIsActive(user.getIsActive());
            dto.setIsDoctor(false);

            // Lấy role chính của user
            List<UserRole> userRoles = userRoleRepo.findActiveByUserId(user.getId());
            if (!userRoles.isEmpty()) {
                Role role = userRoles.get(0).getRole();
                if (role != null) {
                    dto.setRoleName(role.getRoleName());
                }
            }

            result.add(dto);
        }

        return result;
    }
}
