package sunshine_dental_care.services.doctor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import sunshine_dental_care.dto.doctorDTO.ClinicDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorDTO;
import sunshine_dental_care.dto.doctorDTO.PatientDTO;
import sunshine_dental_care.dto.doctorDTO.RoomDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceVariantDTO;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.repositories.doctor.DoctorAppointmentRepo;
import sunshine_dental_care.repositories.doctor.DoctorRepo;

@Service
public class DoctorAppointmentImp implements DoctorAppointmentService{


    private DoctorRepo _doctorRepo;
    private DoctorAppointmentRepo _doctorAppointmentRepo;
    private sunshine_dental_care.repositories.doctor.MedicalRecordRepository _medicalRecordRepository;

    private static final Logger logger = LoggerFactory.getLogger(DoctorAppointmentImp.class); 

    public DoctorAppointmentImp(DoctorRepo doctorRepo, DoctorAppointmentRepo doctorAppointmentRepo,
                                sunshine_dental_care.repositories.doctor.MedicalRecordRepository medicalRecordRepository) {
        _doctorRepo = doctorRepo;
        _doctorAppointmentRepo = doctorAppointmentRepo;
        _medicalRecordRepository = medicalRecordRepository;
    }

    // Hàm chuyển đổi từ entity Appointment sang DTO DoctorAppointmentDTO
    private DoctorAppointmentDTO mapToDTO(Appointment appointment) {
        // Lấy service và variant từ AppointmentService (ưu tiên từ appointmentServices đầu tiên)
        sunshine_dental_care.entities.Service service = null;
        sunshine_dental_care.entities.ServiceVariant serviceVariant = null;
        
        if (appointment.getAppointmentServices() != null && !appointment.getAppointmentServices().isEmpty()) {
            // Lấy service và variant từ AppointmentService đầu tiên
            AppointmentService firstAppointmentService = appointment.getAppointmentServices().get(0);
            service = firstAppointmentService.getService();
            serviceVariant = firstAppointmentService.getServiceVariant();
        } else if (appointment.getService() != null) {
            // Fallback: nếu không có appointmentServices, lấy từ service trực tiếp (backward compatibility)
            service = appointment.getService();
        }
        
        return DoctorAppointmentDTO.builder()
                .appointmentId(appointment.getId())
                .clinic(toClinicSummary(appointment.getClinic())) // Thông tin phòng khám
                .patient(toPatientSummary(appointment.getPatient())) // Thông tin bệnh nhân
                .doctor(toDoctorSummary(appointment.getDoctor())) // Thông tin bác sĩ
                .room(toRoomSummary(appointment.getRoom())) // Thông tin phòng khám chữa trị
                .service(toServiceDTO(service)) // Service object - lấy từ AppointmentService
                .serviceVariant(toServiceVariantDTO(serviceVariant)) // Variant từ AppointmentService
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

    // Chuyển entity ServiceVariant sang ServiceVariantDTO
    private ServiceVariantDTO toServiceVariantDTO(sunshine_dental_care.entities.ServiceVariant serviceVariant) {
        if (serviceVariant == null) {
            return null;
        }
        
        return ServiceVariantDTO.builder()
                .variantId(serviceVariant.getId())
                .variantName(serviceVariant.getVariantName())
                .duration(serviceVariant.getDuration())
                .price(serviceVariant.getPrice())
                .description(serviceVariant.getDescription())
                .currency(serviceVariant.getCurrency())
                .isActive(serviceVariant.getIsActive())
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
                .numberOfChairs(room.getNumberOfChairs())
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
    public void changeStatusAppointment(Integer appointmentId, String status) {
        var appointment = _doctorAppointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

        String originalStatus = appointment.getStatus();
        String normalized = status == null ? "" : status.trim().toUpperCase();

        // Normalize status values
        if ("IN-PROGRESS".equals(normalized) || "IN_PROGRESS".equals(normalized)) {
            normalized = "PROCESSING";
        }
        if ("CANCELLED".equals(normalized)) {
            normalized = "CANCELED";
        }

        switch (normalized) {
            case "COMPLETED":
                validateCanComplete(appointment);
                break;
            case "PROCESSING":
                validateCanProcess(appointment);
                break;
            case "CANCELED":
                validateCanCancel(appointment);
                break;
            default:
                // For other statuses, allow direct update
                break;
        }

        // Update status and note
        appointment.setStatus(normalized);


        _doctorAppointmentRepo.save(appointment);
    }

    // Kiểm tra điều kiện chuyển sang PROCESSING
    private void validateCanProcess(Appointment appointment) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant startMinus5 = appointment.getStartDateTime().minus(java.time.Duration.ofMinutes(5));

        if (now.isBefore(startMinus5)) {
            throw new IllegalArgumentException("Cannot set to PROCESSING: appointment can only start within 5 minutes of scheduled time");
        }

        if (!"SCHEDULED".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalArgumentException("Cannot set to PROCESSING: only SCHEDULED appointments can be started");
        }
    }

    // Kiểm tra điều kiện chuyển sang COMPLETED - FIXED
    private void validateCanComplete(Appointment appointment) {
        if (!"PROCESSING".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalArgumentException("Cannot set to COMPLETED: appointment must be PROCESSING");
        }

        // Kiểm tra chắc chắn đã có medical record cho appointment này
        Integer appointmentId = appointment.getId();

        // Cách 1: Kiểm tra trực tiếp qua appointment
        boolean hasMedicalRecord = _medicalRecordRepository.existsByAppointmentId(appointmentId);

        // Cách 2: Hoặc kiểm tra qua appointmentService nếu cần
        if (!hasMedicalRecord) {
            hasMedicalRecord = _medicalRecordRepository.existsByAppointmentService_Appointment_Id(appointmentId);
        }

        logger.info("[DoctorAppointment] Checking medical record for appointmentId={}, result={}",
                appointmentId, hasMedicalRecord);

        if (!hasMedicalRecord) {
            throw new IllegalArgumentException("Cannot set to COMPLETED: medical record is required for this appointment");
        }
    }

    // Kiểm tra điều kiện chuyển sang CANCELED
    private void validateCanCancel(Appointment appointment) {
        if (!"SCHEDULED".equalsIgnoreCase(appointment.getStatus()) &&
                !"CONFIRMED".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalArgumentException("Cannot set to CANCELED: only SCHEDULED or CONFIRMED appointments allowed");
        }

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant startPlus20 = appointment.getStartDateTime().plus(java.time.Duration.ofMinutes(20));

        if (now.isBefore(startPlus20)) {
            throw new IllegalArgumentException("Cannot set to CANCELED before 20 minutes after scheduled start time");
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
}
