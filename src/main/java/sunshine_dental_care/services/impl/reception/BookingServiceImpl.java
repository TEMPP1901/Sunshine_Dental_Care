package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.ServiceItemRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
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

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final ServiceVariantRepo serviceVariantRepo;
    private final MailService mailService;

    // --- Repos bổ sung ---
    private final ClinicRepo clinicRepo;
    private final PatientRepo patientRepo;
    private final DoctorRepo doctorRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;

    private static final List<LocalTime> FIXED_SLOTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0),
            LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(18, 0)
    );

    @Override
    public List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request) {
        List<TimeSlotResponse> responseSlots = new ArrayList<>();
        List<Integer> variantIds = request.getServiceIds();
        if (variantIds == null || variantIds.isEmpty()) return generateAllBusySlots();

        List<ServiceVariant> selectedVariants = serviceVariantRepo.findAllById(variantIds);
        int totalMinutes = selectedVariants.stream().mapToInt(v -> v.getDuration() != null ? v.getDuration() : 60).sum();
        int durationMinutes = Math.max(60, totalMinutes);

        // Nếu là Standard (doctorId null), ta vẫn check lịch chung (hoặc trả về full slot nếu muốn)
        // Nhưng logic check slot này chủ yếu dùng cho VIP.
        if (request.getDoctorId() == null) {
            // Logic tạm cho standard: Coi như lúc nào cũng available (hoặc logic khác tùy bạn)
            // Ở đây giữ nguyên logic cũ, nếu null nó sẽ tìm theo điều kiện khác hoặc trả về busy/free tùy db
        }

        List<DoctorSchedule> validSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(request.getDoctorId(), request.getClinicId(), request.getDate());
        if (validSchedules.isEmpty()) return generateAllBusySlots();

        List<Appointment> bookedApps = appointmentRepo.findBusySlotsByDoctorAndDate(request.getDoctorId(), request.getDate());
        for (LocalTime slotStart : FIXED_SLOTS) {
            LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
            if (slotEnd.isBefore(slotStart)) continue;
            boolean isAvailable = false;
            for (DoctorSchedule sch : validSchedules) {
                if (!slotStart.isBefore(sch.getStartTime()) && !slotEnd.isAfter(sch.getEndTime())) {
                    isAvailable = true;
                    break;
                }
            }
            if (isAvailable) {
                if (isTimeOverlap(slotStart, slotEnd, bookedApps)) isAvailable = false;
            }
            if (request.getDate().equals(java.time.LocalDate.now())) {
                if (slotStart.isBefore(LocalTime.now())) isAvailable = false;
            }
            responseSlots.add(new TimeSlotResponse(slotStart.toString(), isAvailable));
        }
        return responseSlots;
    }

    @Override
    @Transactional
    public Appointment createAppointment(AppointmentRequest request) {
        log.info("Creating appointment for Patient ID: {}", request.getPatientId());

        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found"));

        Patient patient = patientRepo.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User doctor = null;

        // --- LOGIC CHỌN BÁC SĨ (ĐƠN GIẢN HÓA) ---
        if (request.getDoctorId() != null) {
            // TRƯỜNG HỢP 1: KHÁM VIP (Có chọn bác sĩ)
            doctor = doctorRepo.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        }
        // TRƯỜNG HỢP 2: KHÁM TIÊU CHUẨN -> doctor = null (Để Reception xếp sau)
        // Vì DB đã cho phép NULL nên không cần Auto-Assign nữa.
        // ----------------------------------------

        Appointment appointment = new Appointment();
        appointment.setClinic(clinic);
        appointment.setPatient(patient);

        // Lưu Doctor (có thể là null)
        appointment.setDoctor(doctor);

        appointment.setStartDateTime(request.getStartDateTime());

        if (request.getEndDateTime() != null) {
            appointment.setEndDateTime(request.getEndDateTime());
        } else {
            appointment.setEndDateTime(request.getStartDateTime().plusSeconds(3600));
        }

        appointment.setStatus("PENDING");
        appointment.setChannel(request.getChannel() != null ? request.getChannel() : "WEB_BOOKING");
        appointment.setNote(request.getNote());

        if (request.getBookingFee() != null) {
            appointment.setBookingFee(request.getBookingFee());
        } else {
            appointment.setBookingFee(BigDecimal.ZERO);
        }

        if (request.getAppointmentType() != null) {
            appointment.setAppointmentType(request.getAppointmentType());
        } else {
            appointment.setAppointmentType("STANDARD");
        }

        Appointment savedAppointment = appointmentRepo.save(appointment);

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

        notifyBookingSuccess(savedAppointment);
        return savedAppointment;
    }

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