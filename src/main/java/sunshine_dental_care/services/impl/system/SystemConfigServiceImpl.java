package sunshine_dental_care.services.impl.system;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Holiday;
import sunshine_dental_care.entities.SystemConfig;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.system.HolidayRepo;
import sunshine_dental_care.repositories.system.SystemConfigRepo;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepo systemConfigRepo;
    private final HolidayRepo holidayRepo;
    private final ClinicRepo clinicRepo;

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepo.findAll();
    }

    @Override
    public SystemConfig getConfig(String key) {
        return systemConfigRepo.findByConfigKey(key).orElse(null);
    }

    @Override
    @Transactional
    public SystemConfig updateConfig(String key, String value, String description) {
        Optional<SystemConfig> existing = systemConfigRepo.findByConfigKey(key);
        SystemConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setConfigValue(value);
            if (description != null) {
                config.setDescription(description);
            }
        } else {
            config = SystemConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(description)
                    .build();
        }
        return systemConfigRepo.save(config);
    }

    @Override
    public Map<String, String> getPublicConfigs() {
        List<SystemConfig> configs = systemConfigRepo.findAll();
        Map<String, String> result = new HashMap<>();
        for (SystemConfig cfg : configs) {
            result.put(cfg.getConfigKey(), cfg.getConfigValue());
        }
        return result;
    }

    @Override
    public List<Holiday> getAllHolidays() {
        return holidayRepo.findAll();
    }

    // Thêm holiday, set isActive cho clinic khi cần thiết
    @Override
    @Transactional
    public Holiday addHoliday(LocalDate date, String name, Boolean isRecurring, Integer duration, Integer clinicId) {
        Holiday holiday = Holiday.builder()
                .date(date)
                .name(name)
                .isRecurring(isRecurring != null ? isRecurring : false)
                .duration(duration != null && duration > 0 ? duration : 1)
                .clinicId(clinicId)
                .build();
        Holiday savedHoliday = holidayRepo.save(holiday);
        LocalDate today = LocalDate.now();
        LocalDate holidayEndDate = date.plusDays((duration != null && duration > 0 ? duration : 1) - 1);

        // Nếu holiday đang diễn ra thì set inactive cho clinic
        if (!date.isAfter(today) && !holidayEndDate.isBefore(today)) {
            if (clinicId != null) {
                Optional<Clinic> clinicOpt = clinicRepo.findById(clinicId);
                if (clinicOpt.isPresent()) {
                    Clinic clinic = clinicOpt.get();
                    if (Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(false);
                        clinicRepo.save(clinic);
                        log.info("Set clinic {} ({}) to inactive due to holiday {} from {} to {}", 
                                clinicId, clinic.getClinicName(), name, date, holidayEndDate);
                    }
                } else {
                    log.warn("Clinic with ID {} not found when setting inactive for holiday", clinicId);
                }
            } else {
                // Nghỉ toàn bộ, set tất cả clinic về inactive
                List<Clinic> allClinics = clinicRepo.findAll();
                int inactiveCount = 0;
                for (Clinic clinic : allClinics) {
                    if (Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(false);
                        clinicRepo.save(clinic);
                        inactiveCount++;
                        log.info("Set clinic {} ({}) to inactive due to global holiday {} from {} to {}", 
                                clinic.getId(), clinic.getClinicName(), name, date, holidayEndDate);
                    }
                }
                log.info("Set {} clinics to inactive due to global holiday {} from {} to {}", 
                        inactiveCount, name, date, holidayEndDate);
            }
        }
        return savedHoliday;
    }

    // Xóa holiday và xử lý khôi phục trạng thái clinic nếu không còn ngày nghỉ phù hợp
    @Override
    @Transactional
    public void deleteHoliday(Integer id) {
        Optional<Holiday> holidayOpt = holidayRepo.findById(id);
        if (!holidayOpt.isPresent()) {
            holidayRepo.deleteById(id);
            return;
        }
        
        Holiday holiday = holidayOpt.get();
        Integer clinicId = holiday.getClinicId();
        String holidayName = holiday.getName();
        
        holidayRepo.deleteById(id);

        // Nếu là holiday cho một clinic cụ thể
        if (clinicId != null) {
            LocalDate today = LocalDate.now();
            List<Holiday> allHolidays = holidayRepo.findAll();
            boolean hasActiveHoliday = false;
            for (Holiday h : allHolidays) {
                if (h.getId().equals(id)) continue;
                Integer hClinicId = h.getClinicId();
                if (hClinicId != null && hClinicId.equals(clinicId)) {
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                } else if (hClinicId == null) {
                    // Holiday toàn hệ thống cũng áp dụng với clinic này
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                }
            }
            // Nếu không còn holiday active nào thì set lại clinic về active
            if (!hasActiveHoliday) {
                Optional<Clinic> clinicOpt = clinicRepo.findById(clinicId);
                if (clinicOpt.isPresent()) {
                    Clinic clinic = clinicOpt.get();
                    if (!Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(true);
                        clinicRepo.save(clinic);
                        log.info("Restored clinic {} ({}) to active after deleting holiday {}", 
                                clinicId, clinic.getClinicName(), holidayName);
                    }
                }
            }
        } else {
            // Holiday toàn hệ thống
            LocalDate today = LocalDate.now();
            List<Holiday> allHolidays = holidayRepo.findAll();
            boolean hasActiveGlobalHoliday = false;
            for (Holiday h : allHolidays) {
                if (h.getId().equals(id)) continue;
                if (h.getClinicId() == null) {
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveGlobalHoliday = true;
                        break;
                    }
                }
            }
            // Nếu không còn global holiday active thì phục hồi tất cả clinic, trừ clinic còn holiday riêng
            if (!hasActiveGlobalHoliday) {
                List<Clinic> allClinics = clinicRepo.findAll();
                int restoredCount = 0;
                for (Clinic clinic : allClinics) {
                    boolean hasActiveSpecificHoliday = false;
                    for (Holiday h : allHolidays) {
                        if (h.getId().equals(id)) continue;
                        if (h.getClinicId() != null && h.getClinicId().equals(clinic.getId())) {
                            LocalDate hStart = h.getDate();
                            if (Boolean.TRUE.equals(h.getIsRecurring())) {
                                try {
                                    hStart = hStart.withYear(today.getYear());
                                } catch (Exception e) {
                                    continue;
                                }
                            }
                            Integer hDurationValue = h.getDuration();
                            int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                            LocalDate hEnd = hStart.plusDays(hDuration - 1);
                            if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                                hasActiveSpecificHoliday = true;
                                break;
                            }
                        }
                    }
                    if (!hasActiveSpecificHoliday && !Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(true);
                        clinicRepo.save(clinic);
                        restoredCount++;
                        log.info("Restored clinic {} ({}) to active after deleting global holiday {}", 
                                clinic.getId(), clinic.getClinicName(), holidayName);
                    }
                }
                log.info("Restored {} clinics to active after deleting global holiday {}", restoredCount, holidayName);
            }
        }
    }

    // Kiểm tra ngày có thuộc holiday không
    @Override
    public boolean isHoliday(LocalDate date) {
        List<Holiday> holidays = holidayRepo.findHolidaysForDate(date);
        return !holidays.isEmpty();
    }

    // Quét và tự động trả lại trạng thái active cho clinic nếu không còn holiday diễn ra
    @Override
    @Transactional
    public void restoreClinicsAfterHolidayEnded() {
        LocalDate today = LocalDate.now();
        List<Holiday> allHolidays = holidayRepo.findAll();
        List<Clinic> allClinics = clinicRepo.findAll();

        for (Clinic clinic : allClinics) {
            if (Boolean.TRUE.equals(clinic.getIsActive())) {
                continue;
            }
            boolean hasActiveHoliday = false;
            for (Holiday h : allHolidays) {
                Integer hClinicId = h.getClinicId();
                if (hClinicId == null) {
                    // Global holiday
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                } else if (hClinicId.equals(clinic.getId())) {
                    // Holiday cho từng clinic
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);                  
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                }
            }
            // Nếu không có holiday active cho clinic thì set lại active
            if (!hasActiveHoliday) {
                clinic.setIsActive(true);
                clinicRepo.save(clinic);
                log.info("Auto-restored clinic {} ({}) to active - no active holidays found", 
                        clinic.getId(), clinic.getClinicName());
            }
        }
    }
}
