package sunshine_dental_care.services.interfaces.system;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import sunshine_dental_care.entities.Holiday;
import sunshine_dental_care.entities.SystemConfig;

public interface SystemConfigService {
    // Lấy tất cả config hệ thống
    List<SystemConfig> getAllConfigs();

    // Lấy config theo key
    SystemConfig getConfig(String key);

    // Cập nhật hoặc tạo mới config hệ thống
    SystemConfig updateConfig(String key, String value, String description);

    // Lấy các cấu hình công khai cho frontend
    Map<String, String> getPublicConfigs();

    // Lấy danh sách ngày nghỉ
    List<Holiday> getAllHolidays();

    // Thêm mới ngày nghỉ
    Holiday addHoliday(LocalDate date, String name, Boolean isRecurring, Integer duration, Integer clinicId);

    void deleteHoliday(Integer id);

    // Kiểm tra một ngày có phải ngày nghỉ không
    boolean isHoliday(LocalDate date);

    // Tự động bật lại phòng khám sau khi hết kỳ nghỉ
    void restoreClinicsAfterHolidayEnded();

    BigDecimal getVipFee();       // Lấy phí cọc/khám VIP
    BigDecimal getStandardFee();  // Lấy phí khám Standard
}
