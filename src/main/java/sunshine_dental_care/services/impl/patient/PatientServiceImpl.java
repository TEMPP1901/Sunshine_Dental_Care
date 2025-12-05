package sunshine_dental_care.services.impl.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.interfaces.patient.PatientService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    @Override
    public List<PatientAppointmentResponse> getMyAppointments(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);

        var patient = patientRepo.findByUserId(user.getId()).orElse(null);
        if (patient == null) return new ArrayList<>();

        List<Appointment> appointments = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());

        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelAppointment(String userIdOrEmail, Integer appointmentId, String reason) {
        User user = findUser(userIdOrEmail);
        var patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Hồ sơ bệnh nhân không tồn tại."));

        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn."));

        // 1. Kiểm tra quyền sở hữu
        if (!appt.getPatient().getId().equals(patient.getId())) {
            throw new RuntimeException("Bạn không có quyền hủy lịch hẹn này.");
        }

        // 2. Kiểm tra trạng thái hiện tại (trong DB)
        if ("COMPLETED".equalsIgnoreCase(appt.getStatus()) || "CANCELLED".equalsIgnoreCase(appt.getStatus())) {
            throw new RuntimeException("Lịch hẹn này đã hoàn thành hoặc đã bị hủy trước đó.");
        }

        // 3. Kiểm tra thời gian: Không cho hủy lịch ĐÃ QUA (Quá khứ)
        if (appt.getStartDateTime().isBefore(Instant.now())) {
            throw new RuntimeException("Không thể hủy lịch hẹn đã diễn ra / quá hạn.");
        }

        // 4. Cập nhật Database
        appt.setStatus("CANCELLED");
        String oldNote = appt.getNote() == null ? "" : appt.getNote();
        appt.setNote(oldNote + " | [Khách hủy: " + reason + "]");
        appointmentRepo.save(appt);

        // 5. Gửi Email xác nhận
        try {
            String serviceName = "Nha khoa tổng quát";
            if (!appt.getAppointmentServices().isEmpty() && appt.getAppointmentServices().get(0).getService() != null) {
                serviceName = appt.getAppointmentServices().get(0).getService().getServiceName();
            }

            LocalDateTime ldt = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
            String timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

            mailService.sendCancellationEmail(
                    user.getEmail(),
                    user.getFullName(),
                    String.valueOf(appt.getId()),
                    serviceName,
                    timeStr
            );
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail hủy lịch: " + e.getMessage());
        }
    }

    // --- Helper Methods ---

    private User findUser(String userIdOrEmail) {
        if (userIdOrEmail.matches("\\d+")) {
            return userRepo.findById(Integer.parseInt(userIdOrEmail))
                    .orElseThrow(() -> new RuntimeException("User not found ID: " + userIdOrEmail));
        } else {
            return userRepo.findByEmail(userIdOrEmail)
                    .orElseThrow(() -> new RuntimeException("User not found Email: " + userIdOrEmail));
        }
    }

    private PatientAppointmentResponse mapToDTO(Appointment appt) {
        boolean canCancel = false;
        String displayStatus = appt.getStatus(); // Lấy status gốc từ DB
        Instant now = Instant.now();

        // LOGIC 1: Xử lý hiển thị QUÁ HẠN (NOSHOW)
        // Nếu giờ hẹn < hiện tại VÀ trạng thái vẫn là Chờ/Đã xác nhận
        if (appt.getStartDateTime().isBefore(now)
                && ("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus))) {

            displayStatus = "NOSHOW"; // Ghi đè status trả về cho FE
            canCancel = false;        // Khóa nút hủy
        }

        // LOGIC 2: Xử lý nút HỦY (Chỉ bật khi chưa quá hạn)
        else if (("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus))
                && appt.getStartDateTime().isAfter(now)) {
            canCancel = true;
        }

        LocalDateTime startLocal = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
        LocalDateTime endLocal = null;
        if (appt.getEndDateTime() != null) {
            endLocal = LocalDateTime.ofInstant(appt.getEndDateTime(), ZoneId.systemDefault());
        }

        String serviceName = "Dịch vụ nha khoa";
        String variantName = "";
        if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
            AppointmentService as = appt.getAppointmentServices().get(0);
            if (as.getService() != null) serviceName = as.getService().getServiceName();
            if (as.getServiceVariant() != null) variantName = as.getServiceVariant().getVariantName();
        }

        return PatientAppointmentResponse.builder()
                .appointmentId(appt.getId())
                .startDateTime(startLocal)
                .endDateTime(endLocal)
                .status(displayStatus) // <--- Sử dụng status đã xử lý logic
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