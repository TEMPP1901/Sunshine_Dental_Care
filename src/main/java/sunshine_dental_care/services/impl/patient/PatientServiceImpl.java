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
import sunshine_dental_care.repositories.reception.InvoiceRepo;
import sunshine_dental_care.repositories.doctor.MedicalRecordRepository;
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
    private final InvoiceRepo invoiceRepo;
    private final MedicalRecordRepository medicalRecordRepository;

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

        // [UPDATE] Đồng bộ logic chặn hủy với Reception: Không hủy được nếu đang IN_PROGRESS/PROCESSING
        String status = appt.getStatus().toUpperCase();
        if ("COMPLETED".equals(status) || status.contains("CANCEL") || "IN_PROGRESS".equals(status) || "PROCESSING".equals(status)) {
            throw new RuntimeException("Không thể hủy lịch hẹn khi đang thực hiện hoặc đã kết thúc.");
        }

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
            String timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
            mailService.sendCancellationEmail(patient, String.valueOf(appt.getId()), serviceName, timeStr);
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

    // --- DASHBOARD (Giữ nguyên logic của bạn) ---
    @Override
    public PatientDashboardDTO getPatientDashboardStats(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId()).orElseThrow(() -> new RuntimeException("Patient not found"));

        BigDecimal currentTotal = invoiceRepo.calculateTotalPaidByPatient(patient.getId());
        if (currentTotal == null) currentTotal = BigDecimal.ZERO;

        String rank = "MEMBER";
        BigDecimal nextGoal = BigDecimal.valueOf(5000000);
        double totalDouble = currentTotal.doubleValue();
        if (totalDouble >= 50000000) { rank = "DIAMOND"; nextGoal = null; }
        else if (totalDouble >= 15000000) { rank = "GOLD"; nextGoal = BigDecimal.valueOf(50000000); }
        else if (totalDouble >= 5000000) { rank = "SILVER"; nextGoal = BigDecimal.valueOf(15000000); }

        if (!rank.equals(patient.getMembershipRank()) || currentTotal.compareTo(patient.getAccumulatedSpending()) != 0) {
            patient.setMembershipRank(rank);
            patient.setAccumulatedSpending(currentTotal);
            patientRepo.save(patient);
        }

        List<Appointment> upcoming = appointmentRepo.findUpcomingAppointmentsByPatient(patient.getId());
        PatientDashboardDTO.UpcomingAppointmentDTO nextApptDTO = null;
        if (!upcoming.isEmpty()) {
            Appointment next = upcoming.get(0);
            String serviceName = "Khám nha khoa";
            if (next.getAppointmentServices() != null && !next.getAppointmentServices().isEmpty()) {
                if (next.getAppointmentServices().get(0).getService() != null) serviceName = next.getAppointmentServices().get(0).getService().getServiceName();
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

        List<Appointment> history = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());
        Appointment lastCompleted = history.stream().filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus())).findFirst().orElse(null);
        String healthStatus = "New";
        String healthMessage = "Chào mừng bạn!";
        long daysSince = -1;
        if (lastCompleted != null) {
            LocalDate lastDate = LocalDate.ofInstant(lastCompleted.getStartDateTime(), ZoneId.systemDefault());
            daysSince = ChronoUnit.DAYS.between(lastDate, LocalDate.now());
            if (daysSince < 180) { healthStatus = "Excellent"; healthMessage = "Răng miệng tốt."; }
            else if (daysSince < 365) { healthStatus = "Warning"; healthMessage = "Đến hạn kiểm tra."; }
            else { healthStatus = "Overdue"; healthMessage = "Cần kiểm tra ngay!"; }
        }

        List<MedicalRecord> records = medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
        List<PatientDashboardDTO.MedicalRecordDTO> historyList = records.stream().map(rec -> {
            String img = null;
            if (rec.getMedicalRecordImages() != null && !rec.getMedicalRecordImages().isEmpty()) {
                try { img = rec.getMedicalRecordImages().iterator().next().getImageUrl(); } catch (Exception e) {}
            }
            return PatientDashboardDTO.MedicalRecordDTO.builder()
                    .recordId(rec.getId())
                    .visitDate(rec.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .diagnosis(rec.getDiagnosis())
                    .treatment(rec.getTreatmentPlan())
                    .doctorName(rec.getDoctor() != null ? rec.getDoctor().getFullName() : "Nha sĩ")
                    .note(rec.getNote())
                    .prescriptionNote(rec.getPrescriptionNote())
                    .imageUrl(img).build();
        }).collect(Collectors.toList());

        AIRecommendation aiRec = aiRecommendationRepository.findLatestByPatientId(patient.getId());
        String aiTip = (aiRec != null) ? aiRec.getReason() : "Chải răng 2 lần/ngày.";
        List<PatientDashboardDTO.ActivityDTO> activities = new ArrayList<>();
        int limit = Math.min(history.size(), 3);
        for(int i = 0; i < limit; i++) {
            Appointment a = history.get(i);
            LocalDateTime ldt = LocalDateTime.ofInstant(a.getStartDateTime(), ZoneId.systemDefault());
            activities.add(PatientDashboardDTO.ActivityDTO.builder()
                    .title("Lịch hẹn (" + (a.getDoctor() != null ? a.getDoctor().getFullName() : "BS") + ")")
                    .date(ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .type(a.getStatus()).build());
        }

        return PatientDashboardDTO.builder()
                .fullName(patient.getFullName()).patientCode(patient.getPatientCode()).avatarUrl(user.getAvatarUrl())
                .memberTier(rank).totalSpent(currentTotal).nextTierGoal(nextGoal)
                .healthStatus(healthStatus).healthMessage(healthMessage).daysSinceLastVisit(daysSince)
                .nextAppointment(nextApptDTO).medicalHistory(historyList).latestAiTip(aiTip).recentActivities(activities)
                .build();
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

    // --- MAPPER ĐÃ ĐỒNG BỘ LOGIC STATUS VỚI RECEPTION ---
    private PatientAppointmentResponse mapToDTO(Appointment appt) {
        boolean canCancel = false;
        String displayStatus = appt.getStatus();
        Instant now = Instant.now();

        // Danh sách trạng thái được phép hủy: PENDING, SCHEDULED (CONFIRMED)
        // Không được hủy nếu: IN_PROGRESS, PROCESSING, COMPLETED, CANCELLED
        boolean isProcessOrDone = "IN_PROGRESS".equalsIgnoreCase(displayStatus)
                || "PROCESSING".equalsIgnoreCase(displayStatus)
                || "COMPLETED".equalsIgnoreCase(displayStatus)
                || displayStatus.toUpperCase().contains("CANCEL");

        // 1. Logic NOSHOW (Quá hạn)
        // Chỉ đánh dấu NOSHOW nếu trạng thái vẫn đang "treo" (PENDING/SCHEDULED) mà đã qua giờ
        if (appt.getStartDateTime().isBefore(now) && !isProcessOrDone) {
            displayStatus = "NOSHOW";
            canCancel = false;
        }
        // 2. Logic cho phép Hủy
        // Chỉ được hủy khi chưa quá hạn và trạng thái không phải là đang làm/đã xong
        else if (!isProcessOrDone && appt.getStartDateTime().isAfter(now)) {
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
        return PatientAppointmentResponse.builder()
                .appointmentId(appt.getId())
                .startDateTime(startLocal).endDateTime(endLocal)
                .status(displayStatus)
                .note(appt.getNote())
                .clinicName(appt.getClinic() != null ? appt.getClinic().getClinicName() : "")
                .clinicAddress(appt.getClinic() != null ? appt.getClinic().getAddress() : "")
                .doctorName(appt.getDoctor() != null ? appt.getDoctor().getFullName() : "Đang sắp xếp")
                .doctorAvatar(appt.getDoctor() != null ? appt.getDoctor().getAvatarUrl() : null)
                .serviceName(serviceName).variantName(variantName)
                .canCancel(canCancel)
                .build();
    }
}