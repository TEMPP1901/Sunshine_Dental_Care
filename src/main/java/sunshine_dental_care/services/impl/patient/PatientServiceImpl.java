package sunshine_dental_care.services.impl.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.interfaces.patient.PatientService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final UserRepo userRepo;
    private final PatientRepo patientRepo;
    private final AppointmentRepo appointmentRepo;
    private final MailService mailService;
    private final ClinicRepo clinicRepo;
    private final ServiceVariantRepo serviceVariantRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;

    // --- HELPER METHOD: Tìm User thông minh (In Log để debug) ---
    private User findUser(String userIdOrEmail) {
        System.out.println(">>> [DEBUG] Đang tìm User với từ khóa: " + userIdOrEmail);

        if (userIdOrEmail == null) {
            throw new RuntimeException("Token không hợp lệ (User identifier is null)");
        }

        // Nếu là số -> Tìm theo ID
        if (userIdOrEmail.matches("\\d+")) {
            System.out.println(">>> [DEBUG] Phát hiện là ID. Đang tìm theo ID...");
            return userRepo.findById(Integer.parseInt(userIdOrEmail))
                    .orElseThrow(() -> new RuntimeException("User not found (ID: " + userIdOrEmail + ")"));
        }
        // Nếu là Email -> Tìm theo Email
        else {
            System.out.println(">>> [DEBUG] Phát hiện là Email. Đang tìm theo Email...");
            return userRepo.findByEmail(userIdOrEmail.trim()) // Thêm .trim() để xóa khoảng trắng thừa nếu có
                    .orElseThrow(() -> new RuntimeException("User not found (Email: " + userIdOrEmail + ")"));
        }
    }

    // =================================================================
    // 1. LẤY DANH SÁCH LỊCH HẸN
    // =================================================================
    @Override
    public List<PatientAppointmentResponse> getMyAppointments(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);

        var patient = patientRepo.findByUserId(user.getId()).orElse(null);
        if (patient == null) return new ArrayList<>();

        List<Appointment> appointments = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());

        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // =================================================================
    // 2. HỦY LỊCH HẸN
    // =================================================================
    @Override
    @Transactional
    public void cancelAppointment(String userIdOrEmail, Integer appointmentId, String reason) {
        User user = findUser(userIdOrEmail);
        var patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Hồ sơ bệnh nhân không tồn tại."));

        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn."));

        // Kiểm tra quyền
        if (!appt.getPatient().getId().equals(patient.getId())) {
            throw new RuntimeException("Bạn không có quyền hủy lịch hẹn này.");
        }

        if ("COMPLETED".equalsIgnoreCase(appt.getStatus()) || "CANCELLED".equalsIgnoreCase(appt.getStatus())) {
            throw new RuntimeException("Lịch hẹn này đã hoàn thành hoặc đã bị hủy trước đó.");
        }

        if (appt.getStartDateTime().isBefore(Instant.now())) {
            throw new RuntimeException("Không thể hủy lịch hẹn đã diễn ra / quá hạn.");
        }

        appt.setStatus("CANCELLED");
        String oldNote = appt.getNote() == null ? "" : appt.getNote();
        appt.setNote(oldNote + " | [Khách hủy: " + reason + "]");
        appointmentRepo.save(appt);

        // Gửi Email
        try {
            String serviceName = "Nha khoa tổng quát";
            if (!appt.getAppointmentServices().isEmpty() && appt.getAppointmentServices().get(0).getService() != null) {
                serviceName = appt.getAppointmentServices().get(0).getService().getServiceName();
            }
            LocalDateTime ldt = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
            String timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

            mailService.sendCancellationEmail(user.getEmail(), user.getFullName(), String.valueOf(appt.getId()), serviceName, timeStr);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail hủy lịch: " + e.getMessage());
        }
    }

    // =================================================================
    // 3. ĐẶT LỊCH HẸN MỚI (FIXED USER LOOKUP)
    // =================================================================
    @Override
    @Transactional
    public Appointment createAppointment(String userIdOrEmail, BookingRequest request) {
        // 1. Tìm User (Dùng hàm findUser thông minh)
        User user = findUser(userIdOrEmail);

        // 2. Tìm Hồ sơ bệnh nhân
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Hồ sơ bệnh nhân chưa được tạo (Patient profile not found for UserID: " + user.getId() + ")"));

        // 3. Tìm Clinic & Doctor
        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new RuntimeException("Clinic not found"));
        User doctor = userRepo.findById(request.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        // 4. Tính thời gian
        long totalDurationMinutes = 0;
        List<ServiceVariant> selectedVariants = new ArrayList<>();

        for (Integer variantId : request.getServiceIds()) {
            ServiceVariant v = serviceVariantRepo.findById(variantId)
                    .orElseThrow(() -> new RuntimeException("Service Variant not found: " + variantId));
            selectedVariants.add(v);
            totalDurationMinutes += (v.getDuration() != null ? v.getDuration() : 60);
        }
        totalDurationMinutes = Math.max(30, totalDurationMinutes);

        Instant start = request.getStartDateTime();
        Instant end = start.plus(totalDurationMinutes, ChronoUnit.MINUTES);

        // 5. Validate & Tạo lịch
        List<Appointment> conflicts = appointmentRepo.findConflictAppointments(doctor.getId(), null, start, end);
        if (!conflicts.isEmpty()) {
            throw new AppointmentConflictException("Bác sĩ bận trong khung giờ này.");
        }
        if (start.isBefore(Instant.now())) {
            throw new RuntimeException("Không thể đặt lịch trong quá khứ.");
        }

        Appointment appt = new Appointment();
        appt.setClinic(clinic);
        appt.setPatient(patient);
        appt.setDoctor(doctor);
        appt.setStartDateTime(start);
        appt.setEndDateTime(end);
        appt.setStatus("PENDING");
        appt.setChannel("ONLINE_BOOKING");
        appt.setNote(request.getNote());
        appt.setCreatedBy(user);
        appt.setAppointmentType("STANDARD");
        appt.setBookingFee(BigDecimal.ZERO);

        appt = appointmentRepo.save(appt);

        // 6. Lưu dịch vụ
        for (ServiceVariant v : selectedVariants) {
            AppointmentService as = new AppointmentService();
            as.setAppointment(appt);
            as.setService(v.getService());
            as.setQuantity(1);
            as.setUnitPrice(v.getPrice());
            as.setDiscountPct(BigDecimal.ZERO);
            as.setNote(v.getVariantName());
            appointmentServiceRepo.save(as);
        }

        return appt;
    }

    // --- MAPPER ---
    private PatientAppointmentResponse mapToDTO(Appointment appt) {
        boolean canCancel = false;
        String displayStatus = appt.getStatus();
        Instant now = Instant.now();

        if (appt.getStartDateTime().isBefore(now)
                && ("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus))) {
            displayStatus = "NOSHOW";
            canCancel = false;
        }
        else if (("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus))
                && appt.getStartDateTime().isAfter(now)) {
            canCancel = true;
        }

        LocalDateTime startLocal = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
        LocalDateTime endLocal = (appt.getEndDateTime() != null) ?
                LocalDateTime.ofInstant(appt.getEndDateTime(), ZoneId.systemDefault()) : null;

        String serviceName = "Dịch vụ nha khoa";
        String variantName = "";
        if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
            sunshine_dental_care.entities.AppointmentService as = appt.getAppointmentServices().get(0);
            if (as.getService() != null) serviceName = as.getService().getServiceName();
            if (as.getNote() != null) variantName = as.getNote();
        }

        return PatientAppointmentResponse.builder()
                .appointmentId(appt.getId())
                .startDateTime(startLocal)
                .endDateTime(endLocal)
                .status(displayStatus)
                .note(appt.getNote())
                .clinicName(appt.getClinic() != null ? appt.getClinic().getClinicName() : "")
                .clinicAddress(appt.getClinic() != null ? appt.getClinic().getAddress() : "")
                .doctorName(appt.getDoctor() != null ? appt.getDoctor().getFullName() : "Đang sắp xếp")
                .doctorAvatar(appt.getDoctor() != null ? appt.getDoctor().getAvatarUrl() : null)
                .serviceName(serviceName)
                .variantName(variantName)
                .canCancel(canCancel)
                .build();
    }
}

