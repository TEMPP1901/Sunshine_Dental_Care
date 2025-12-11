package sunshine_dental_care.services.impl.reception;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service; // Annotation của Spring
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.*;
import sunshine_dental_care.dto.receptionDTO.mapper.AppointmentMapper;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AccessDeniedException;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;

import sunshine_dental_care.dto.receptionDTO.mapper.DoctorScheduleMapper;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
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
import sunshine_dental_care.services.interfaces.reception.ReceptionService;

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
        // --- ĐÃ SỬA ĐOẠN NÀY ---
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
        if (request.getBookingFee() != null) {
            appointment.setBookingFee(request.getBookingFee());
        } else {
            // Fallback giá mặc định
            appointment.setBookingFee("VIP".equalsIgnoreCase(type) ?
                    new java.math.BigDecimal("1000000") : new java.math.BigDecimal("500000"));
        }

        appointment = appointmentRepo.save(appointment);

        // 6. LƯU DỊCH VỤ
        for (ServiceItemRequest req : request.getServices()) {
            sunshine_dental_care.entities.ServiceVariant v = serviceVariantRepo.findById(req.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found"));

            AppointmentService as = getAppointmentService(req, appointment, v);

            appointmentServiceRepo.save(as);
        }
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

            actionLog.setUser(userRepo.getReferenceById(currentUser.userId()));
            actionLog.setClinic(appointment.getClinic());
            actionLog.setTableName("Appointments");
            actionLog.setRecordId(appointment.getId());
            actionLog.setAction("RESCHEDULE");
            actionLog.setAfterData("Reschedule Reason: " + request.getReason());

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

    @Override
    public AppointmentResponse updateAppointment(Integer appointmentId, AppointmentUpdateRequest request) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        // Cập nhật trạng thái nếu có
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            appointment.setStatus(request.getStatus());
        }

        // Cập nhật ghi chú nếu có
        if (request.getNote() != null) {
            appointment.setNote(request.getNote());
        }

        // (Optional) Nếu muốn log lại lịch sử sửa
        // logRepo.save(new Log(..., "UPDATE_INFO", ...));

        return appointmentMapper.mapToAppointmentResponse(appointmentRepo.save(appointment));
    }
}