package sunshine_dental_care.dto.receptionDTO.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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

        // Map Patient
        response.setPatient(mapPatientToPatientResponse(appointment.getPatient()));

        // Map Doctor (Tái sử dụng DoctorScheduleMapper)
        response.setDoctor(doctorScheduleMapper.mapUserToHrDocDto(appointment.getDoctor()));

        // Map Clinic (Tái sử dụng logic mapping Clinic)
        response.setClinic(doctorScheduleMapper.mapClinicToClinicResponse(appointment.getClinic()));

        response.setStartDateTime(appointment.getStartDateTime());
        response.setEndDateTime(appointment.getEndDateTime());
        response.setStatus(appointment.getStatus());
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

        return response;
    }

    // Helper method để map chi tiết dịch vụ
    private ServiceItemResponse mapAppointmentServiceToServiceItemResponse(AppointmentService as) {
        ServiceItemResponse dto = new ServiceItemResponse();
        // Ánh xạ các trường từ AppointmentService
        dto.setId(as.getId());
        dto.setServiceId(as.getService().getId());
        dto.setServiceName(as.getService().getServiceName());
        dto.setQuantity(as.getQuantity());
        dto.setUnitPrice(as.getUnitPrice());
        dto.setDiscountPct(as.getDiscountPct());
        dto.setNote(as.getNote());
        return dto;
    }

    // Helper để map Patient Entity sang DTO
    private PatientResponse mapPatientToPatientResponse(Patient patient) {
        if (patient == null) return null;
        PatientResponse dto = new PatientResponse();
        dto.setId(patient.getId());
        dto.setPatientCode(patient.getPatientCode());
        dto.setFullName(patient.getFullName());
        dto.setPhone(patient.getPhone());
        dto.setEmail(patient.getEmail());
        return dto;
    }
}