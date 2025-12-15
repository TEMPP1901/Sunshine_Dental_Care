package sunshine_dental_care.dto.receptionDTO.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sunshine_dental_care.dto.doctorDTO.RoomDTO;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.PatientResponse;
import sunshine_dental_care.dto.receptionDTO.ServiceItemResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.Patient;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AppointmentMapper {

    private final DoctorScheduleMapper doctorScheduleMapper;

    /**
     * Map Appointment Entity sang AppointmentResponse DTO
     */
    public AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
        if (appointment == null) return null;

        AppointmentResponse response = new AppointmentResponse();

        response.setId(appointment.getId());

        // 1. Map Patient (Object cũ & Trường phẳng mới)
        if (appointment.getPatient() != null) {
            // Map object đầy đủ (như cũ)
            response.setPatient(mapPatientToPatientResponse(appointment.getPatient()));

            // MAP SANG TRƯỜNG PHẲNG (Để hiện lên Table)
            response.setPatientName(appointment.getPatient().getFullName());
            response.setPatientCode(appointment.getPatient().getPatientCode());
            response.setPatientPhone(appointment.getPatient().getPhone());
        }

        // Map Doctor (Tái sử dụng DoctorScheduleMapper)
        if (appointment.getDoctor() != null) {
            response.setDoctor(doctorScheduleMapper.mapUserToHrDocDto(appointment.getDoctor()));

            // MAP SANG TRƯỜNG PHẲNG
            response.setDoctorName(appointment.getDoctor().getFullName());
        }

        // Map Clinic (Tái sử dụng logic mapping Clinic)
        if (appointment.getClinic() != null) {
            response.setClinic(doctorScheduleMapper.mapClinicToClinicResponse(appointment.getClinic()));
            response.setClinicName(appointment.getClinic().getClinicName());
        }
        response.setStartDateTime(appointment.getStartDateTime());
        response.setEndDateTime(appointment.getEndDateTime());
        response.setStatus(appointment.getStatus());
        response.setPaymentStatus(appointment.getPaymentStatus());
        response.setChannel(appointment.getChannel());
        response.setNote(appointment.getNote());

        if (appointment.getCreatedBy() != null) {
            response.setCreatedByUserName(appointment.getCreatedBy().getFullName());
        }
        response.setCreatedAt(appointment.getCreatedAt());
        response.setUpdatedAt(appointment.getUpdatedAt());
        response.setAppointmentType(appointment.getAppointmentType());
        response.setBookingFee(appointment.getBookingFee());

        // Map Service Items
        // Entity Appointment cần có List<AppointmentService>
        if (appointment.getAppointmentServices() != null) {
            List<ServiceItemResponse> services = appointment.getAppointmentServices().stream()
                    .map(this::mapAppointmentServiceToServiceItemResponse)
                    .collect(Collectors.toList());
            response.setServices(services);
        }

        // Map Room
        if (appointment.getRoom() != null) {
            RoomDTO roomDto = new RoomDTO();
            roomDto.setId(appointment.getRoom().getId());
            roomDto.setRoomName(appointment.getRoom().getRoomName());
            roomDto.setIsPrivate(appointment.getRoom().getIsPrivate());

            response.setRoom(roomDto);
        }

        return response;
    }

    // Helper method để map chi tiết dịch vụ
    private ServiceItemResponse mapAppointmentServiceToServiceItemResponse(AppointmentService as) {
        ServiceItemResponse dto = new ServiceItemResponse();
        // Ánh xạ các trường từ AppointmentService
        dto.setId(as.getId());
        dto.setServiceId(as.getService().getId());
        String displayName = as.getService().getServiceName();

        // Nếu có variant (gói cụ thể), lấy tên variant
        if (as.getServiceVariant() != null) {
            displayName = as.getServiceVariant().getVariantName();
        }
        dto.setServiceName(displayName);
        dto.setQuantity(as.getQuantity());
        dto.setUnitPrice(as.getUnitPrice());
        dto.setDiscountPct(as.getDiscountPct());
        dto.setNote(as.getNote());
        return dto;
    }

    // Helper để map Patient Entity sang DTO
    public PatientResponse mapPatientToPatientResponse(Patient patient) {
        if (patient == null) return null;
        PatientResponse dto = new PatientResponse();
        dto.setId(patient.getId());
        dto.setPatientCode(patient.getPatientCode());
        dto.setFullName(patient.getFullName());
        dto.setPhone(patient.getPhone());
        dto.setEmail(patient.getEmail());

        // Danh sách bệnh nhân (Patient List)
        dto.setGender(patient.getGender());
        dto.setDateOfBirth(patient.getDateOfBirth());
        dto.setAddress(patient.getAddress());
        dto.setIsActive(patient.getIsActive());
        return dto;
    }
}