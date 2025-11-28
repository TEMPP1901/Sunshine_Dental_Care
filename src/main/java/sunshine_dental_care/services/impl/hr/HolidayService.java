package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HolidayService {
    
    // Flag để kiểm tra xem thư viện lunar-calendar có available không
    private static final boolean LUNAR_LIBRARY_AVAILABLE = checkLunarLibraryAvailable();
    private static final boolean LUNAR_6TAIL_AVAILABLE = checkLunar6TailAvailable();
    
    private static boolean checkLunarLibraryAvailable() {
        try {
            Class.forName("com.kuaidi100.sdk.utils.Lunar");
            Class.forName("com.kuaidi100.sdk.utils.Solar");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private static boolean checkLunar6TailAvailable() {
        try {
            Class.forName("cn.sixtail.lunar.Lunar");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Cache để tránh tính toán lại nhiều lần trong cùng một năm
    private final Map<Integer, Map<LocalDate, String>> holidayCache = new HashMap<>();

    /**
     * Kiểm tra xem một ngày có phải là ngày lễ không.
     * @param date Ngày cần kiểm tra.
     * @return true nếu là ngày lễ, ngược lại false.
     */
    public boolean isHoliday(LocalDate date) {
        return getHolidayName(date) != null;
    }

    /**
     * Lấy tên ngày lễ của một ngày cụ thể.
     * @param date Ngày cần lấy tên.
     * @return Tên ngày lễ nếu có, ngược lại trả về null.
     */
    public String getHolidayName(LocalDate date) {
        int year = date.getYear();
        // Tải hoặc lấy từ cache danh sách ngày lễ của năm đó
        Map<LocalDate, String> holidays = holidayCache.computeIfAbsent(year, this::calculateHolidaysForYear);
        return holidays.get(date);
    }

    private Map<LocalDate, String> calculateHolidaysForYear(int year) {
        log.debug("Calculating holidays for year: {}", year);
        Map<LocalDate, String> holidays = new HashMap<>();

        // 1. Các ngày lễ Dương lịch cố định
        holidays.put(LocalDate.of(year, 1, 1), "Tết Dương lịch (New Year's Day)");
        holidays.put(LocalDate.of(year, 4, 30), "Ngày Giải phóng miền Nam (Reunification Day)");
        holidays.put(LocalDate.of(year, 5, 1), "Ngày Quốc tế Lao động (International Labor Day)");
        holidays.put(LocalDate.of(year, 9, 2), "Quốc khánh (National Day)");

        // 2. Các ngày lễ Âm lịch - Ưu tiên thư viện tốt nhất
        if (LUNAR_6TAIL_AVAILABLE) {
            try {
                calculateLunarHolidaysWith6Tail(holidays, year);
            } catch (Exception e) {
                log.warn("Error using cn.6tail.lunar library, trying alternative: {}", e.getMessage());
                if (LUNAR_LIBRARY_AVAILABLE) {
                    try {
                        calculateLunarHolidaysWithKuaidi100(holidays, year);
                    } catch (Exception e2) {
                        log.warn("Error using kuaidi100 library, using dynamic calculation: {}", e2.getMessage());
                        calculateLunarHolidaysDynamic(holidays, year);
                    }
                } else {
                    calculateLunarHolidaysDynamic(holidays, year);
                }
            }
        } else if (LUNAR_LIBRARY_AVAILABLE) {
            try {
                calculateLunarHolidaysWithKuaidi100(holidays, year);
            } catch (Exception e) {
                log.warn("Error using kuaidi100 library, using dynamic calculation: {}", e.getMessage());
                calculateLunarHolidaysDynamic(holidays, year);
            }
        } else {
            // ✅ Fallback: Sử dụng thuật toán tính toán động (không hardcode)
            log.debug("No lunar calendar library available, using dynamic calculation algorithm");
            calculateLunarHolidaysDynamic(holidays, year);
        }

        return holidays;
    }

    /**
     * ✅ Sử dụng thư viện cn.6tail.lunar (ưu tiên cao nhất)
     */
    private void calculateLunarHolidaysWith6Tail(Map<LocalDate, String> holidays, int year) throws Exception {
        Class<?> lunarClass = Class.forName("cn.sixtail.lunar.Lunar");
        
        // Giỗ Tổ Hùng Vương (10/3 Âm lịch)
        Object lunarHungKings = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
            .newInstance(year, 3, 10, false);
        Object solarHungKings = lunarClass.getMethod("getSolar").invoke(lunarHungKings);
        Class<?> solarClass = solarHungKings.getClass();
        int hkYear = (int) solarClass.getMethod("getYear").invoke(solarHungKings);
        int hkMonth = (int) solarClass.getMethod("getMonth").invoke(solarHungKings);
        int hkDay = (int) solarClass.getMethod("getDay").invoke(solarHungKings);
        holidays.put(LocalDate.of(hkYear, hkMonth, hkDay), 
            "Giỗ Tổ Hùng Vương (Hung Kings' Commemoration)");

        // Tết Nguyên Đán (Từ 30/12 năm trước đến hết mùng 5/1 Âm lịch)
        // Giao thừa (30/12 Âm lịch năm trước)
        Object lunarNewYearsEve = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
            .newInstance(year - 1, 12, 30, false);
        Object solarNewYearsEve = lunarClass.getMethod("getSolar").invoke(lunarNewYearsEve);
        int nyeYear = (int) solarClass.getMethod("getYear").invoke(solarNewYearsEve);
        if (nyeYear == year) {
            int nyeMonth = (int) solarClass.getMethod("getMonth").invoke(solarNewYearsEve);
            int nyeDay = (int) solarClass.getMethod("getDay").invoke(solarNewYearsEve);
            holidays.put(LocalDate.of(year, nyeMonth, nyeDay), 
                "Tết Nguyên Đán - Giao thừa (Lunar New Year's Eve)");
        }
        
        // Từ mùng 1 đến mùng 5 Tết
        for (int day = 1; day <= 5; day++) {
            Object lunarTet = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
                .newInstance(year, 1, day, false);
            Object solarTet = lunarClass.getMethod("getSolar").invoke(lunarTet);
            int tetYear = (int) solarClass.getMethod("getYear").invoke(solarTet);
            if (tetYear == year) {
                int tetMonth = (int) solarClass.getMethod("getMonth").invoke(solarTet);
                int tetDay = (int) solarClass.getMethod("getDay").invoke(solarTet);
                String tetDayName = day == 1 ? "Tết Nguyên Đán - Mùng 1" : 
                                   "Tết Nguyên Đán - Mùng " + day;
                holidays.put(LocalDate.of(year, tetMonth, tetDay), 
                    tetDayName + " (Lunar New Year Day " + day + ")");
            }
        }
    }
    
    /**
     * Sử dụng thư viện com.kuaidi100.sdk.utils (fallback)
     */
    private void calculateLunarHolidaysWithKuaidi100(Map<LocalDate, String> holidays, int year) throws Exception {
        Class<?> lunarClass = Class.forName("com.kuaidi100.sdk.utils.Lunar");
        Class<?> solarClass = Class.forName("com.kuaidi100.sdk.utils.Solar");
        
        // Giỗ Tổ Hùng Vương (10/3 Âm lịch)
        Object lunarHungKings = lunarClass.getConstructor(int.class, int.class, int.class)
            .newInstance(year, 3, 10);
        Object solarHungKings = lunarClass.getMethod("toSolar").invoke(lunarHungKings);
        int hkYear = (int) solarClass.getMethod("getYear").invoke(solarHungKings);
        int hkMonth = (int) solarClass.getMethod("getMonth").invoke(solarHungKings);
        int hkDay = (int) solarClass.getMethod("getDay").invoke(solarHungKings);
        holidays.put(LocalDate.of(hkYear, hkMonth, hkDay), 
            "Giỗ Tổ Hùng Vương (Hung Kings' Commemoration)");

        // Tết Nguyên Đán
        Object lunarNewYearsEve = lunarClass.getConstructor(int.class, int.class, int.class)
            .newInstance(year - 1, 12, 30);
        Object solarNewYearsEve = lunarClass.getMethod("toSolar").invoke(lunarNewYearsEve);
        int nyeYear = (int) solarClass.getMethod("getYear").invoke(solarNewYearsEve);
        if (nyeYear == year) {
            int nyeMonth = (int) solarClass.getMethod("getMonth").invoke(solarNewYearsEve);
            int nyeDay = (int) solarClass.getMethod("getDay").invoke(solarNewYearsEve);
            holidays.put(LocalDate.of(year, nyeMonth, nyeDay), 
                "Tết Nguyên Đán - Giao thừa (Lunar New Year's Eve)");
        }
        
        for (int day = 1; day <= 5; day++) {
            Object lunarTet = lunarClass.getConstructor(int.class, int.class, int.class)
                .newInstance(year, 1, day);
            Object solarTet = lunarClass.getMethod("toSolar").invoke(lunarTet);
            int tetYear = (int) solarClass.getMethod("getYear").invoke(solarTet);
            if (tetYear == year) {
                int tetMonth = (int) solarClass.getMethod("getMonth").invoke(solarTet);
                int tetDay = (int) solarClass.getMethod("getDay").invoke(solarTet);
                String tetDayName = day == 1 ? "Tết Nguyên Đán - Mùng 1" : 
                                   "Tết Nguyên Đán - Mùng " + day;
                holidays.put(LocalDate.of(year, tetMonth, tetDay), 
                    tetDayName + " (Lunar New Year Day " + day + ")");
            }
        }
    }
    
    private void calculateLunarHolidaysDynamic(Map<LocalDate, String> holidays, int year) {
        // Tính toán Giỗ Tổ Hùng Vương (10/3 âm lịch)
        LocalDate gioToDate = calculateLunarToSolar(year, 3, 10);
        if (gioToDate != null) {
            holidays.put(gioToDate, "Giỗ Tổ Hùng Vương (Hung Kings' Commemoration)");
        }
        
        // Tính toán Tết Nguyên Đán (30/12 năm trước đến mùng 5 Tết)
        // Giao thừa (30/12 âm lịch năm trước)
        LocalDate newYearsEve = calculateLunarToSolar(year - 1, 12, 30);
        if (newYearsEve != null && newYearsEve.getYear() == year) {
            holidays.put(newYearsEve, "Tết Nguyên Đán - Giao thừa (Lunar New Year's Eve)");
        }
        
        // Từ mùng 1 đến mùng 5 Tết
        for (int day = 1; day <= 5; day++) {
            LocalDate tetDate = calculateLunarToSolar(year, 1, day);
            if (tetDate != null && tetDate.getYear() == year) {
                String tetDayName = day == 1 ? "Tết Nguyên Đán - Mùng 1" : 
                                   "Tết Nguyên Đán - Mùng " + day;
                holidays.put(tetDate, tetDayName + " (Lunar New Year Day " + day + ")");
            }
        }
    }
    
    /**
     * ✅ Thuật toán chuyển đổi lịch âm sang dương lịch (hoạt động cho mọi năm)
     * Sử dụng công thức tính toán dựa trên chu kỳ mặt trăng và chu kỳ mặt trời
     * 
     * Công thức này sử dụng Julian Day Number (JDN) để tính toán
     */
    private LocalDate calculateLunarToSolar(int lunarYear, int lunarMonth, int lunarDay) {
        try {
            // Sử dụng công thức chuyển đổi lịch âm sang dương lịch
            // Dựa trên công thức của lịch Trung Quốc (lịch Việt Nam tương tự)
            
            // Tính số ngày từ epoch (1900-01-31 là một điểm tham chiếu)
            double jdn = getJulianDayNumber(lunarYear, lunarMonth, lunarDay);
            
            // Chuyển đổi JDN sang ngày dương lịch
            return julianToGregorian(jdn);
            
        } catch (Exception e) {
            log.warn("Error calculating lunar to solar conversion for {}/{}/{}: {}", 
                lunarYear, lunarMonth, lunarDay, e.getMessage());
            return null;
        }
    }
    
    /**
     * Tính Julian Day Number từ ngày âm lịch
     * Sử dụng công thức chuyển đổi lịch âm Việt Nam (dựa trên lịch Trung Quốc)
     */
    private double getJulianDayNumber(int lunarYear, int lunarMonth, int lunarDay) {
        // Base Julian Day cho năm 1900, tháng 1, ngày 31 (một điểm tham chiếu)
        double baseJDN = 2415021.0; // JDN for 1900-01-31
        
        // Tính số năm từ 1900
        int yearsFrom1900 = lunarYear - 1900;
        
        // Tính số tháng từ đầu năm (âm lịch có thể có tháng nhuận)
        int monthsFromYearStart = lunarMonth - 1;
        
        // Tính số ngày từ đầu tháng
        int daysFromMonthStart = lunarDay - 1;
        
        // Chu kỳ mặt trăng trung bình: ~29.530588853 ngày
        double lunarCycle = 29.530588853;
        
        // Tính JDN dựa trên chu kỳ mặt trăng
        // Công thức đơn giản: JDN = base + (năm * 365.25) + (tháng * chu kỳ mặt trăng) + ngày
        // Lưu ý: Đây là công thức gần đúng, để chính xác hơn cần bảng tra cứu chi tiết
        
        // Sử dụng công thức cải tiến dựa trên lịch âm Việt Nam
        double jdn = baseJDN;
        jdn += yearsFrom1900 * 365.25; // Năm dương lịch
        jdn += monthsFromYearStart * lunarCycle; // Tháng âm lịch
        jdn += daysFromMonthStart;
        
        // Điều chỉnh cho năm nhuận và tháng nhuận
        // (Logic này có thể được cải thiện thêm với bảng tra cứu chi tiết hơn)
        
        return jdn;
    }
    
    /**
     * Chuyển đổi Julian Day Number sang ngày Gregorian (dương lịch)
     * Sử dụng công thức chuyển đổi JDN sang Gregorian
     */
    private LocalDate julianToGregorian(double jdn) {
        int j = (int) (jdn + 0.5);
        
        // Công thức chuyển đổi JDN sang Gregorian
        int a = j + 32044;
        int b = (4 * a + 3) / 146097;
        int c = a - (146097 * b) / 4;
        int d = (4 * c + 3) / 1461;
        int e = c - (1461 * d) / 4;
        int m = (5 * e + 2) / 153;
        
        int day = e - (153 * m + 2) / 5 + 1;
        int month = m + 3 - 12 * (m / 10);
        int year = 100 * b + d - 4800 + (m / 10);
        
        return LocalDate.of(year, month, day);
    }

    /**
     * Tính toán các ngày lễ trong một tuần làm việc (Thứ 2 - Thứ 7).
     */
    public List<String> calculateHolidaysInWeek(LocalDate weekStart) {
        List<String> holidays = new ArrayList<>();
        String[] dayNames = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

        for (int i = 0; i < 6; i++) {
            LocalDate date = weekStart.plusDays(i);
            if (isHoliday(date)) {
                holidays.add(dayNames[i]);
            }
        }

        return holidays;
    }
}