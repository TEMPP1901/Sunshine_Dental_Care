package sunshine_dental_care.services.impl.reception;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.validation.ValidationException;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Annotation của Spring

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.AppointmentUpdateRequest;
import sunshine_dental_care.dto.receptionDTO.BillInvoiceDTO;
import sunshine_dental_care.dto.receptionDTO.PatientHistoryDTO;
import sunshine_dental_care.dto.receptionDTO.PatientRequest;
import sunshine_dental_care.dto.receptionDTO.PatientResponse;
import sunshine_dental_care.dto.receptionDTO.RescheduleRequest;
import sunshine_dental_care.dto.receptionDTO.ServiceItemRequest;
import sunshine_dental_care.dto.receptionDTO.mapper.AppointmentMapper;
import sunshine_dental_care.dto.receptionDTO.mapper.DoctorScheduleMapper;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Log;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.ServiceVariant;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.reception.AccessDeniedException;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.repositories.system.LogRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.auth_service.PatientCodeService;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.reception.ReceptionService;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionServiceImpl implements ReceptionService {

    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final PatientRepo patientRepo;
    private final RoomRepo roomRepo;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;
    private final ClinicRepo clinicRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;
    private final LogRepo logRepo;
    private final ServiceVariantRepo serviceVariantRepo;
    private final PatientCodeService patientCodeService;
    private final DoctorScheduleMapper doctorScheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final NotificationService notificationService;
    private final SystemConfigService systemConfigService;


    private Integer getReceptionistClinicId(CurrentUser currentUser) {
        if (currentUser == null) throw new AccessDeniedException("User context is missing.");
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(currentUser.userId());
        UserClinicAssignment primaryAssignment = assignments.stream()
                .filter(a -> a.getIsPrimary() != null && a.getIsPrimary()).findFirst().orElse(null);
        if (primaryAssignment == null && !assignments.isEmpty()) primaryAssignment = assignments.getFirst();
        if (primaryAssignment == null || primaryAssignment.getClinic() == null) throw new AccessDeniedException("No clinic assigned.");
        return primaryAssignment.getClinic().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getDoctorSchedulesForView(CurrentUser currentUser, LocalDate date, Integer requestedClinicId) {
        Integer defaultClinicId = getReceptionistClinicId(currentUser);
        Integer targetClinicId = (requestedClinicId != null && requestedClinicId > 0) ? requestedClinicId : defaultClinicId;
        return doctorScheduleRepo.findByClinicAndDate(targetClinicId, date).stream()
                .map(doctorScheduleMapper::mapToScheduleDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsForDashboard(CurrentUser currentUser, LocalDate date, Integer requestedClinicId) {
        Integer defaultClinicId = getReceptionistClinicId(currentUser);
        Integer targetClinicId = (requestedClinicId != null && requestedClinicId > 0) ? requestedClinicId : defaultClinicId;
        return appointmentRepo.findByClinicIdAndDate(targetClinicId, date).stream()
                .map(appointmentMapper::mapToAppointmentResponse).collect(Collectors.toList());
    }

    private void validateDoctorWorkingHours(Integer doctorId, Integer clinicId, Instant start, Instant end) {
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate bookingDate = start.atZone(zoneId).toLocalDate();
        java.time.LocalTime bookingStartTime = start.atZone(zoneId).toLocalTime();
        java.time.LocalTime bookingEndTime = end.atZone(zoneId).toLocalTime();

        List<DoctorSchedule> doctorSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                doctorId, clinicId, bookingDate
        );

        boolean isWithinWorkingHours = false;
        for (DoctorSchedule schedule : doctorSchedules) {
            if (!bookingStartTime.isBefore(schedule.getStartTime()) &&
                    !bookingEndTime.isAfter(schedule.getEndTime())) {
                isWithinWorkingHours = true;
                break;
            }
        }

        if (!isWithinWorkingHours) {
            throw new AppointmentConflictException("Thời gian (" + bookingStartTime + "-" + bookingEndTime + ") nằm ngoài ca làm việc của bác sĩ.");
        }
    }

    @Override
    @Transactional
    public AppointmentResponse createNewAppointment(CurrentUser currentUser, AppointmentRequest request) {
        User creator = userRepo.getReferenceById(currentUser.userId());
        Clinic clinic = clinicRepo.findById(request.getClinicId()).orElseThrow(() -> new ResourceNotFoundException("Clinic not found."));

        // 1. XỬ LÝ LOẠI LỊCH & BÁC SĨ
        String type = request.getAppointmentType() != null ? request.getAppointmentType() : "VIP";
        User doctor = null;

        if ("VIP".equalsIgnoreCase(type)) {
            if (request.getDoctorId() == null) {
                throw new AppointmentConflictException("Lịch đặt VIP yêu cầu phải chọn Bác sĩ.");
            }
            doctor = userRepo.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found."));
        } else {
            // Standard: Nếu có gửi ID thì tìm, không thì để null
            if (request.getDoctorId() != null) {
                doctor = userRepo.findById(request.getDoctorId()).orElse(null);
            }
        }

        Room room = (request.getRoomId() != null) ? roomRepo.findById(request.getRoomId()).orElseThrow(() -> new ResourceNotFoundException("Room not found.")) : null;

        // 2. XỬ LÝ BỆNH NHÂN
        Patient patient;
        if (request.getPatientId() != null && request.getPatientId() > 0) {
            patient = patientRepo.findById(request.getPatientId()).orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        } else {
            patient = patientRepo.findByUserId(currentUser.userId()).orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        }

        // 3. TÍNH TOÁN THỜI GIAN
        long totalDurationMinutes = request.getServices().stream()
                .mapToLong(req -> {
                    sunshine_dental_care.entities.ServiceVariant v = serviceVariantRepo.findById(req.getServiceId())
                            .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found: " + req.getServiceId()));
                    return (long) v.getDuration() * req.getQuantity();
                })
                .sum();

        Instant start = request.getStartDateTime();
        Instant end = start.plusSeconds(TimeUnit.MINUTES.toSeconds(totalDurationMinutes));

        // 4. KIỂM TRA XUNG ĐỘT (QUAN TRỌNG: CHỈ CHECK NẾU CÓ BÁC SĨ)

        if (doctor != null) {
            // 4a. Check giờ làm việc của bác sĩ đó
            validateDoctorWorkingHours(doctor.getId(), request.getClinicId(), start, end);

            // 4b. Check trùng lịch với bác sĩ đó
            Integer roomIdToCheck = (room != null) ? room.getId() : null;
            List<Appointment> conflicts = appointmentRepo.findConflictAppointments(doctor.getId(), roomIdToCheck, start, end);

            if (!conflicts.isEmpty()) {
                throw new AppointmentConflictException("Bác sĩ đã có lịch hẹn khác trong khoảng thời gian này.");
            }
        }
        // -----------------------

        // 5. TẠO APPOINTMENT
        Appointment appointment = new Appointment();
        appointment.setClinic(clinic);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor); // Có thể null (Standard)
        appointment.setRoom(room);
        appointment.setStartDateTime(start);
        appointment.setEndDateTime(end);
        appointment.setStatus(request.getStatus());
        appointment.setChannel(request.getChannel() != null ? request.getChannel() : "Walk-in");
        appointment.setNote(request.getNote());
        appointment.setCreatedBy(creator);


        // --- BỔ SUNG: Set Service Cha vào bảng Appointment (để Doctor View không bị lỗi) ---
        if (!request.getServices().isEmpty()) {
            Integer firstId = request.getServices().getFirst().getServiceId();

            var variantOpt = serviceVariantRepo.findById(firstId);

            if (variantOpt.isPresent()) {
                appointment.setService(variantOpt.get().getService());
            }
        }
        // Set Type & Fee
        appointment.setAppointmentType(type);
        appointment.setPaymentStatus("UNPAID");

        if (request.getBookingFee() != null) {
            appointment.setBookingFee(request.getBookingFee());
        } else {
            // 👇 LOGIC MỚI: Lấy giá từ Admin Setting
            if ("VIP".equalsIgnoreCase(type)) {
                appointment.setBookingFee(systemConfigService.getVipFee());
            } else {
                appointment.setBookingFee(systemConfigService.getStandardFee());
            }
        }

        appointment = appointmentRepo.save(appointment);

        // 6. LƯU DỊCH VỤ
        for (ServiceItemRequest req : request.getServices()) {
            sunshine_dental_care.entities.ServiceVariant v = serviceVariantRepo.findById(req.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found"));

            AppointmentService as = getAppointmentService(req, appointment, v);

            appointmentServiceRepo.save(as);
        }

        // Gửi notification APPOINTMENT_CREATED cho patient
        sendAppointmentCreatedNotification(appointment);

        return appointmentMapper.mapToAppointmentResponse(appointment);
    }

    private static AppointmentService getAppointmentService(ServiceItemRequest req, Appointment appointment, ServiceVariant v) {
        AppointmentService as = new AppointmentService();
        as.setAppointment(appointment);
        as.setService(v.getService()); // Lưu cha để tương thích
        as.setServiceVariant(v);
        as.setQuantity(req.getQuantity());
        as.setUnitPrice(v.getPrice());
        as.setDiscountPct(req.getDiscountPct());

        String note = req.getNote() != null ? req.getNote() : "";
        as.setNote(note + " [" + v.getVariantName() + "]"); // Lưu tên gói vào note
        return as;
    }

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(CurrentUser currentUser,
                                                     Integer appointmentId,
                                                     RescheduleRequest request) {

        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        // Bác sĩ hiện tại (có thể null nếu lịch đang ở hàng chờ)
        User originalDoctor = appointment.getDoctor();
        User targetDoctor   = originalDoctor;

        Integer newDoctorId = request.getNewDoctorId();

        // Nếu request có gửi newDoctorId
        if (newDoctorId != null) {
            // Nếu chưa có bác sĩ hoặc khác bác sĩ cũ thì load từ DB
            if (originalDoctor == null || !newDoctorId.equals(originalDoctor.getId())) {
                targetDoctor = userRepo.findById(newDoctorId)
                        .orElseThrow(() -> new ResourceNotFoundException("Target doctor not found"));
            }
        } else if (originalDoctor == null) {
            // Lịch đang hàng chờ mà không gửi bác sĩ mới -> request sai
            throw new IllegalArgumentException("newDoctorId is required for unassigned appointment");
        }

        // ===== Tính giờ mới =====
        Instant oldStart = appointment.getStartDateTime();
        Instant oldEnd   = appointment.getEndDateTime();
        long durationSeconds = oldEnd.getEpochSecond() - oldStart.getEpochSecond();

        Instant newStart = request.getNewStartDateTime();
        Instant newEnd   = newStart.plusSeconds(durationSeconds);

        // ===== Validate giờ làm việc bác sĩ mới =====
        validateDoctorWorkingHours(
                targetDoctor.getId(),
                appointment.getClinic().getId(),
                newStart,
                newEnd
        );

        // ===== Check conflict =====
        List<Appointment> conflicts = appointmentRepo.findConflictAppointments(
                targetDoctor.getId(),
                null,       // roomId tạm để null như bạn đang dùng
                newStart,
                newEnd
        );

        boolean hasRealConflict = conflicts.stream()
                .anyMatch(a -> !a.getId().equals(appointmentId));

        if (hasRealConflict) {
            throw new AppointmentConflictException("Khung giờ mới đã bị trùng lịch hẹn khác.");
        }

        // ===== Cập nhật bác sĩ & giờ mới =====
        appointment.setDoctor(targetDoctor);
        appointment.setStartDateTime(newStart);
        appointment.setEndDateTime(newEnd);

        // Nếu đổi bác sĩ thì reset room
        if (originalDoctor == null || !targetDoctor.getId().equals(originalDoctor.getId())) {
            appointment.setRoom(null);
        }

        // ===== Ghi log nếu có lý do =====
        if (request.getReason() != null) {
            Log actionLog = new Log();

            actionLog.setType("APPOINTMENT");
            actionLog.setTitle("Dời lịch hẹn");
            actionLog.setMessage("Dời lịch hẹn #" + appointment.getId() + ". Lý do: " + request.getReason());
            actionLog.setPriority("MEDIUM");
            actionLog.setUser(userRepo.getReferenceById(currentUser.userId()));
            actionLog.setClinic(appointment.getClinic());
            actionLog.setTableName("Appointments");
            actionLog.setRecordId(appointment.getId());
            actionLog.setAction("RESCHEDULE");
            actionLog.setAfterData("Reschedule Reason: " + request.getReason());

            if (actionLog.getCreatedAt() == null) actionLog.setCreatedAt(Instant.now());
            actionLog.setActionTime(Instant.now());
            logRepo.save(actionLog);
        }

        return appointmentMapper.mapToAppointmentResponse(appointmentRepo.save(appointment));
    }

    @Override
    public Page<PatientResponse> getPatients(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Patient> patientPage = patientRepo.searchPatients(keyword, pageRequest);

        // SỬ DỤNG HÀM MAPPER ĐÃ PUBLIC TỪ APPOINTMENT MAPPER
        return patientPage.map(appointmentMapper::mapPatientToPatientResponse);
    }

    @Override
    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        log.info("Creating new walk-in patient: {}", request.getFullName());

        // Chuẩn hóa dữ liệu đầu vào
        String fullName = request.getFullName() != null ? request.getFullName().trim() : null;
        String phone    = request.getPhone()    != null ? request.getPhone().trim()    : null;
        String rawEmail = request.getEmail();
        String email    = (rawEmail == null || rawEmail.isBlank())
                ? null
                : rawEmail.trim();

        // Nếu muốn thì set ngược lại vào request (cho nhất quán)
        request.setFullName(fullName);
        request.setPhone(phone);
        request.setEmail(email);

        // 1. Kiểm tra trùng SĐT
        if (userRepo.findByPhone(phone).isPresent()) {
            throw new AppointmentConflictException("Số điện thoại " + phone + " đã được đăng ký.");
        }

        // 2. Tạo User
        User newUser = new User();
        newUser.setFullName(fullName);
        newUser.setPhone(phone);
        newUser.setEmail(email); // email đã được chuẩn hóa: "" -> null

        // Username = Phone
        newUser.setUsername(phone);

        // Password = Phone (Mã hóa)
        newUser.setPasswordHash(passwordEncoder.encode(phone));

        newUser.setIsActive(true);
        newUser.setProvider("local");

        newUser = userRepo.save(newUser);

        // 3. Gán Role USER (ID = 6)
        Role roleUser = roleRepo.findById(6)
                .orElseThrow(() -> new ResourceNotFoundException("Role USER (ID=6) not found"));

        UserRole userRole = new UserRole();
        userRole.setUser(newUser);
        userRole.setRole(roleUser);
        userRole.setIsActive(true);
        userRole.setAssignedDate(Instant.now());

        userRoleRepo.save(userRole);

        // 4. Tạo Patient
        Patient newPatient = new Patient();
        newPatient.setUser(newUser);
        newPatient.setFullName(fullName);
        newPatient.setPhone(phone);
        newPatient.setGender(request.getGender());
        newPatient.setDateOfBirth(request.getDateOfBirth());
        newPatient.setAddress(request.getAddress());
        newPatient.setEmail(email); // dùng cùng email đã normalize
        newPatient.setIsActive(true);

        // Sinh mã Patient Code bằng Service
        String generatedCode = patientCodeService.nextPatientCode();
        newPatient.setPatientCode(generatedCode);

        newPatient = patientRepo.save(newPatient);
        log.info("Created patient profile: {} ({})", newPatient.getFullName(), generatedCode);

        // 5. Gửi Email Welcome
        if (email != null) {
            try {
                mailService.sendWelcomeEmail(newPatient, phone);
                log.info("Queued welcome email for patient: {}", email);
            } catch (Exception e) {
                log.error("Failed to send welcome email: {}", e.getMessage());
            }
        }

        return appointmentMapper.mapPatientToPatientResponse(newPatient);
    }

    // Hàm cập nhật lịch hẹn
    @Override
    @Transactional
    public AppointmentResponse updateAppointment(Integer appointmentId, AppointmentUpdateRequest request) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        String oldStatus = appointment.getStatus();
        String newStatus = request.getStatus();

        // Cập nhật trạng thái nếu có
        if (newStatus != null && !newStatus.isEmpty()) {
            appointment.setStatus(newStatus);
        }

        // Cập nhật ghi chú nếu có
        if (request.getNote() != null) {
            appointment.setNote(request.getNote());
        }

        Appointment savedAppointment = appointmentRepo.save(appointment);

        // Gửi thông báo cho patient khi reception xác nhận hoặc hủy lịch
        if (newStatus != null && !newStatus.equals(oldStatus)) {
            if ("CONFIRMED".equalsIgnoreCase(newStatus) || "CANCELLED".equalsIgnoreCase(newStatus)) {
                sendAppointmentStatusNotification(savedAppointment, newStatus);
            }
        }

        if (savedAppointment.getDoctor() != null) {
            Hibernate.initialize(savedAppointment.getDoctor());
        }
        if (savedAppointment.getPatient() != null) {
            Hibernate.initialize(savedAppointment.getPatient());
        }
        if (savedAppointment.getRoom() != null) {
            Hibernate.initialize(savedAppointment.getRoom());
        }

        return appointmentMapper.mapToAppointmentResponse(savedAppointment);
    }

    /**
     * Gửi notification APPOINTMENT_CREATED cho patient khi tạo lịch hẹn thành công
     */
    private void sendAppointmentCreatedNotification(Appointment appointment) {
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

            String message = String.format(
                "Bạn đã đặt lịch hẹn thành công tại %s với %s vào lúc %s. Lịch hẹn đang chờ xác nhận.",
                clinicName, doctorName, timeStr);

            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(patientUserId)
                    .type("APPOINTMENT_CREATED")
                    .priority("MEDIUM")
                    .title("Đặt lịch hẹn thành công")
                    .message(message)
                    .actionUrl("/appointments")
                    .relatedEntityType("APPOINTMENT")
                    .relatedEntityId(appointment.getId())
                    .build();

            notificationService.sendNotification(notiRequest);
            log.info("Sent APPOINTMENT_CREATED notification to patient {} for appointment {}",
                    patientUserId, appointment.getId());
        } catch (Exception e) {
            log.error("Failed to send APPOINTMENT_CREATED notification for appointment {}: {}",
                    appointment.getId(), e.getMessage(), e);
            // Không throw exception để không ảnh hưởng đến việc tạo appointment
        }
    }

    /**
     * Gửi thông báo cho patient khi reception xác nhận hoặc hủy lịch hẹn
     */
    private void sendAppointmentStatusNotification(Appointment appointment, String status) {
        if (appointment.getStartDateTime().isBefore(java.time.Instant.now())) {
            log.info("Skip sending notification for past appointment #{}", appointment.getId());
            return;
        }
        try {
            if (appointment.getPatient() == null || appointment.getPatient().getUser() == null) {
                log.warn("Cannot send notification: appointment {} has no patient user", appointment.getId());
                return;
            }

            Integer patientUserId = appointment.getPatient().getUser().getId();
            String clinicName = appointment.getClinic() != null ? appointment.getClinic().getClinicName() : "Phòng khám";
            
            // Format thời gian
            java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
            java.time.LocalDateTime startDateTime = appointment.getStartDateTime().atZone(zoneId).toLocalDateTime();
            String timeStr = startDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

            String title;
            String message;
            String notificationType;
            String priority = "MEDIUM";

            if ("CONFIRMED".equalsIgnoreCase(status)) {
                title = "Lịch hẹn đã được xác nhận";
                message = String.format(
                    "Lịch hẹn của bạn tại %s vào lúc %s đã được xác nhận. Vui lòng đến đúng giờ.",
                    clinicName, timeStr);
                notificationType = "APPOINTMENT_CONFIRMED";
            } else if ("CANCELLED".equalsIgnoreCase(status)) {
                title = "Lịch hẹn đã bị hủy";
                message = String.format(
                    "Lịch hẹn của bạn tại %s vào lúc %s đã bị hủy. Vui lòng liên hệ phòng khám để được hỗ trợ.",
                    clinicName, timeStr);
                notificationType = "APPOINTMENT_CANCELLED";
                priority = "HIGH";
            } else {
                return; // Không gửi notification cho các status khác
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

            notificationService.sendNotification(notiRequest);
            log.info("Sent {} notification to patient {} for appointment {}", 
                    notificationType, patientUserId, appointment.getId());
        } catch (Exception e) {
            log.error("Failed to send appointment status notification for appointment {}: {}", 
                    appointment.getId(), e.getMessage(), e);
            // Không throw exception để không ảnh hưởng đến việc update appointment
        }
    }

    // Hàm xếp phòng cho lịch hẹn
    @Override
    @Transactional
    public AppointmentResponse assignRoomToAppointment(Integer appointmentId, Integer roomId) {
        // 1. Lấy thông tin lịch hẹn
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        // 2. Lấy thông tin phòng
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        // 3. Kiểm tra phòng có thuộc đúng cơ sở của lịch hẹn không
        if (!room.getClinic().getId().equals(appt.getClinic().getId())) {
            throw new ValidationException("Lỗi: Phòng " + room.getRoomName() + " không thuộc cơ sở này!");
        }

        // 4. Double check (Chống xung đột) xem có ai vừa đặt phòng này không
        // (Hàm existsByRoomIdAndDateOverlap đã thêm ở Repo)
        boolean isOccupied = appointmentRepo.existsByRoomIdAndDateOverlap(
                roomId,
                appointmentId,
                appt.getStartDateTime(),
                appt.getEndDateTime()
        );

        if (isOccupied) {
            throw new AppointmentConflictException("Phòng " + room.getRoomName() + " vừa có người khác đặt. Vui lòng chọn phòng khác!");
        }

        // 5. Gán phòng và Lưu
        appt.setRoom(room);
        Appointment savedAppt = appointmentRepo.save(appt);

        // 6. Map dữ liệu vừa lưu sang DTO và trả về cho Controller
        return appointmentMapper.mapToAppointmentResponse(savedAppt);
    }

    // --- HELPER METHODS FOR BILLING ---
    private java.math.BigDecimal getDiscountRate(String rank) {
        if (rank == null) return java.math.BigDecimal.ZERO;
        return switch (rank.toUpperCase()) {
            case "DIAMOND" -> new java.math.BigDecimal("0.15"); // 15%
            case "GOLD" -> new java.math.BigDecimal("0.10"); // 10%
            case "SILVER" -> new java.math.BigDecimal("0.05"); // 5%
            default -> java.math.BigDecimal.ZERO;
        };
    }

    private String calculateNewRank(java.math.BigDecimal totalSpent) {
        double amount = totalSpent.doubleValue();
        if (amount >= 100_000_000) return "DIAMOND";
        if (amount >= 30_000_000)  return "GOLD";
        if (amount >= 10_000_000)  return "SILVER";
        return "MEMBER";
    }

    @Override
    @Transactional(readOnly = true)
    public BillInvoiceDTO getBillDetails(Integer appointmentId) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn: " + appointmentId));

        // 1. Tính tổng tiền Dịch vụ (SubTotal)
        List<AppointmentService> usedServices = appointmentServiceRepo.findByAppointmentId(appointmentId);
        java.math.BigDecimal subTotal = java.math.BigDecimal.ZERO;
        List<BillInvoiceDTO.BillServiceItem> billItems = new java.util.ArrayList<>();

        for (AppointmentService as : usedServices) {
            java.math.BigDecimal lineTotal = as.getUnitPrice().multiply(new java.math.BigDecimal(as.getQuantity()));
            subTotal = subTotal.add(lineTotal);

            billItems.add(BillInvoiceDTO.BillServiceItem.builder()
                    .serviceName(as.getServiceVariant().getVariantName())
                    .quantity(as.getQuantity())
                    .unitPrice(as.getUnitPrice())
                    .total(lineTotal)
                    .build());
        }

        // 2. Tính Giảm Giá theo Rank
        String currentRank = appt.getPatient().getMembershipRank();
        java.math.BigDecimal discountPercent = getDiscountRate(currentRank);
        java.math.BigDecimal discountAmount = subTotal.multiply(discountPercent);

        // 3. Phí Cọc (Booking Fee) - Không giảm
        java.math.BigDecimal bookingFee = appt.getBookingFee() != null ? appt.getBookingFee() : java.math.BigDecimal.ZERO;

        // 4. Tổng cuối (Total Amount) = (Dịch vụ - Giảm giá) + Cọc
        java.math.BigDecimal grandTotal = subTotal.subtract(discountAmount).add(bookingFee);

        // 5. Số tiền còn thiếu
        boolean isDepositPaid = "PAID".equalsIgnoreCase(appt.getPaymentStatus()) || appt.getTransactionRef() != null;
        java.math.BigDecimal totalPaid = isDepositPaid ? bookingFee : java.math.BigDecimal.ZERO;
        java.math.BigDecimal remaining = grandTotal.subtract(totalPaid);

        return BillInvoiceDTO.builder()
                .clinicName(appt.getClinic().getClinicName())
                .clinicAddress(appt.getClinic().getAddress())
                .invoiceId("INV-" + String.format("%06d", appt.getId()))
                .createdDate(java.time.LocalDateTime.now())
                .patientName(appt.getPatient().getFullName())
                .patientPhone(appt.getPatient().getPhone())
                .patientCode(appt.getPatient().getPatientCode())
                .membershipRank(currentRank != null ? currentRank : "MEMBER")
                .appointmentType(appt.getAppointmentType())
                .bookingFee(bookingFee)
                .isBookingFeePaid(isDepositPaid)
                .services(billItems)
                // Các số liệu tài chính quan trọng
                .subTotal(subTotal)
                .discountAmount(discountAmount)
                .totalAmount(grandTotal)
                .totalPaid(totalPaid)
                .remainingBalance(remaining)
                .build();
    }

    @Override
    @Transactional
    public void confirmPayment(CurrentUser currentUser, Integer appointmentId) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));

        // 1. TÍNH TOÁN LẠI (Re-calculate để đảm bảo chính xác)
        List<AppointmentService> usedServices = appointmentServiceRepo.findByAppointmentId(appointmentId);
        java.math.BigDecimal subTotal = java.math.BigDecimal.ZERO;
        for (AppointmentService as : usedServices) {
            java.math.BigDecimal lineTotal = as.getUnitPrice().multiply(new java.math.BigDecimal(as.getQuantity()));
            subTotal = subTotal.add(lineTotal);
        }

        String currentRank = appt.getPatient().getMembershipRank();
        java.math.BigDecimal discountPercent = getDiscountRate(currentRank);
        java.math.BigDecimal discountAmount = subTotal.multiply(discountPercent);

        java.math.BigDecimal bookingFee = appt.getBookingFee() != null ? appt.getBookingFee() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal finalTotal = subTotal.subtract(discountAmount).add(bookingFee);

        // 2. LƯU THÔNG TIN VÀO APPOINTMENT
        appt.setSubTotal(subTotal);
        appt.setDiscountAmount(discountAmount);
        appt.setTotalAmount(finalTotal);

        appt.setPaymentStatus("PAID");
        appt.setStatus("COMPLETED");

        appointmentRepo.save(appt);

        // 3. TÍCH ĐIỂM & CẬP NHẬT RANK CHO BỆNH NHÂN
        Patient patient = appt.getPatient();
        java.math.BigDecimal currentSpending = patient.getAccumulatedSpending() != null
                ? patient.getAccumulatedSpending()
                : java.math.BigDecimal.ZERO;

        java.math.BigDecimal newSpending = currentSpending.add(finalTotal);
        patient.setAccumulatedSpending(newSpending);

        String newRank = calculateNewRank(newSpending);
        if (!newRank.equals(patient.getMembershipRank())) {
            patient.setMembershipRank(newRank);
            log.info("Khách hàng {} đã thăng hạng lên {}", patient.getFullName(), newRank);
        }
        patientRepo.save(patient);

        // --- 4. GHI LOG ---
        try {
            Log paymentLog = new Log();
            paymentLog.setType("PAYMENT");
            paymentLog.setPriority("MEDIUM");
            paymentLog.setTitle("Xác nhận thanh toán");

            String msg = "Xác nhận thanh toán lịch hẹn #" + appointmentId
                    + ". Tổng tiền: " + finalTotal
                    + ". Rank mới: " + newRank;
            paymentLog.setMessage(msg);

            // 2. CÁC TRƯỜNG BỔ SUNG
            paymentLog.setAction("CONFIRM_PAYMENT");
            paymentLog.setRecordId(appointmentId);
            paymentLog.setTableName("Appointments");
            paymentLog.setAfterData("Paid: " + finalTotal + ", Rank: " + newRank);

            // Set user từ CurrentUser (bắt buộc vì userId không được null)
            User user = userRepo.getReferenceById(currentUser.userId());
            paymentLog.setUser(user);
            
            // Set clinic từ appointment
            if (appt.getClinic() != null) {
                paymentLog.setClinic(appt.getClinic());
            }

            // Nếu Entity Log chưa có @PrePersist cho createdAt thì set tay:
            if (paymentLog.getCreatedAt() == null) {
                paymentLog.setCreatedAt(java.time.Instant.now());
            }
            // Thêm actionTime
            paymentLog.setActionTime(java.time.Instant.now());

            logRepo.save(paymentLog);

        } catch (Exception e) {
            // In lỗi ra nhưng không throw exception để tránh rollback giao dịch thanh toán chính
            System.err.println("Lỗi ghi log (không ảnh hưởng thanh toán): " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public Page<AppointmentResponse> getAppointmentList(CurrentUser currentUser, String keyword, String paymentStatus, String status, LocalDate date, int page, int size) {
        // 1. Lấy Clinic ID của Lễ tân đang đăng nhập
        Integer clinicId = getReceptionistClinicId(currentUser);

        // 2. Tạo PageRequest (Sắp xếp mới nhất lên đầu)
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("startDateTime").descending());

        // 3. Gọi Repo
        Page<Appointment> appointmentPage = appointmentRepo.searchAppointments(
                clinicId,
                keyword,
                paymentStatus,
                status,
                date,
                pageRequest
        );

        // 4. Map sang DTO (Dùng Mapper có sẵn)
        return appointmentPage.map(appointmentMapper::mapToAppointmentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse getPatientDetail(Integer id) {
        Patient patient = patientRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân ID: " + id));
        return appointmentMapper.mapPatientToPatientResponse(patient);
    }

    @Override
    @Transactional
    public PatientResponse updatePatient(Integer id, PatientResponse request) {
        Patient patient = patientRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân"));

        // Cập nhật thông tin cơ bản
        if (request.getFullName() != null) patient.setFullName(request.getFullName());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());

        patient.setEmail(request.getEmail());

        Patient saved = patientRepo.save(patient);
        return appointmentMapper.mapPatientToPatientResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientHistoryDTO> getPatientHistory(Integer patientId) {
        // 1. Tìm tất cả lịch hẹn của bệnh nhân
        List<Appointment> appointments = appointmentRepo.findByPatientId(patientId);

        // 2. Map sang DTO
        return appointments.stream()
                .map(app -> {
                    // A. Lấy tên Bác sĩ
                    String doctorName = (app.getDoctor() != null) ? app.getDoctor().getFullName() : "Chưa chỉ định";

                    // B. Lấy danh sách tên dịch vụ chi tiết (Variant Name)
                    String servicesStr = "";
                    if (app.getAppointmentServices() != null && !app.getAppointmentServices().isEmpty()) {
                        servicesStr = app.getAppointmentServices().stream()
                                .map(as -> {
                                    // Ưu tiên lấy tên Variant (Chi tiết) trước
                                    if (as.getServiceVariant() != null) {
                                        return as.getServiceVariant().getVariantName();
                                    }
                                    // Fallback về Service chung
                                    return as.getService().getServiceName();
                                })
                                .collect(Collectors.joining(", "));
                    }

                    // C. Lấy tổng tiền (Ưu tiên cột totalAmount trong Appointment)
                    java.math.BigDecimal finalTotal = app.getTotalAmount() != null ? app.getTotalAmount() : java.math.BigDecimal.ZERO;

                    // D. Build DTO
                    return PatientHistoryDTO.builder()
                            .appointmentId(app.getId())
                            .visitDate(app.getStartDateTime())
                            .doctorName(doctorName)
                            .diagnosis(app.getNote()) // Tạm dùng Note làm diagnosis
                            .serviceNames(servicesStr)
                            .totalAmount(finalTotal)
                            .status(app.getStatus())
                            .build();
                })
                // 3. Sắp xếp: Mới nhất lên đầu
                .sorted(java.util.Comparator.comparing(PatientHistoryDTO::getVisitDate).reversed())
                .collect(Collectors.toList());
    }
}