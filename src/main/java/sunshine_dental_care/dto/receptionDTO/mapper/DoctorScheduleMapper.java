package sunshine_dental_care.dto.receptionDTO.mapper;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.DepartmentResponse;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.HrDocDto;
import sunshine_dental_care.dto.hrDTO.RoleResponse;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;

@Component
@RequiredArgsConstructor
public class DoctorScheduleMapper {

    /**
     * Map User Entity (Doctor) sang HrDocDto, bao gồm cả Specialty, Department và Roles.
     */
    public HrDocDto mapUserToHrDocDto(User doctor) {
        if (doctor == null) return null;

        HrDocDto dto = new HrDocDto();
        dto.setId(doctor.getId());
        dto.setCode(doctor.getCode());
        dto.setFullName(doctor.getFullName());
        dto.setAvatarUrl(doctor.getAvatarUrl());
        // Lấy các trường đã thêm vào DTO
        dto.setEmail(doctor.getEmail());
        dto.setPhone(doctor.getPhone());

        // --- Ánh xạ các trường mới (Specialty, Department, Roles) ---
        dto.setSpecialty(doctor.getSpecialty());

        // Map Department
        if (doctor.getDepartment() != null) {
            dto.setDepartment(new DepartmentResponse(
                    doctor.getDepartment().getId(),
                    doctor.getDepartment().getDepartmentName()
            ));
        }

        // Map Roles (Sử dụng UserRole Entity để lấy Role)
        if (doctor.getUserRoles() != null) {
            List<RoleResponse> roles = doctor.getUserRoles().stream()
                    .map(userRole -> new RoleResponse(
                            userRole.getRole().getId(),
                            userRole.getRole().getRoleName()
                    ))
                    .collect(Collectors.toList());
            dto.setUserRoles(roles);
        }

        return dto;
    }

    /**
     * Phương thức mới: Ánh xạ Clinic Entity sang ClinicResponse DTO
     */
    public ClinicResponse mapClinicToClinicResponse(Clinic clinic) {
        if (clinic == null) return null;

        return new ClinicResponse(
                clinic.getId(),
                clinic.getClinicCode(),
                clinic.getClinicName()
        );
    }

    /**
     * Map DoctorSchedule Entity sang DoctorScheduleDto (là phương thức this::mapToScheduleDto đã đề cập)
     */
    public DoctorScheduleDto mapToScheduleDto(DoctorSchedule schedule) {
        DoctorScheduleDto dto = new DoctorScheduleDto();

        dto.setId(schedule.getId());

        // 1. Map Doctor (sử dụng phương thức mapUserToHrDocDto)
        dto.setDoctor(mapUserToHrDocDto(schedule.getDoctor()));

        // 2. Map Clinic
        if (schedule.getClinic() != null) {
            dto.setClinic(new ClinicResponse(
                    schedule.getClinic().getId(),
                    schedule.getClinic().getClinicCode(),
                    schedule.getClinic().getClinicName()
            ));
        }

        // 3. Map Room (Sử dụng constructor 2 tham số: id, roomName)
        if (schedule.getRoom() != null) {
            dto.setRoom(new RoomResponse(
                    schedule.getRoom().getId(),
                    schedule.getRoom().getRoomName()
            ));
        }

        // 4. Map Thời gian và Trạng thái
        dto.setWorkDate(schedule.getWorkDate());
        dto.setStartTime(schedule.getStartTime());
        dto.setEndTime(schedule.getEndTime());
        dto.setStatus(schedule.getStatus());
        dto.setNote(schedule.getNote());

        // 5. Map Timestamps (sử dụng Instant + ZoneOffset.UTC)
        if (schedule.getCreatedAt() != null) {
            dto.setCreatedAt(schedule.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
        }
        if (schedule.getUpdatedAt() != null) {
            dto.setUpdatedAt(schedule.getUpdatedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
        }

        return dto;
    }
}
