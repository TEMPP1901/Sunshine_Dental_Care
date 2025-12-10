package sunshine_dental_care.services.impl.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.patientDTO.PatientDashboardDTO; // Import DTO Dashboard
import sunshine_dental_care.dto.patientDTO.PatientProfileDTO; // Import DTO Profile
import sunshine_dental_care.dto.patientDTO.UpdatePatientProfileRequest; // Import Request Update
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.repositories.AIRecommendationRepository; // Import Repo AI
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

    // Inject thêm Repo AI
    private final AIRecommendationRepository aiRecommendationRepository;

    // --- HELPER METHOD: Tìm User thông minh ---
    private User findUser(String userIdOrEmail) {
        System.out.println(">>> [DEBUG] Đang tìm User với từ khóa: " + userIdOrEmail);

        if (userIdOrEmail == null) {
            throw new RuntimeException("Token không hợp lệ (User identifier is null)");
        }
        if (userIdOrEmail.matches("\\d+")) {
            return userRepo.findById(Integer.parseInt(userIdOrEmail))
                    .orElseThrow(() -> new RuntimeException("User not found (ID: " + userIdOrEmail + ")"));
        } else {
            return userRepo.findByEmail(userIdOrEmail.trim())
                    .orElseThrow(() -> new RuntimeException("User not found (Email: " + userIdOrEmail + ")"));
        }
    }

    // =================================================================
    // 1. LẤY DANH SÁCH LỊCH HẸN (CŨ - GIỮ NGUYÊN)
    // =================================================================
    @Override
    public List<PatientAppointmentResponse> getMyAppointments(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        var patient = patientRepo.findByUserId(user.getId()).orElse(null);
        if (patient == null) return new ArrayList<>();
        // Sử dụng patient.getId() thay vì getPatientId()
        List<Appointment> appointments = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());
        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // =================================================================
    // 2. HỦY LỊCH HẸN (CŨ - GIỮ NGUYÊN)
    // =================================================================
    @Override
    @Transactional
    public void cancelAppointment(String userIdOrEmail, Integer appointmentId, String reason) {
        User user = findUser(userIdOrEmail);
        var patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Hồ sơ bệnh nhân không tồn tại."));

        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn."));

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

        // Gửi mail hủy
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
    // 3. ĐẶT LỊCH HẸN MỚI (CŨ - GIỮ NGUYÊN)
    // =================================================================
    @Override
    @Transactional
    public Appointment createAppointment(String userIdOrEmail, BookingRequest request) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Hồ sơ bệnh nhân chưa được tạo."));
        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new RuntimeException("Clinic not found"));
        User doctor = userRepo.findById(request.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

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

    // =================================================================
    // 4. [MỚI] DASHBOARD STATS (LOGIC WELLNESS + AI TỰ ĐỘNG)
    // =================================================================
    @Override
    public PatientDashboardDTO getPatientDashboardStats(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Patient profile not found"));

        // A. KHỐI LỊCH HẸN SẮP TỚI
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

        // B. KHỐI WELLNESS TRACKER (Trạng thái sức khỏe định kỳ)
        List<Appointment> history = appointmentRepo.findByPatientIdOrderByStartDateTimeDesc(patient.getId());

        // Tìm lần khám đã HOÀN THÀNH gần nhất
        Appointment lastCompleted = history.stream()
                .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()))
                .findFirst()
                .orElse(null);

        String healthStatus = "New";
        String healthMessage = "Chào mừng bạn! Hãy đặt lịch khám đầu tiên để bảo vệ nụ cười.";
        long daysSince = -1;

        if (lastCompleted != null) {
            LocalDate lastDate = LocalDate.ofInstant(lastCompleted.getStartDateTime(), ZoneId.systemDefault());
            daysSince = ChronoUnit.DAYS.between(lastDate, LocalDate.now());

            // Quy chuẩn: 6 tháng (180 ngày)
            if (daysSince < 180) {
                healthStatus = "Excellent";
                healthMessage = "Răng miệng đang được bảo vệ tốt. Hãy duy trì thói quen nhé!";
            } else if (daysSince < 365) {
                healthStatus = "Warning";
                healthMessage = "Đã đến hạn kiểm tra định kỳ (6 tháng). Hãy đặt lịch sớm.";
            } else {
                healthStatus = "Overdue";
                healthMessage = "Đã quá lâu bạn chưa khám răng. Cần kiểm tra ngay!";
            }
        }

        // C. KHỐI AI RECOMMENDATION (Tự động hóa)
        AIRecommendation aiRec = aiRecommendationRepository.findLatestByPatientId(patient.getId());
        String aiTip;

        if (aiRec != null) {
            // Ưu tiên 1: Lấy từ DB (do bác sĩ hoặc hệ thống AI trước đó tạo ra)
            aiTip = aiRec.getReason();
        } else if (lastCompleted != null) {
            // Ưu tiên 2: Tự sinh lời khuyên dựa trên tên dịch vụ của lần khám cuối
            String lastService = "";
            if (!lastCompleted.getAppointmentServices().isEmpty() && lastCompleted.getAppointmentServices().get(0).getService() != null) {
                lastService = lastCompleted.getAppointmentServices().get(0).getService().getServiceName().toLowerCase();
            }

            // Logic gợi ý đơn giản
            if (lastService.contains("nhổ") || lastService.contains("extraction")) {
                aiTip = "Sau khi nhổ răng, hạn chế súc miệng mạnh và tránh dùng ống hút trong 24h đầu để vết thương mau lành.";
            } else if (lastService.contains("cao răng") || lastService.contains("vôi răng") || lastService.contains("scaling")) {
                aiTip = "Lợi có thể hơi nhạy cảm sau khi lấy cao răng. Hãy dùng bàn chải lông mềm và nước súc miệng dịu nhẹ.";
            } else if (lastService.contains("tẩy trắng") || lastService.contains("whitening")) {
                aiTip = "Để giữ màu răng trắng sáng, hãy hạn chế thực phẩm có màu đậm (cà phê, trà, nghệ) trong 2 tuần tới.";
            } else if (lastService.contains("niềng") || lastService.contains("braces")) {
                aiTip = "Hãy nhớ vệ sinh kỹ các mắc cài và dùng chỉ nha khoa chuyên dụng sau mỗi bữa ăn.";
            } else {
                aiTip = "Đừng quên thay bàn chải đánh răng định kỳ 3 tháng/lần để đảm bảo vệ sinh.";
            }
        } else {
            // Mặc định cho người mới
            aiTip = "Chải răng 2 lần/ngày và dùng chỉ nha khoa là cách đơn giản nhất để phòng ngừa sâu răng.";
        }

        // D. LỊCH SỬ HOẠT ĐỘNG (Recent Activity)
        List<PatientDashboardDTO.ActivityDTO> activities = new ArrayList<>();
        int limit = Math.min(history.size(), 3);

        for(int i = 0; i < limit; i++) {
            Appointment a = history.get(i);
            String action = "Đặt lịch";
            if("COMPLETED".equals(a.getStatus())) action = "Khám xong";
            else if("CANCELLED".equals(a.getStatus())) action = "Đã hủy";
            else if("NOSHOW".equals(a.getStatus())) action = "Vắng mặt";

            LocalDateTime ldt = LocalDateTime.ofInstant(a.getStartDateTime(), ZoneId.systemDefault());

            activities.add(PatientDashboardDTO.ActivityDTO.builder()
                    .title(action + " (" + (a.getDoctor() != null ? a.getDoctor().getFullName() : "BS") + ")")
                    .date(ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .type(a.getStatus())
                    .build());
        }

        return PatientDashboardDTO.builder()
                .fullName(patient.getFullName())
                .patientCode(patient.getPatientCode())
                .avatarUrl(user.getAvatarUrl())
                .healthStatus(healthStatus)
                .healthMessage(healthMessage)
                .daysSinceLastVisit(daysSince)
                .nextAppointment(nextApptDTO)
                .latestAiTip(aiTip)
                .recentActivities(activities)
                .build();
    }

    // =================================================================
    // 5. CÁC HÀM PROFILE (MỚI)
    // =================================================================
    @Override
    public PatientProfileDTO getPatientProfile(String userIdOrEmail) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        return PatientProfileDTO.builder()
                .patientId(patient.getId()) // Sửa patientId -> getId()
                .fullName(patient.getFullName())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .gender(patient.getGender())
                .dateOfBirth(patient.getDateOfBirth())
                .address(patient.getAddress())
                .note(patient.getNote())
                .patientCode(patient.getPatientCode())
                .build();
    }

    @Override
    public void updatePatientProfile(String userIdOrEmail, UpdatePatientProfileRequest request) {
        User user = findUser(userIdOrEmail);
        Patient patient = patientRepo.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        if (request.getFullName() != null) patient.setFullName(request.getFullName());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());
        if (request.getNote() != null) patient.setNote(request.getNote());

        patientRepo.save(patient);
    }

    // --- MAPPER (Giữ nguyên) ---
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