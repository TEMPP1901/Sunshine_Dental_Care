package sunshine_dental_care.services.impl.hr.schedule;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HolidayService {

    private final sunshine_dental_care.repositories.system.HolidayRepo holidayRepo;

    public HolidayService(sunshine_dental_care.repositories.system.HolidayRepo holidayRepo) {
        this.holidayRepo = holidayRepo;
    }

    // Kiểm tra một ngày có phải là ngày nghỉ lễ tại một clinic
    public boolean isHoliday(LocalDate date, Integer clinicId) {
        List<sunshine_dental_care.entities.Holiday> dbHolidays = holidayRepo.findAll();
        for (sunshine_dental_care.entities.Holiday h : dbHolidays) {
            LocalDate start = h.getDate();
            if (Boolean.TRUE.equals(h.getIsRecurring())) {
                try {
                    start = start.withYear(date.getYear());
                } catch (Exception e) {
                    // Bỏ qua nếu ngày không hợp lệ cho năm hiện tại
                    continue;
                }
            }
            int duration = h.getDuration() != null && h.getDuration() > 0 ? h.getDuration() : 1;
            LocalDate end = start.plusDays(duration - 1);

            if (!date.isBefore(start) && !date.isAfter(end)) {
                // holiday cho toàn bộ hệ thống
                if (h.getClinicId() == null) {
                    return true;
                }
                // holiday chỉ cho clinic cụ thể
                if (clinicId != null && h.getClinicId().equals(clinicId)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Lấy tên ngày nghỉ lễ cho một ngày ở một clinic (nếu có)
    public String getHolidayName(LocalDate date, Integer clinicId) {
        List<sunshine_dental_care.entities.Holiday> dbHolidays = holidayRepo.findAll();
        for (sunshine_dental_care.entities.Holiday h : dbHolidays) {
            LocalDate start = h.getDate();
            if (Boolean.TRUE.equals(h.getIsRecurring())) {
                try {
                    start = start.withYear(date.getYear());
                } catch (Exception e) {
                    // Bỏ qua nếu ngày không hợp lệ cho năm hiện tại
                    continue;
                }
            }
            int duration = h.getDuration() != null && h.getDuration() > 0 ? h.getDuration() : 1;
            LocalDate end = start.plusDays(duration - 1);

            if (!date.isBefore(start) && !date.isAfter(end)) {
                if (h.getClinicId() == null) {
                    return h.getName();
                }
                if (clinicId != null && h.getClinicId().equals(clinicId)) {
                    return h.getName();
                }
            }
        }
        return null;
    }

}