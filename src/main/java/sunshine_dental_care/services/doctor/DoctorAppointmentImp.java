package sunshine_dental_care.services.doctor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.doctorDTO.ClinicDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorDTO;
import sunshine_dental_care.dto.doctorDTO.PatientDTO;
import sunshine_dental_care.dto.doctorDTO.RoomDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceVariantDTO;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.doctor.DoctorAppointmentRepo;
import sunshine_dental_care.repositories.doctor.DoctorRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.system.AuditLogService;

@Service
@Slf4j
public class DoctorAppointmentImp implements DoctorAppointmentService{


    private DoctorRepo _doctorRepo;
    private DoctorAppointmentRepo _doctorAppointmentRepo;
    private UserRepo _userRepo;
    private AuditLogService _auditLogService;
    private NotificationService _notificationService;

    public DoctorAppointmentImp(DoctorRepo doctorRepo,
                                DoctorAppointmentRepo doctorAppointmentRepo,
                                UserRepo userRepo,
                                AuditLogService auditLogService,
                                NotificationService notificationService) {
        _doctorRepo = doctorRepo;
        _doctorAppointmentRepo = doctorAppointmentRepo;
        _userRepo = userRepo;
        _auditLogService = auditLogService;
        _notificationService = notificationService;
    }

    // Hàm chuyển đổi từ entity Appointment sang DTO DoctorAppointmentDTO
    private DoctorAppointmentDTO mapToDTO(Appointment appointment) {
        List<String> detailNames = null;
        if (appointment.getAppointmentServices() != null) {
            detailNames = appointment.getAppointmentServices().stream()
                    .map(as -> {
                        if (as.getServiceVariant() != null) {
                            return as.getServiceVariant().getVariantName();
                        }
                        if (as.getNote() != null && as.getNote().contains("[") && as.getNote().contains("]")) {
                            try {
                                // Cắt chuỗi để lấy phần trong ngoặc vuông
                                int start = as.getNote().indexOf("[") + 1;
                                int end = as.getNote().indexOf("]");
                                return as.getNote().substring(start, end);
                            } catch (Exception e) {
                            }
                        }
                        // 3. FALLBACK: Nếu không có gì cả thì mới lấy tên Service Cha
                        return as.getService() != null ? as.getService().getServiceName() : "N/A";
                    })
                    .collect(Collectors.toList());
        }
        return DoctorAppointmentDTO.builder()
                .appointmentId(appointment.getId())
                .clinic(toClinicSummary(appointment.getClinic())) // Thông tin phòng khám
                .patient(toPatientSummary(appointment.getPatient())) // Thông tin bệnh nhân
                .doctor(toDoctorSummary(appointment.getDoctor())) // Thông tin bác sĩ
                .room(toRoomSummary(appointment.getRoom())) // Thông tin phòng khám chữa trị
                .service(toServiceDTO(appointment.getService())) // Service object - load trực tiếp từ appointment.service
                .serviceDetails(detailNames)
                .startDateTime(appointment.getStartDateTime()) // Thời gian bắt đầu
                .endDateTime(appointment.getEndDateTime()) // Thời gian kết thúc
                .status(appointment.getStatus()) // Trạng thái lịch hẹn
                .channel(appointment.getChannel()) // Kênh đặt lịch
                .note(appointment.getNote()) // Ghi chú
                // Người tạo lịch (nếu có)
                .createdById(appointment.getCreatedBy() != null ? appointment.getCreatedBy().getId() : null)
                .createdByName(appointment.getCreatedBy() != null ? appointment.getCreatedBy().getFullName() : null)
                .createdAt(appointment.getCreatedAt()) // Ngày tạo
                .updatedAt(appointment.getUpdatedAt()) // Ngày cập nhật
                .appointmentType(appointment.getAppointmentType()) // Loại lịch hẹn
                .bookingFee(appointment.getBookingFee()) // Phí đặt lịch hẹn
                .build();
    }

