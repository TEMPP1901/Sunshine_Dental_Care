package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.ServiceItemRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.SessionAvailabilityResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.doctor.DoctorRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.interfaces.reception.BookingService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    // --- Repositories Chung ---
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final ServiceVariantRepo serviceVariantRepo;

    // --- Repositories Của Tuấn (Cho createAppointment & Mail) ---
    private final ClinicRepo clinicRepo;
    private final PatientRepo patientRepo;
    private final DoctorRepo doctorRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;
    private final MailService mailService;

    // Các khung giờ cố định
    private static final List<LocalTime> FIXED_SLOTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            // Nghỉ trưa 12:00 - 13:00
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0),
            LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(18, 0)
    );

    // ========================================================================
    // 1. HÀM GET AVAILABLE SLOTS (LẤY GIỜ TRỐNG) - MERGED
    // ========================================================================
    @Override
    public List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request) {
        List<TimeSlotResponse> responseSlots = new ArrayList<>();

        // 1. Tính tổng thời gian
        List<Integer> variantIds = request.getServiceIds();
        if (variantIds == null || variantIds.isEmpty()) return generateAllBusySlots();

        List<ServiceVariant> selectedVariants = serviceVariantRepo.findAllById(variantIds);
        int totalMinutes = selectedVariants.stream().mapToInt(v -> v.getDuration() != null ? v.getDuration() : 60).sum();
        int durationMinutes = Math.max(60, totalMinutes);

        log.info("Calculating Slots for Doctor {}. Total Duration: {} mins", request.getDoctorId(), durationMinutes);

        // 2. Lấy lịch làm việc
        List<DoctorSchedule> validSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                request.getDoctorId(), request.getClinicId(), request.getDate()
        );

        if (validSchedules.isEmpty()) return generateAllBusySlots();

        // 3. Lấy lịch đã đặt (Bận)
        List<Appointment> bookedApps = appointmentRepo.findBusySlotsByDoctorAndDate(request.getDoctorId(), request.getDate());

        // 4. Duyệt Slot
        for (LocalTime slotStart : FIXED_SLOTS) {
            LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
            if (slotEnd.isBefore(slotStart)) continue; // Qua ngày hôm sau

            boolean isAvailable = false;

            // Check nằm trong ca làm việc
            for (DoctorSchedule sch : validSchedules) {
                if (!slotStart.isBefore(sch.getStartTime()) && !slotEnd.isAfter(sch.getEndTime())) {
                    isAvailable = true;
                    break;
                }
            }

            // Check trùng lịch hẹn khác
            if (isAvailable) {
                if (isTimeOverlap(slotStart, slotEnd, bookedApps)) isAvailable = false;
            }

            // Check quá khứ (Logic của Tuấn)
            if (request.getDate().equals(LocalDate.now())) {
                if (slotStart.isBefore(LocalTime.now())) isAvailable = false;
            }

            responseSlots.add(new TimeSlotResponse(slotStart.toString(), isAvailable));
        }
        return responseSlots;
    }

    // ========================================================================
    // 2. HÀM CREATE APPOINTMENT (TẠO LỊCH) - CỦA TUẤN
    // ========================================================================
    @Override
    @Transactional
    public Appointment createAppointment(AppointmentRequest request) {
        log.info("Creating appointment for Patient ID: {}", request.getPatientId());

        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        Patient patient = patientRepo.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User doctor = null;
        if (request.getDoctorId() != null) {
            doctor = doctorRepo.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        }

        Appointment appointment = new Appointment();
        appointment.setClinic(clinic);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStartDateTime(request.getStartDateTime());

        if (request.getEndDateTime() != null) {
            appointment.setEndDateTime(request.getEndDateTime());
        } else {
            appointment.setEndDateTime(request.getStartDateTime().plusSeconds(3600)); // Mặc định 1 tiếng
        }

        appointment.setStatus("PENDING");
        appointment.setChannel(request.getChannel() != null ? request.getChannel() : "WEB_BOOKING");
        appointment.setNote(request.getNote());
        appointment.setBookingFee(request.getBookingFee() != null ? request.getBookingFee() : BigDecimal.ZERO);
        appointment.setAppointmentType(request.getAppointmentType() != null ? request.getAppointmentType() : "STANDARD");

        Appointment savedAppointment = appointmentRepo.save(appointment);

        // Lưu Services đi kèm
        if (request.getServices() != null && !request.getServices().isEmpty()) {
            List<AppointmentService> appointmentServices = new ArrayList<>();
            for (ServiceItemRequest item : request.getServices()) {
                ServiceVariant variant = serviceVariantRepo.findById(item.getServiceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found"));

                AppointmentService as = new AppointmentService();
                as.setAppointment(savedAppointment);
                as.setServiceVariant(variant);
                as.setService(variant.getService());
                as.setQuantity(item.getQuantity() > 0 ? item.getQuantity() : 1);
                as.setUnitPrice(variant.getPrice());

                appointmentServices.add(as);
            }
            appointmentServiceRepo.saveAll(appointmentServices);
            savedAppointment.setAppointmentServices(appointmentServices);
        }

        // Gửi Mail (Logic của Tuấn)
        notifyBookingSuccess(savedAppointment);
        return savedAppointment;
    }

    // ========================================================================
    // 3. HÀM CHECK SESSION AVAILABILITY - CỦA LONG (MỚI)
    // ========================================================================
    @Override
    public SessionAvailabilityResponse checkSessionAvailability(Integer clinicId, LocalDate date) {
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return new SessionAvailabilityResponse(false, false, "Phòng khám nghỉ Chủ Nhật");
        }
        if (date.isBefore(LocalDate.now())) {
            return new SessionAvailabilityResponse(false, false, "Không thể chọn ngày quá khứ");
        }

        // Lấy lịch làm việc của TẤT CẢ bác sĩ tại Clinic ngày hôm đó
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByClinicAndDate(clinicId, date);

        if (schedules.isEmpty()) {
            return new SessionAvailabilityResponse(false, false, "Chưa có lịch làm việc cho ngày này");
        }

        boolean hasMorning = false;
        boolean hasAfternoon = false;

        // Quy ước: Sáng (08:00 - 12:00), Chiều (13:00 - 17:00)
        LocalTime morningEnd = LocalTime.of(12, 0);
        LocalTime afternoonStart = LocalTime.of(13, 0);

        for (DoctorSchedule s : schedules) {
            // Check Sáng
            if (s.getStartTime().isBefore(morningEnd)) {
                hasMorning = true;
            }
            // Check Chiều
            if (s.getEndTime().isAfter(afternoonStart)) {
                hasAfternoon = true;
            }
        }
        return new SessionAvailabilityResponse(hasMorning, hasAfternoon, null);
    }

    // ========================================================================
    // 4. HELPER METHODS
    // ========================================================================

    @Override
    public void notifyBookingSuccess(Appointment appt) {
        try {
            if (appt.getPatient() != null && appt.getPatient().getUser() != null) {
                String timeStr = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                String serviceName = "Dịch vụ nha khoa";
                if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
                    AppointmentService as = appt.getAppointmentServices().get(0);
                    if (as.getService() != null) serviceName = as.getService().getServiceName();
                }
                String address = (appt.getClinic() != null) ? appt.getClinic().getAddress() : "Phòng khám";

                mailService.sendBookingSuccessEmail(
                        appt.getPatient().getUser(),
                        appt,
                        timeStr,
                        serviceName,
                        address
                );
            }
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }

    private boolean isTimeOverlap(LocalTime start, LocalTime end, List<Appointment> existingAppointments) {
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        for (Appointment app : existingAppointments) {
            LocalTime appStart = app.getStartDateTime().atZone(zoneId).toLocalTime();
            LocalTime appEnd = app.getEndDateTime().atZone(zoneId).toLocalTime();
            if (start.isBefore(appEnd) && appStart.isBefore(end)) return true;
        }
        return false;
    }

    private List<TimeSlotResponse> generateAllBusySlots() {
        List<TimeSlotResponse> slots = new ArrayList<>();
        for (LocalTime time : FIXED_SLOTS) slots.add(new TimeSlotResponse(time.toString(), false));
        return slots;
    }
}