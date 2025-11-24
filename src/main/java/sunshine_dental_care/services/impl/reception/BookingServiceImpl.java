package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.ServiceRepo;
import sunshine_dental_care.services.interfaces.reception.BookingService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final ServiceRepo serviceRepo;

    private static final List<LocalTime> FIXED_SLOTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            // Nghỉ trưa 12:00 - 13:00 (Không có slot)
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0),
            LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(18, 0)
    );

    @Override
    public List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request) {
        List<TimeSlotResponse> responseSlots = new ArrayList<>();

        // 1. TÍNH TỔNG THỜI GIAN TỪ DANH SÁCH SERVICE IDs
        List<Integer> serviceIds = request.getServiceIds();
        if (serviceIds == null || serviceIds.isEmpty()) {
            // Fallback nếu FE gửi sai
            return generateAllBusySlots();
        }

        List<sunshine_dental_care.entities.Service> selectedServices = serviceRepo.findAllById(serviceIds);

        int totalMinutes = selectedServices.stream()
                .mapToInt(s -> s.getDefaultDuration() != null ? s.getDefaultDuration() : 60)
                .sum();

        // Thời lượng booking (ít nhất là 60 phút để khớp slot)
        // Nếu tổng 30p -> Làm tròn lên 60p. Nếu 90p -> Giữ 90p.
        int durationMinutes = Math.max(60, totalMinutes);

        log.info("Calculating Slots for Doctor {}. Total Duration: {} mins", request.getDoctorId(), durationMinutes);

        // 2. Lấy lịch làm việc tại Clinic
        List<DoctorSchedule> validSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                request.getDoctorId(),
                request.getClinicId(),
                request.getDate()
        );

        if (validSchedules.isEmpty()) {
            return generateAllBusySlots();
        }

        // 3. Lấy lịch BẬN (bao gồm cả PENDING)
        List<Appointment> bookedApps = appointmentRepo.findBusySlotsByDoctorAndDate(
                request.getDoctorId(),
                request.getDate()
        );

        // 4. Duyệt qua các slot cứng
        for (LocalTime slotStart : FIXED_SLOTS) {
            // Tính giờ kết thúc dự kiến dựa trên TỔNG THỜI GIAN
            LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);

            // *Lưu ý: Nếu slotEnd vượt quá 24h (qua ngày hôm sau) thì bỏ qua
            if (slotEnd.isBefore(slotStart)) continue;

            boolean isAvailable = false;

            // CHECK 1: Nằm trong ca làm việc
            for (DoctorSchedule sch : validSchedules) {
                // Phải nằm trọn vẹn trong ca
                if (!slotStart.isBefore(sch.getStartTime()) && !slotEnd.isAfter(sch.getEndTime())) {
                    isAvailable = true;
                    break;
                }
            }

            // CHECK 2: Va chạm lịch khác
            if (isAvailable) {
                if (isTimeOverlap(slotStart, slotEnd, bookedApps)) {
                    isAvailable = false;
                }
            }

            // CHECK 3: Quá khứ
            if (request.getDate().equals(java.time.LocalDate.now())) {
                if (slotStart.isBefore(LocalTime.now())) {
                    isAvailable = false;
                }
            }

            responseSlots.add(new TimeSlotResponse(slotStart.toString(), isAvailable));
        }

        return responseSlots;
    }

    /**
     * Helper: Kiểm tra xem khoảng [start, end] có trùng với bất kỳ lịch hẹn nào không
     */
    private boolean isTimeOverlap(LocalTime start, LocalTime end, List<Appointment> existingAppointments) {
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh"); // Fix cứng Zone VN

        for (Appointment app : existingAppointments) {
            LocalTime appStart = app.getStartDateTime().atZone(zoneId).toLocalTime();
            LocalTime appEnd = app.getEndDateTime().atZone(zoneId).toLocalTime();

            // Logic va chạm: (Start A < End B) và (Start B < End A)
            // [08:00 - 09:30] vs [09:00 - 10:00]
            // 08:00 < 10:00 (True) && 09:00 < 09:30 (True) -> TRUE (Va chạm)
            if (start.isBefore(appEnd) && appStart.isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private List<TimeSlotResponse> generateAllBusySlots() {
        List<TimeSlotResponse> slots = new ArrayList<>();
        for (LocalTime time : FIXED_SLOTS) {
            slots.add(new TimeSlotResponse(time.toString(), false));
        }
        return slots;
    }
}

