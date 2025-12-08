package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.ServiceVariant;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.interfaces.reception.BookingService;
import sunshine_dental_care.services.auth_service.MailService; // <--- 1. Import mới

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
    private final MailService mailService; // <--- 2. Inject MailService

    private static final List<LocalTime> FIXED_SLOTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0),
            LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(18, 0)
    );

    @Override
    public List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request) {
        // ... (Giữ nguyên toàn bộ logic cũ của bạn ở đây) ...
        // ... Code cũ ...
        List<TimeSlotResponse> responseSlots = new ArrayList<>();
        List<Integer> variantIds = request.getServiceIds();
        if (variantIds == null || variantIds.isEmpty()) return generateAllBusySlots();
        List<ServiceVariant> selectedVariants = serviceVariantRepo.findAllById(variantIds);
        int totalMinutes = selectedVariants.stream().mapToInt(v -> v.getDuration() != null ? v.getDuration() : 60).sum();
        int durationMinutes = Math.max(60, totalMinutes);
        log.info("Calculating Slots for Doctor {}. Total Duration: {} mins", request.getDoctorId(), durationMinutes);
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

    // ... Helper methods cũ giữ nguyên ...
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

    // =========================================================
    // 3. THÊM HÀM NÀY ĐỂ GỌI KHI ĐẶT LỊCH THÀNH CÔNG
    // =========================================================
    /**
     * Hàm này nên được gọi ngay sau khi appointmentRepo.save(appt) ở nơi xử lý đặt lịch.
     * Vì file này chưa có hàm createBooking, mình viết hàm public này để bạn gọi từ Controller hoặc nơi khác.
     */
    public void notifyBookingSuccess(Appointment appt) {
        try {
            if (appt.getPatient() != null && appt.getPatient().getUser() != null) {
                // Prepare data
                String timeStr = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

                String serviceName = "Dịch vụ nha khoa";
                if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
                    AppointmentService as = appt.getAppointmentServices().get(0);
                    if (as.getService() != null) serviceName = as.getService().getServiceName();
                }

                String address = (appt.getClinic() != null) ? appt.getClinic().getAddress() : "Phòng khám";

                // Send Mail
                mailService.sendBookingSuccessEmail(
                        appt.getPatient().getUser(),
                        appt,
                        timeStr,
                        serviceName,
                        address
                );
                log.info(">>> Sent Booking Success Email for Appt ID: {}", appt.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send booking success email: {}", e.getMessage());
            // Không throw exception để tránh rollback giao dịch đặt lịch
        }
    }
}