package sunshine_dental_care.services.impl.reception;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service; // Annotation @Service
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.ServiceItemRequest;
import sunshine_dental_care.dto.receptionDTO.mapper.AppointmentMapper;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AccessDeniedException;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;

import sunshine_dental_care.dto.receptionDTO.mapper.DoctorScheduleMapper;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.ServiceRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.reception.ReceptionService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionServiceImpl implements ReceptionService {

    // =========================================================================================
    // 1. REPOSITORIES VÀ MAPPER
    // =========================================================================================
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final PatientRepo patientRepo;
    private final ServiceRepo serviceRepo;
    private final RoomRepo roomRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;

    private final DoctorScheduleMapper doctorScheduleMapper;
    private final AppointmentMapper appointmentMapper;

    /**
     * Xác định Clinic ID mà Receptionist đang làm việc (SỬ DỤNG CUSTOM RUNTIME EXCEPTION).
     */
    private Integer getReceptionistClinicId(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("User context is missing.");
        }

        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(currentUser.userId());

        UserClinicAssignment primaryAssignment = assignments.stream()
                .filter(a -> a.getIsPrimary() != null && a.getIsPrimary())
                .findFirst()
                .orElse(null);

        if (primaryAssignment == null && !assignments.isEmpty()) {
            primaryAssignment = assignments.getFirst();
        }

        if (primaryAssignment == null || primaryAssignment.getClinic() == null) {
            throw new AccessDeniedException("Receptionist is not assigned to any primary clinic or clinic information is missing.");
        }

        return primaryAssignment.getClinic().getId();
    }

    /**
     * [IMPLEMENTATION] Lấy lịch làm việc của Bác sĩ theo yêu cầu xem (SỬ DỤNG MAPPER ĐÃ TÁCH).
     */
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getDoctorSchedulesForView(
            CurrentUser currentUser,
            LocalDate date,
            Integer requestedClinicId) {

        Integer defaultClinicId = getReceptionistClinicId(currentUser);

        Integer targetClinicId = (requestedClinicId != null && requestedClinicId > 0)
                ? requestedClinicId
                : defaultClinicId;

        log.info("User {} (Default Clinic {}) viewing schedules for target Clinic ID {} on {}",
                currentUser.userId(), defaultClinicId, targetClinicId, date);

        List<DoctorSchedule> schedules = doctorScheduleRepo.findByClinicAndDate(targetClinicId, date);

        // SỬ DỤNG MAPPER ĐÃ TÁCH
        return schedules.stream()
                .map(doctorScheduleMapper::mapToScheduleDto)
                .collect(Collectors.toList());
    }

    /**
     * [IMPLEMENTATION] Tạo mới lịch hẹn cho bệnh nhân (Walk-in hoặc Offline).
     */
    @Override
    @Transactional
    public AppointmentResponse createNewAppointment(CurrentUser currentUser, AppointmentRequest request) {

        // 1. Xác định người tạo
        User creator = userRepo.getReferenceById(currentUser.userId());

        // 2. Lấy Clinic
        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found."));

        // 3. Lấy Doctor
        User doctor = userRepo.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found."));

        // 4. XỬ LÝ ROOM (CHO PHÉP NULL) -> FIX LỖI 500 TẠI ĐÂY
        Room room = null;
        if (request.getRoomId() != null) {
            room = roomRepo.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found."));
        }

        // 5. ĐẶT LỊCH CHO BỆNH NHÂN (Dành cho bệnh nhân đã đăng kí trước)
        Patient patient;

        // TRƯỜNG HỢP 1: Lễ tân đặt hộ (Có gửi patientId cụ thể)
        if (request.getPatientId() != null && request.getPatientId() > 0) {
            patient = patientRepo.findById(request.getPatientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân với ID: " + request.getPatientId()));
        }
        // TRƯỜNG HỢP 2: Khách hàng tự đặt (Dựa vào Token đăng nhập)
        else {
            // Tìm hồ sơ Patient gắn với User đang đăng nhập
            patient = patientRepo.findByUserId(currentUser.userId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tài khoản này chưa có hồ sơ Bệnh nhân hợp lệ. Vui lòng liên hệ CSKH."
                    ));

            // Logic này đảm bảo chỉ những User đã qua bước Sign-up chuẩn (có Patient Code) mới đặt được.
        }
        // 6. Tính toán EndDateTime
        long totalDurationMinutes = request.getServices().stream()
                .mapToLong(serviceReq -> {
                    sunshine_dental_care.entities.Service serviceEntity = serviceRepo.findById(serviceReq.getServiceId())
                            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceReq.getServiceId()));
                    return (long) serviceEntity.getDefaultDuration() * serviceReq.getQuantity();
                })
                .sum();

        Instant start = request.getStartDateTime();
        Instant end = start.plusSeconds(TimeUnit.MINUTES.toSeconds(totalDurationMinutes));

        // 7. Kiểm tra Xung đột (Conflict Check)
        // 7a. iểm tra Giờ làm việc của Bác sĩ (Doctor Schedule)
        // Chuyển đổi Instant sang ngày giờ địa phương để so sánh với lịch làm việc
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate bookingDate = start.atZone(zoneId).toLocalDate();
        java.time.LocalTime bookingStartTime = start.atZone(zoneId).toLocalTime();
        java.time.LocalTime bookingEndTime = end.atZone(zoneId).toLocalTime();

        // Lấy các ca làm việc ACTIVE của bác sĩ tại Clinic này trong ngày
        List<DoctorSchedule> doctorSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                request.getDoctorId(),
                request.getClinicId(),
                bookingDate
        );

        boolean isWithinWorkingHours = false;
        for (DoctorSchedule schedule : doctorSchedules) {
            // Logic: Giờ đặt phải nằm TRỌN VẸN trong một ca làm việc
            // Start >= ScheduleStart  VÀ  End <= ScheduleEnd
            if (!bookingStartTime.isBefore(schedule.getStartTime()) &&
                    !bookingEndTime.isAfter(schedule.getEndTime())) {
                isWithinWorkingHours = true;
                break; // Tìm thấy ca phù hợp
            }
        }

        if (!isWithinWorkingHours) {
            throw new AppointmentConflictException("Thời gian đặt lịch (" + bookingStartTime + " - " + bookingEndTime + ") nằm ngoài ca làm việc của bác sĩ hoặc vượt quá thời gian phục vụ.");
        }

        // 7b. Kiểm tra Xung đột với Lịch hẹn khác (Appointments)
        Integer roomIdToCheck = (room != null) ? room.getId() : null;

        List<Appointment> conflicts = appointmentRepo.findConflictAppointments(
                request.getDoctorId(),
                roomIdToCheck,
                start,
                end
        );

        if (!conflicts.isEmpty()) {
            throw new AppointmentConflictException("Bác sĩ đã có lịch hẹn khác trong khoảng thời gian này.");
        }
        // 8. Tạo và Lưu Appointment
        Appointment appointment = new Appointment();
        appointment.setClinic(clinic);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setRoom(room); // Có thể là null để reception set sau
        appointment.setStartDateTime(start);
        appointment.setEndDateTime(end);
        appointment.setStatus(request.getStatus());
        appointment.setChannel(request.getChannel() != null ? request.getChannel() : "Walk-in");
        appointment.setNote(request.getNote());
        appointment.setCreatedBy(creator);

        appointment = appointmentRepo.save(appointment);

        // 9. Lưu Appointment Services
        for (ServiceItemRequest serviceReq : request.getServices()) {
            sunshine_dental_care.entities.Service serviceEntity = serviceRepo.findById(serviceReq.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found."));

            AppointmentService as = new AppointmentService();
            as.setAppointment(appointment);
            as.setService(serviceEntity);
            as.setQuantity(serviceReq.getQuantity());
            as.setUnitPrice(serviceReq.getUnitPrice());
            as.setDiscountPct(serviceReq.getDiscountPct());
            as.setNote(serviceReq.getNote());

            appointmentServiceRepo.save(as);
        }

        return appointmentMapper.mapToAppointmentResponse(appointment);
    }
}