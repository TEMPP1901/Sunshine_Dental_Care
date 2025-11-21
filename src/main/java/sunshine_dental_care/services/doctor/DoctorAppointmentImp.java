package sunshine_dental_care.services.doctor;

import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.doctorDTO.AppointmentServiceDTO;
import sunshine_dental_care.dto.doctorDTO.ClinicDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorDTO;
import sunshine_dental_care.dto.doctorDTO.PatientDTO;
import sunshine_dental_care.dto.doctorDTO.RoomDTO;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.repositories.doctor.AppointmentServiceRepository;
import sunshine_dental_care.repositories.doctor.DoctorAppointmentRepo;
import sunshine_dental_care.repositories.doctor.DoctorRepo;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DoctorAppointmentImp implements DoctorAppointmentService{


    private DoctorRepo _doctorRepo;
    private DoctorAppointmentRepo _doctorAppointmentRepo;
    private AppointmentServiceRepository appointmentServiceRepository;

    public DoctorAppointmentImp(DoctorRepo doctorRepo,
                                DoctorAppointmentRepo doctorAppointmentRepo,
                                AppointmentServiceRepository appointmentServiceRepository) {
        _doctorRepo = doctorRepo;
        _doctorAppointmentRepo = doctorAppointmentRepo;
        this.appointmentServiceRepository = appointmentServiceRepository;
    }

    // Hàm chuyển đổi từ entity Appointment sang DTO DoctorAppointmentDTO
    private DoctorAppointmentDTO mapToDTO(Appointment appointment) {
        List<AppointmentServiceDTO> services = appointmentServiceRepository.findByAppointmentId(appointment.getId())
                .stream()
                .map(this::toAppointmentServiceDTO)
                .collect(Collectors.toList());

        return DoctorAppointmentDTO.builder()
                .appointmentId(appointment.getId())
                .clinic(toClinicSummary(appointment.getClinic())) // Thông tin phòng khám
                .patient(toPatientSummary(appointment.getPatient())) // Thông tin bệnh nhân
                .doctor(toDoctorSummary(appointment.getDoctor())) // Thông tin bác sĩ
                .room(toRoomSummary(appointment.getRoom())) // Thông tin phòng khám chữa trị
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
                .services(services)
                .build();
    }

    private AppointmentServiceDTO toAppointmentServiceDTO(AppointmentService appointmentService) {
        if (appointmentService == null) {
            return null;
        }
        var service = appointmentService.getService();
        return AppointmentServiceDTO.builder()
                .id(appointmentService.getId())
                .serviceId(service != null ? service.getId() : null)
                .serviceName(service != null ? service.getServiceName() : null)
                .serviceCategory(service != null ? service.getCategory() : null)
                .quantity(appointmentService.getQuantity())
                .unitPrice(appointmentService.getUnitPrice())
                .discountPct(appointmentService.getDiscountPct())
                .note(appointmentService.getNote())
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
    // Thay đổi trạng thái của lịch hẹn (VD: đã hoàn thành, đã hủy,...)
    public void changeStatusAppointment(Integer appointmentId, String status) {
        _doctorAppointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        _doctorAppointmentRepo.updateStatus(appointmentId, status); // Cập nhật trạng thái lịch hẹn
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
