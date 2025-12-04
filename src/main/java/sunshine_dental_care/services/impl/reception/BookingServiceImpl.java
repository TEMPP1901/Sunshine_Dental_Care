package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.SessionAvailabilityResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.ServiceVariant;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.interfaces.reception.BookingService;

import java.time.LocalDate;
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
    private final ServiceVariantRepo serviceVariantRepo;

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

        // 1. TÍNH TỔNG THỜI GIAN TỪ variantsId
        List<Integer> variantIds = request.getServiceIds();
        if (variantIds == null || variantIds.isEmpty()) {
            return generateAllBusySlots();
        }

        // Tìm các Variants theo ID gửi lên
        List<ServiceVariant> selectedVariants = serviceVariantRepo.findAllById(variantIds);

        // Cộng tổng duration từ các Variant
        int totalMinutes = selectedVariants.stream()
                .mapToInt(v -> v.getDuration() != null ? v.getDuration() : 60)
                .sum();

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
                /* code dài dòng hơn nếu ko dùng phủ định !
                (slotStart.isAfter(schStart) || slotStart.equals(schStart))
                &&
                (slotEnd.isBefore(schEnd) || slotEnd.equals(schEnd))*/

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

    @Override
    public SessionAvailabilityResponse checkSessionAvailability(Integer clinicId, LocalDate date) {
        // 1. Check Chủ Nhật (Nếu phòng khám nghỉ CN)
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return new SessionAvailabilityResponse(false, false, "Phòng khám nghỉ Chủ Nhật");
        }

        // 2. Check Quá khứ
        if (date.isBefore(LocalDate.now())) {
            return new SessionAvailabilityResponse(false, false, "Không thể chọn ngày quá khứ");
        }

        // 3. Lấy lịch làm việc của TẤT CẢ bác sĩ tại Clinic ngày hôm đó
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByClinicAndDate(clinicId, date);

        if (schedules.isEmpty()) {
            return new SessionAvailabilityResponse(false, false, "Chưa có lịch làm việc cho ngày này");
        }

        boolean hasMorning = false;
        boolean hasAfternoon = false;

        // 4. Quét xem có ai làm Sáng/Chiều không
        // Quy ước: Sáng (08:00 - 12:00), Chiều (13:00 - 17:00)
        LocalTime morningStart = LocalTime.of(8, 0);
        LocalTime morningEnd = LocalTime.of(12, 0);
        LocalTime afternoonStart = LocalTime.of(13, 0);
        LocalTime afternoonEnd = LocalTime.of(17, 0);

        for (DoctorSchedule s : schedules) {
            // Logic giao thoa: StartCa < EndBuoi && EndCa > StartBuoi
            if (s.getStartTime().isBefore(morningEnd) && s.getEndTime().isAfter(morningStart)) {
                hasMorning = true;
            }
            if (s.getStartTime().isBefore(afternoonEnd) && s.getEndTime().isAfter(afternoonStart)) {
                hasAfternoon = true;
            }
        }
        return new SessionAvailabilityResponse(hasMorning, hasAfternoon, null);
    }

    /**
     * Helper: Kiểm tra xem khoảng [start, end] có trùng với bất kỳ lịch hẹn nào không
     */
    private boolean isTimeOverlap(LocalTime start, LocalTime end, List<Appointment> existingAppointments) {
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

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

