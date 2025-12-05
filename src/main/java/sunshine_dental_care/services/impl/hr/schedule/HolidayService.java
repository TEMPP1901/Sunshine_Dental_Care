package sunshine_dental_care.services.impl.hr;

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

    // kiểm tra ngày và clinic có phải là ngày nghỉ lễ không
    public boolean isHoliday(LocalDate date, Integer clinicId) {
        List<sunshine_dental_care.entities.Holiday> dbHolidays = holidayRepo.findAll();
        for (sunshine_dental_care.entities.Holiday h : dbHolidays) {
            LocalDate start = h.getDate();
            // xử lý holiday lặp lại hàng năm
            if (Boolean.TRUE.equals(h.getIsRecurring())) {
                try {
                    start = start.withYear(date.getYear());
                } catch (Exception e) {
                    // trường hợp ngày không hợp lệ (ví dụ 29/2 cho năm không nhuận)
                    continue;
                }
            }
            int duration = h.getDuration() != null && h.getDuration() > 0 ? h.getDuration() : 1;
            LocalDate end = start.plusDays(duration - 1);

            if (!date.isBefore(start) && !date.isAfter(end)) {
                // holiday áp dụng cho toàn bộ clinic
                if (h.getClinicId() == null) {
                    return true;
                }
                // holiday áp dụng cho clinic cụ thể
                if (clinicId != null && h.getClinicId().equals(clinicId)) {
                    return true;
                }
            }
        }
        return false;
    }

    // lấy tên ngày nghỉ lễ cho clinic cụ thể hoặc toàn hệ thống
    public String getHolidayName(LocalDate date, Integer clinicId) {
        List<sunshine_dental_care.entities.Holiday> dbHolidays = holidayRepo.findAll();
        for (sunshine_dental_care.entities.Holiday h : dbHolidays) {
            LocalDate start = h.getDate();
            // holiday lặp lại mỗi năm (ví dụ Tết, Quốc Khánh...)
            if (Boolean.TRUE.equals(h.getIsRecurring())) {
                try {
                    start = start.withYear(date.getYear());
                } catch (Exception e) {
                    continue;
                }
            }
            int duration = h.getDuration() != null && h.getDuration() > 0 ? h.getDuration() : 1;
            LocalDate end = start.plusDays(duration - 1);

            if (!date.isBefore(start) && !date.isAfter(end)) {
                // holiday cho toàn bộ clinic
                if (h.getClinicId() == null) {
                    return h.getName();
                }
                // holiday cho clinic cụ thể
                if (clinicId != null && h.getClinicId().equals(clinicId)) {
                    return h.getName();
                }
            }
        }
        return null;
    }

}