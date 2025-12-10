package sunshine_dental_care.services.impl.hr;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.DepartmentResponse;
import sunshine_dental_care.dto.hrDTO.RoleResponse;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.DataLoadException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.services.interfaces.hr.HRManagementService;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HRManagementServiceImpl implements HRManagementService {
    
    private final DepartmentRepo departmentRepo;
    private final ClinicRepo clinicRepo;
    private final RoleRepo roleRepo;
    private final RoomRepo roomRepo;
    private final SystemConfigService systemConfigService;
    private final sunshine_dental_care.repositories.system.HolidayRepo holidayRepo;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        log.info("Get all departments (ordered by name)");
        try {
            List<Department> departments = departmentRepo.findAllByOrderByDepartmentNameAsc();
            if (departments == null || departments.isEmpty()) {
                log.warn("No departments found");
                return List.of();
            }
            return departments.stream()
                .map(d -> new DepartmentResponse(d.getId(), d.getDepartmentName()))
                .collect(Collectors.toList());
        } catch (DataAccessException ex) {
            log.error("Error while fetching departments: {}", ex.getMessage(), ex);
            throw new DataLoadException("departments", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching departments: {}", ex.getMessage(), ex);
            throw new DataLoadException("Failed to fetch departments: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicResponse> getAllClinics() {
        log.info("Get all active clinics");
        try {
            // Tự động restore trạng thái phòng khám nếu holiday đã hết
            systemConfigService.restoreClinicsAfterHolidayEnded();
            List<Clinic> clinics = clinicRepo.findAll();
            if (clinics == null || clinics.isEmpty()) {
                log.warn("No clinics found");
                return List.of();
            }
            // chỉ lấy clinic đang active
            return clinics.stream()
                .filter(c -> c.getIsActive() != null && c.getIsActive())
                .map(c -> new ClinicResponse(c.getId(), c.getClinicName()))
                .collect(Collectors.toList());
        } catch (DataAccessException ex) {
            log.error("Error while fetching clinics: {}", ex.getMessage(), ex);
            throw new DataLoadException("clinics", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching clinics: {}", ex.getMessage(), ex);
            throw new DataLoadException("Failed to fetch clinics: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.info("Get all roles (excluding ADMIN and USER)");
        try {
            List<Role> roles = roleRepo.findAll();
            if (roles == null || roles.isEmpty()) {
                log.warn("No roles found");
                return List.of();
            }
            // loại trừ ADMIN và USER
            return roles.stream()
                .filter(r -> {
                    if (r.getRoleName() == null) {
                        return false;
                    }
                    String roleName = r.getRoleName();
                    return !roleName.equalsIgnoreCase("ADMIN") && !roleName.equalsIgnoreCase("USER");
                })
                .map(r -> new RoleResponse(r.getId(), r.getRoleName(), r.getDescription()))
                .collect(Collectors.toList());
        } catch (DataAccessException ex) {
            log.error("Error while fetching roles: {}", ex.getMessage(), ex);
            throw new DataLoadException("roles", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching roles: {}", ex.getMessage(), ex);
            throw new DataLoadException("Failed to fetch roles: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        log.info("Get all active rooms, include related clinic info");
        try {
            List<Room> rooms = roomRepo.findByIsActiveTrueOrderByRoomNameAsc();
            if (rooms == null || rooms.isEmpty()) {
                log.warn("No active rooms found");
                return List.of();
            }
            // trả về phòng và thông tin phòng khám liên kết
            return rooms.stream()
                .map(r -> {
                    Integer clinicId = null;
                    String clinicName = null;
                    try {
                        if (r.getClinic() != null) {
                            Clinic clinic = r.getClinic();
                            clinicName = clinic.getClinicName();
                            clinicId = clinic.getId();
                        }
                    } catch (Exception ex) {
                        log.warn("Lỗi khi lấy clinic của phòng {}: {}", r.getId(), ex.getMessage());
                    }
                    return new RoomResponse(r.getId(), r.getRoomName(), clinicId, clinicName);
                })
                .collect(Collectors.toList());
        } catch (DataAccessException ex) {
            log.error("Error while fetching rooms: {}", ex.getMessage(), ex);
            throw new DataLoadException("rooms", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching rooms: {}", ex.getMessage(), ex);
            throw new DataLoadException("Failed to fetch rooms: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<sunshine_dental_care.entities.Holiday> getAllHolidays() {
        log.info("Get all holidays for HR");
        try {
            // HR chỉ được xem holiday, không cho sửa/xóa ở đây
            return holidayRepo.findAll();
        } catch (DataAccessException ex) {
            log.error("Error while fetching holidays: {}", ex.getMessage(), ex);
            throw new DataLoadException("holidays", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching holidays: {}", ex.getMessage(), ex);
            throw new DataLoadException("Failed to fetch holidays: " + ex.getMessage(), ex);
        }
    }
}