    // Chuyển entity Service sang ServiceDTO
    private ServiceDTO toServiceDTO(sunshine_dental_care.entities.Service service) {
        if (service == null) {
            return null;
        }
        
        // Map variants nếu có
        List<ServiceVariantDTO> variantDTOs = null;
        if (service.getVariants() != null && !service.getVariants().isEmpty()) {
            variantDTOs = service.getVariants().stream()
                    .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                    .map(v -> ServiceVariantDTO.builder()
                            .variantId(v.getId())
                            .variantName(v.getVariantName())
                            .duration(v.getDuration())
                            .price(v.getPrice())
                            .description(v.getDescription())
                            .currency(v.getCurrency())
                            .isActive(v.getIsActive())
                            .build())
                    .collect(Collectors.toList());
        }
        
        return ServiceDTO.builder()
                .id(service.getId())
                .serviceName(service.getServiceName())
                .category(service.getCategory())
                .description(service.getDescription())
                .defaultDuration(service.getDefaultDuration())
                .isActive(service.getIsActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .variants(variantDTOs)
                .build();
    }

    // Hàm chuyển đổi entity Clinic sang ClinicDTO rút gọn
    private ClinicDTO toClinicSummary(sunshine_dental_care.entities.Clinic clinic) {
        if (clinic == null) return null;
        return ClinicDTO.builder()
                .id(clinic.getId())
                .clinicCode(clinic.getClinicCode())
                .clinicName(clinic.getClinicName())
                .phone(clinic.getPhone())
                .address(clinic.getAddress())
                .build();
    }

    // Hàm chuyển đổi entity Patient sang PatientDTO rút gọn
    private PatientDTO toPatientSummary(sunshine_dental_care.entities.Patient patient) {
        if (patient == null) return null;
        return PatientDTO.builder()
                .id(patient.getId())
                .patientCode(patient.getPatientCode())
                .fullName(patient.getFullName())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .build();
    }

    // Hàm chuyển đổi entity User (bác sĩ) sang DoctorDTO
    private DoctorDTO toDoctorSummary(sunshine_dental_care.entities.User doctor) {
        if (doctor == null) return null;
        return DoctorDTO.builder()
                .id(doctor.getId())
                .code(doctor.getCode())
                .fullName(doctor.getFullName())
                .email(doctor.getEmail())
                .phone(doctor.getPhone())
                .build();
    }

    // Hàm chuyển đổi entity Room sang RoomDTO rút gọn
    private RoomDTO toRoomSummary(sunshine_dental_care.entities.Room room) {
        if (room == null) return null;
        return RoomDTO.builder()
                .id(room.getId())
                .roomName(room.getRoomName())
                .isPrivate(room.getIsPrivate())
                .build();
    }

    @Override
    // Lấy tất cả lịch hẹn theo id của bác sĩ
    public List<DoctorAppointmentDTO> findByDoctorId(Integer id) {
        var doctor = _doctorRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found")); // Kiểm tra bác sĩ tồn tại
        List<Appointment> appointments = _doctorAppointmentRepo.findByDoctorId(doctor.getId());
        // Chuyển từng entity Appointment sang DTO
        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    // Lấy danh sách lịch hẹn của bác sĩ theo trạng thái
    public List<DoctorAppointmentDTO> findByDoctorIdAndStatus(Integer id, String status) {
        var doctor = _doctorRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        List<Appointment> appointments = _doctorAppointmentRepo.findByDoctorIdAndStatus(doctor.getId(), status);
        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    // Lấy chi tiết lịch hẹn theo id lịch hẹn và id bác sĩ
    public DoctorAppointmentDTO findByIdAndDoctorId(Integer appointmentId, Integer doctorId) {
        var doctor = _doctorRepo.findById(doctorId)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        Appointment appointment = _doctorAppointmentRepo.findByIdAndDoctorId(appointmentId, doctor.getId());
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment not found");
        }
        return mapToDTO(appointment); // Trả về thông tin lịch hẹn dạng DTO
    }

    @Override
    // Thay đổi trạng thái của lịch hẹn (VD: đã hoàn thành, đã hủy,...)
    public void changeStatusAppointment(Integer appointmentId, String status) {
        Appointment appt = _doctorAppointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        String oldStatus = appt.getStatus();
        _doctorAppointmentRepo.updateStatus(appointmentId, status); // Cập nhật trạng thái lịch hẹn

        // Lấy lại appointment sau khi update để có thông tin mới nhất
        Appointment updatedAppt = _doctorAppointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found after update"));

        // Audit log: bác sĩ đổi trạng thái lịch hẹn
        User actor = resolveCurrentUser();
        if (actor != null) {
            _auditLogService.logAction(actor, "DOCTOR_UPDATE_STATUS", "APPOINTMENT", appointmentId, null,
                    "Status -> " + status);
        }

        // Gửi thông báo cho patient khi bác sĩ cập nhật trạng thái
        if (!status.equals(oldStatus)) {
            sendAppointmentStatusUpdateNotification(updatedAppt, status, oldStatus);
        }
    }

    /**
     * Gửi thông báo cho patient khi bác sĩ cập nhật trạng thái appointment
     */
    private void sendAppointmentStatusUpdateNotification(Appointment appointment, String newStatus, String oldStatus) {
        try {
            if (appointment.getPatient() == null || appointment.getPatient().getUser() == null) {
                log.warn("Cannot send notification: appointment {} has no patient user", appointment.getId());
                return;
            }

            Integer patientUserId = appointment.getPatient().getUser().getId();
            String clinicName = appointment.getClinic() != null ? appointment.getClinic().getClinicName() : "Phòng khám";
            String doctorName = appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Bác sĩ";
            
            // Format thời gian
            java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
            java.time.LocalDateTime startDateTime = appointment.getStartDateTime().atZone(zoneId).toLocalDateTime();
            String timeStr = startDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

            String title;
            String message;
            String notificationType;
            String priority = "MEDIUM";

            if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                title = "Lịch hẹn đã hoàn thành";
                message = String.format(
                    "Bác sĩ %s đã hoàn thành lịch khám của bạn tại %s vào lúc %s. Bạn có thể xem bệnh án trong hồ sơ.",
                    doctorName, clinicName, timeStr);
                notificationType = "APPOINTMENT_COMPLETED";
            } else if ("IN_PROGRESS".equalsIgnoreCase(newStatus)) {
                title = "Đang khám";
                message = String.format(
                    "Bác sĩ %s đang thực hiện khám cho bạn tại %s.",
                    doctorName, clinicName);
                notificationType = "APPOINTMENT_IN_PROGRESS";
            } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                title = "Lịch hẹn đã bị hủy";
                message = String.format(
                    "Bác sĩ %s đã hủy lịch hẹn của bạn tại %s vào lúc %s. Vui lòng liên hệ phòng khám để được hỗ trợ.",
                    doctorName, clinicName, timeStr);
                notificationType = "APPOINTMENT_CANCELLED";
                priority = "HIGH";
            } else {
                // Các status khác
                title = "Cập nhật lịch hẹn";
                message = String.format(
                    "Bác sĩ %s đã cập nhật trạng thái lịch hẹn của bạn tại %s.",
                    doctorName, clinicName);
                notificationType = "APPOINTMENT_STATUS_UPDATED";
            }

            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(patientUserId)
                    .type(notificationType)
                    .priority(priority)
                    .title(title)
                    .message(message)
                    .actionUrl("/appointments")
                    .relatedEntityType("APPOINTMENT")
                    .relatedEntityId(appointment.getId())
                    .build();

            _notificationService.sendNotification(notiRequest);
            log.info("Sent {} notification to patient {} for appointment {} status update from {} to {}", 
                    notificationType, patientUserId, appointment.getId(), oldStatus, newStatus);
        } catch (Exception e) {
            log.error("Failed to send appointment status update notification for appointment {}: {}", 
                    appointment.getId(), e.getMessage(), e);
            // Không throw exception để không ảnh hưởng đến việc update appointment
        }
    }

    @Override
    // Lấy các lịch hẹn của bác sĩ trong một khoảng thời gian cụ thể
    public List<DoctorAppointmentDTO> findByDoctorIdAndStartDateTimeBetween(Integer doctorId, Instant startDate, Instant endDate) {
        var doctor = _doctorRepo.findById(doctorId)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
        List<Appointment> appointments = _doctorAppointmentRepo.findByDoctorIdAndStartDateTimeBetween(
                doctor.getId(), startDate, endDate);
        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Lấy user hiện tại từ SecurityContext để ghi audit
    private User resolveCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CurrentUser currentUser) {
                return _userRepo.findById(currentUser.userId()).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
