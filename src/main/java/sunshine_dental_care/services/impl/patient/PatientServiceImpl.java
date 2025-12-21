package sunshine_dental_care.services.impl.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.patientDTO.PatientDashboardDTO;
import sunshine_dental_care.dto.patientDTO.PatientProfileDTO;
import sunshine_dental_care.dto.patientDTO.UpdatePatientProfileRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.repositories.AIRecommendationRepository;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.InvoiceRepo; // [QUAN TRỌNG] Dùng để tính tiền
import sunshine_dental_care.repositories.doctor.MedicalRecordRepository; // [QUAN TRỌNG] Dùng để lấy bệnh án
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.interfaces.patient.PatientService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final AIRecommendationRepository aiRecommendationRepository;

    // Inject 2 Repo quan trọng mới thêm
    private final InvoiceRepo invoiceRepo;
    private final MedicalRecordRepository medicalRecordRepository;

    // --- HELPER METHOD: Tìm User ---
    private User findUser(String userIdOrEmail) {
        if (userIdOrEmail == null) throw new RuntimeException("Token invalid");
        if (userIdOrEmail.matches("\\d+")) {
            return userRepo.findById(Integer.parseInt(userIdOrEmail))
                    .orElseThrow(() -> new RuntimeException("User not found ID: " + userIdOrEmail));
        } else {
            return userRepo.findByEmail(userIdOrEmail.trim())
                    .orElseThrow(() -> new RuntimeException("User not found Email: " + userIdOrEmail));
        }
    }

    // =================================================================
    // 1. DASHBOARD STATS (LOGIC TỔNG HỢP)
    // =================================================================
    @Override
    public PatientDashboardDTO getPatientDashboardStats(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Patient profile not found"));

        // -----------------------------------------------------------------
        // A. MEMBERSHIP (TÍNH TOÁN HẠNG DỰA TRÊN HÓA ĐƠN ĐÃ THANH TOÁN - PAID)
        // -----------------------------------------------------------------
        BigDecimal currentTotal = invoiceRepo.calculateTotalPaidByPatient(patient.getId());

        // Nếu chưa có hóa đơn nào PAID thì là 0
        if (currentTotal == null) currentTotal = BigDecimal.ZERO;

        String rank = "MEMBER";
        BigDecimal nextGoal = BigDecimal.valueOf(5000000); // Mốc lên Silver (5tr)

        double totalDouble = currentTotal.doubleValue();
        if (totalDouble >= 50000000) { // > 50tr -> Kim Cương
            rank = "DIAMOND";
            nextGoal = null; // Đã max cấp
        } else if (totalDouble >= 15000000) { // > 15tr -> Vàng
            rank = "GOLD";
            nextGoal = BigDecimal.valueOf(50000000);
        } else if (totalDouble >= 5000000) { // > 5tr -> Bạc
            rank = "SILVER";
            nextGoal = BigDecimal.valueOf(15000000);
        }

        // Cập nhật lại vào DB để lưu trữ (chỉ update nếu có thay đổi để tối ưu)
        if (!rank.equals(patient.getMembershipRank()) || currentTotal.compareTo(patient.getAccumulatedSpending()) != 0) {
            patient.setMembershipRank(rank);
            patient.setAccumulatedSpending(currentTotal);
            patientRepo.save(patient);
        }

        // -----------------------------------------------------------------
        // B. UPCOMING APPOINTMENT (LỊCH SẮP TỚI)
        // -----------------------------------------------------------------
        List<Appointment> upcoming = appointmentRepo.findUpcomingAppointmentsByPatient(patient.getId());
        PatientDashboardDTO.UpcomingAppointmentDTO nextApptDTO = null;
        if (!upcoming.isEmpty()) {
            Appointment next = upcoming.get(0);
            String serviceName = "Khám nha khoa";
            if (next.getAppointmentServices() != null && !next.getAppointmentServices().isEmpty()) {
                if (next.getAppointmentServices().get(0).getService() != null) {
                    serviceName = next.getAppointmentServices().get(0).getService().getServiceName();
                }
            }
            nextApptDTO = PatientDashboardDTO.UpcomingAppointmentDTO.builder()
                    .appointmentId(next.getId())
                    .startDateTime(LocalDateTime.ofInstant(next.getStartDateTime(), ZoneId.systemDefault()))
                    .doctorName(next.getDoctor() != null ? next.getDoctor().getFullName() : "Đang sắp xếp")
                    .serviceName(serviceName)
                    .status(next.getStatus())
                    .roomName(next.getRoom() != null ? next.getRoom().getRoomName() : "01")
                    .build();
        }

        // -----------------------------------------------------------------
        // C. WELLNESS TRACKER (DỰA TRÊN LẦN KHÁM CUỐI CÙNG)
        // -----------------------------------------------------------------
        List<Appointment> history = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());

        // Tìm lần khám COMPLETED gần nhất
        Appointment lastCompleted = history.stream()
                .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()))
                .findFirst().orElse(null);

        String healthStatus = "New";
        String healthMessage = "Chào mừng bạn! Hãy đặt lịch khám đầu tiên.";
        long daysSince = -1;

        if (lastCompleted != null) {
            LocalDate lastDate = LocalDate.ofInstant(lastCompleted.getStartDateTime(), ZoneId.systemDefault());
            daysSince = ChronoUnit.DAYS.between(lastDate, LocalDate.now());

            if (daysSince < 180) {
                healthStatus = "Excellent";
                healthMessage = "Răng miệng tốt. Hãy duy trì thói quen!";
            } else if (daysSince < 365) {
                healthStatus = "Warning";
                healthMessage = "Đã đến hạn kiểm tra định kỳ (6 tháng).";
            } else {
                healthStatus = "Overdue";
                healthMessage = "Đã quá lâu chưa khám. Cần kiểm tra ngay!";
            }
        }

        // -----------------------------------------------------------------
        // D. MEDICAL HISTORY (LẤY TOÀN BỘ DANH SÁCH BỆNH ÁN)
        // -----------------------------------------------------------------
        // Sử dụng MedicalRecordRepository để lấy list
        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());

        List<PatientDashboardDTO.MedicalRecordDTO> historyList = records.stream().map(rec -> {
            String img = null;
            // Lấy ảnh đầu tiên nếu có (tránh lỗi NullPointer)
            if (rec.getMedicalRecordImages() != null && !rec.getMedicalRecordImages().isEmpty()) {
                try {
                    img = rec.getMedicalRecordImages().iterator().next().getImageUrl();
                } catch (Exception e) { img = null; }
            }

            return PatientDashboardDTO.MedicalRecordDTO.builder()
                    .recordId(rec.getId())
                    .visitDate(rec.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .diagnosis(rec.getDiagnosis())
                    .treatment(rec.getTreatmentPlan())
                    .doctorName(rec.getDoctor() != null ? rec.getDoctor().getFullName() : "Nha sĩ")
                    .note(rec.getNote())
                    .prescriptionNote(rec.getPrescriptionNote())
                    .imageUrl(img)
                    .build();
        }).collect(Collectors.toList());

        // -----------------------------------------------------------------
        // E. AI & RECENT ACTIVITY (LẤY 3 HOẠT ĐỘNG GẦN NHẤT)
        // -----------------------------------------------------------------
        AIRecommendation aiRec = aiRecommendationRepository.findLatestByPatientId(patient.getId());
        String aiTip = (aiRec != null) ? aiRec.getReason() : "Chải răng 2 lần/ngày để ngừa sâu răng.";

        List<PatientDashboardDTO.ActivityDTO> activities = new ArrayList<>();
        int limit = Math.min(history.size(), 3);
        for(int i = 0; i < limit; i++) {
            Appointment a = history.get(i);
            String action = "Đặt lịch";
            if("COMPLETED".equals(a.getStatus())) action = "Khám xong";
            else if("CANCELLED".equals(a.getStatus())) action = "Đã hủy";

            LocalDateTime ldt = LocalDateTime.ofInstant(a.getStartDateTime(), ZoneId.systemDefault());
            activities.add(PatientDashboardDTO.ActivityDTO.builder()
                    .title(action + " (" + (a.getDoctor() != null ? a.getDoctor().getFullName() : "BS") + ")")
                    .date(ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .type(a.getStatus()).build());
        }

        // --- BUILD FINAL DTO ---
        return PatientDashboardDTO.builder()
                .fullName(patient.getFullName())
                .patientCode(patient.getPatientCode())
                .avatarUrl(user.getAvatarUrl())
                // Membership Data
                .memberTier(rank)
                .totalSpent(currentTotal)
                .nextTierGoal(nextGoal)
                // Dashboard Data
                .healthStatus(healthStatus)
                .healthMessage(healthMessage)
                .daysSinceLastVisit(daysSince)
                .nextAppointment(nextApptDTO)
                .medicalHistory(historyList) // Trả về List đầy đủ
                .latestAiTip(aiTip)
                .recentActivities(activities)
                .build();
    }

    // =================================================================
    // CÁC HÀM KHÁC (GET APPOINTMENTS, CANCEL, BOOKING...) - GIỮ NGUYÊN
    // =================================================================

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

        if (!appt.getPatient().getId().equals(patient.getId())) throw new RuntimeException("Bạn không có quyền hủy lịch hẹn này.");
        if ("COMPLETED".equalsIgnoreCase(appt.getStatus()) || "CANCELLED".equalsIgnoreCase(appt.getStatus())) throw new RuntimeException("Lịch hẹn này đã hoàn thành hoặc đã bị hủy.");
        if (appt.getStartDateTime().isBefore(Instant.now())) throw new RuntimeException("Không thể hủy lịch hẹn quá hạn.");

        appt.setStatus("CANCELLED");
        String oldNote = appt.getNote() == null ? "" : appt.getNote();
        appt.setNote(oldNote + " | [Khách hủy: " + reason + "]");
        appointmentRepo.save(appt);

        try {
            String serviceName = "Nha khoa tổng quát";
            if (!appt.getAppointmentServices().isEmpty() && appt.getAppointmentServices().get(0).getService() != null) {
                serviceName = appt.getAppointmentServices().get(0).getService().getServiceName();
            }
            LocalDateTime ldt = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
            mailService.sendCancellationEmail(patient, String.valueOf(appt.getId()), serviceName, ldt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    @Transactional
    public Appointment createAppointment(String userIdOrEmail, BookingRequest request) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId()).orElseThrow(() -> new RuntimeException("Patient not found."));
        Clinic clinic = clinicRepo.findById(request.getClinicId()).orElseThrow(() -> new RuntimeException("Clinic not found"));
        User doctor = userRepo.findById(request.getDoctorId()).orElseThrow(() -> new RuntimeException("Doctor not found"));

        long totalDurationMinutes = 0;
        List<ServiceVariant> selectedVariants = new ArrayList<>();
        for (Integer vid : request.getServiceIds()) {
            ServiceVariant v = serviceVariantRepo.findById(vid).orElseThrow(() -> new RuntimeException("Variant not found"));
            selectedVariants.add(v);
            totalDurationMinutes += (v.getDuration() != null ? v.getDuration() : 60);
        }
        totalDurationMinutes = Math.max(30, totalDurationMinutes);
        Instant start = request.getStartDateTime();
        Instant end = start.plus(totalDurationMinutes, ChronoUnit.MINUTES);

        if (!appointmentRepo.findConflictAppointments(doctor.getId(), null, start, end).isEmpty()) throw new AppointmentConflictException("Bác sĩ bận.");
        if (start.isBefore(Instant.now())) throw new RuntimeException("Không đặt lịch quá khứ.");

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

    @Override
    public PatientProfileDTO getPatientProfile(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId()).orElseThrow(() -> new RuntimeException("Patient not found"));
        return PatientProfileDTO.builder().patientId(patient.getId()).fullName(patient.getFullName()).phone(patient.getPhone()).email(patient.getEmail()).gender(patient.getGender()).dateOfBirth(patient.getDateOfBirth()).address(patient.getAddress()).note(patient.getNote()).patientCode(patient.getPatientCode()).build();
    }

    @Override
    public void updatePatientProfile(String userIdOrEmail, UpdatePatientProfileRequest request) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId()).orElseThrow(() -> new RuntimeException("Patient not found"));
        if (request.getFullName() != null) patient.setFullName(request.getFullName());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());
        if (request.getNote() != null) patient.setNote(request.getNote());
        patientRepo.save(patient);
    }

    private PatientAppointmentResponse mapToDTO(Appointment appt) {
        boolean canCancel = false;
        String displayStatus = appt.getStatus();
        Instant now = Instant.now();
        if (appt.getStartDateTime().isBefore(now) && ("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus))) {
            displayStatus = "NOSHOW"; canCancel = false;
        } else if (("PENDING".equalsIgnoreCase(displayStatus) || "CONFIRMED".equalsIgnoreCase(displayStatus)) && appt.getStartDateTime().isAfter(now)) {
            canCancel = true;
        }
        LocalDateTime startLocal = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
        LocalDateTime endLocal = (appt.getEndDateTime() != null) ? LocalDateTime.ofInstant(appt.getEndDateTime(), ZoneId.systemDefault()) : null;
        String serviceName = "Dịch vụ nha khoa";
        String variantName = "";
        if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
            AppointmentService as = appt.getAppointmentServices().get(0);
            if (as.getService() != null) serviceName = as.getService().getServiceName();
            if (as.getNote() != null) variantName = as.getNote();
        }
        return PatientAppointmentResponse.builder().appointmentId(appt.getId()).startDateTime(startLocal).endDateTime(endLocal).status(displayStatus).note(appt.getNote()).clinicName(appt.getClinic() != null ? appt.getClinic().getClinicName() : "").clinicAddress(appt.getClinic() != null ? appt.getClinic().getAddress() : "").doctorName(appt.getDoctor() != null ? appt.getDoctor().getFullName() : "Đang sắp xếp").doctorAvatar(appt.getDoctor() != null ? appt.getDoctor().getAvatarUrl() : null).serviceName(serviceName).variantName(variantName).canCancel(canCancel).build();
    }
}