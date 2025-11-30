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

    // Cờ kiểm tra thư viện hỗ trợ lịch âm có khả dụng không
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

    // Cache cho từng năm
    private final Map<Integer, Map<LocalDate, String>> holidayCache = new HashMap<>();

    // Kiểm tra ngày có là ngày nghỉ lễ không
    public boolean isHoliday(LocalDate date) {
        return getHolidayName(date) != null;
    }

    // Lấy tên ngày lễ nếu có
    public String getHolidayName(LocalDate date) {
        int year = date.getYear();
        Map<LocalDate, String> holidays = holidayCache.computeIfAbsent(year, this::calculateHolidaysForYear);
        return holidays.get(date);
    }

    // Tính tất cả các ngày lễ trong năm, ưu tiên dùng thư viện nếu có
    private Map<LocalDate, String> calculateHolidaysForYear(int year) {
        Map<LocalDate, String> holidays = new HashMap<>();

        // Ngày lễ cố định (dương lịch)
        holidays.put(LocalDate.of(year, 1, 1), "New Year's Day");
        holidays.put(LocalDate.of(year, 4, 30), "Reunification Day");
        holidays.put(LocalDate.of(year, 5, 1), "International Labor Day");
        holidays.put(LocalDate.of(year, 9, 2), "National Day");

        // Ngày lễ âm lịch, ưu tiên thư viện tốt nhất
        if (LUNAR_6TAIL_AVAILABLE) {
            try {
                calculateLunarHolidaysWith6Tail(holidays, year);
            } catch (Exception e) {
                // Thư viện 6tail lỗi, thử thư viện còn lại hoặc tính động
                if (LUNAR_LIBRARY_AVAILABLE) {
                    try {
                        calculateLunarHolidaysWithKuaidi100(holidays, year);
                    } catch (Exception ex) {
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
                calculateLunarHolidaysDynamic(holidays, year);
            }
        } else {
            calculateLunarHolidaysDynamic(holidays, year);
        }

        return holidays;
    }

    // Sử dụng thư viện cn.6tail.lunar để tính các ngày lễ âm lịch
    private void calculateLunarHolidaysWith6Tail(Map<LocalDate, String> holidays, int year) throws Exception {
        Class<?> lunarClass = Class.forName("cn.sixtail.lunar.Lunar");

        // Giỗ Tổ Hùng Vương (10/3 âm lịch)
        Object lunarHungKings = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
                .newInstance(year, 3, 10, false);
        Object solarHungKings = lunarClass.getMethod("getSolar").invoke(lunarHungKings);
        Class<?> solarClass = solarHungKings.getClass();
        int hkYear = (int) solarClass.getMethod("getYear").invoke(solarHungKings);
        int hkMonth = (int) solarClass.getMethod("getMonth").invoke(solarHungKings);
        int hkDay = (int) solarClass.getMethod("getDay").invoke(solarHungKings);
        holidays.put(LocalDate.of(hkYear, hkMonth, hkDay),
                "Hung Kings' Commemoration Day");

        // Tết Nguyên Đán: từ 30/12 năm trước tới hết mùng 5 (1/5 âm lịch)
        Object lunarNewYearsEve = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
                .newInstance(year - 1, 12, 30, false);
        Object solarNewYearsEve = lunarClass.getMethod("getSolar").invoke(lunarNewYearsEve);
        int nyeYear = (int) solarClass.getMethod("getYear").invoke(solarNewYearsEve);
        if (nyeYear == year) {
            int nyeMonth = (int) solarClass.getMethod("getMonth").invoke(solarNewYearsEve);
            int nyeDay = (int) solarClass.getMethod("getDay").invoke(solarNewYearsEve);
            holidays.put(LocalDate.of(year, nyeMonth, nyeDay),
                    "Lunar New Year's Eve");
        }
        // Từ mùng 1 đến mùng 5
        for (int day = 1; day <= 5; day++) {
            Object lunarTet = lunarClass.getConstructor(int.class, int.class, int.class, boolean.class)
                    .newInstance(year, 1, day, false);
            Object solarTet = lunarClass.getMethod("getSolar").invoke(lunarTet);
            int tetYear = (int) solarClass.getMethod("getYear").invoke(solarTet);
            if (tetYear == year) {
                int tetMonth = (int) solarClass.getMethod("getMonth").invoke(solarTet);
                int tetDay = (int) solarClass.getMethod("getDay").invoke(solarTet);
                String tetDayName = day == 1 ? "Lunar New Year Day 1" : "Lunar New Year Day " + day;
                holidays.put(LocalDate.of(year, tetMonth, tetDay),
                        tetDayName);
            }
        }
    }

    // Sử dụng thư viện com.kuaidi100.sdk.utils (fallback)
    private void calculateLunarHolidaysWithKuaidi100(Map<LocalDate, String> holidays, int year) throws Exception {
        Class<?> lunarClass = Class.forName("com.kuaidi100.sdk.utils.Lunar");
        Class<?> solarClass = Class.forName("com.kuaidi100.sdk.utils.Solar");

        // Giỗ Tổ Hùng Vương (10/3 âm lịch)
        Object lunarHungKings = lunarClass.getConstructor(int.class, int.class, int.class)
                .newInstance(year, 3, 10);
        Object solarHungKings = lunarClass.getMethod("toSolar").invoke(lunarHungKings);
        int hkYear = (int) solarClass.getMethod("getYear").invoke(solarHungKings);
        int hkMonth = (int) solarClass.getMethod("getMonth").invoke(solarHungKings);
        int hkDay = (int) solarClass.getMethod("getDay").invoke(solarHungKings);
        holidays.put(LocalDate.of(hkYear, hkMonth, hkDay),
                "Hung Kings' Commemoration Day");

        // Tết Nguyên Đán âm lịch
        Object lunarNewYearsEve = lunarClass.getConstructor(int.class, int.class, int.class)
                .newInstance(year - 1, 12, 30);
        Object solarNewYearsEve = lunarClass.getMethod("toSolar").invoke(lunarNewYearsEve);
        int nyeYear = (int) solarClass.getMethod("getYear").invoke(solarNewYearsEve);
        if (nyeYear == year) {
            int nyeMonth = (int) solarClass.getMethod("getMonth").invoke(solarNewYearsEve);
            int nyeDay = (int) solarClass.getMethod("getDay").invoke(solarNewYearsEve);
            holidays.put(LocalDate.of(year, nyeMonth, nyeDay),
                    "Lunar New Year's Eve");
        }
        for (int day = 1; day <= 5; day++) {
            Object lunarTet = lunarClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(year, 1, day);
            Object solarTet = lunarClass.getMethod("toSolar").invoke(lunarTet);
            int tetYear = (int) solarClass.getMethod("getYear").invoke(solarTet);
            if (tetYear == year) {
                int tetMonth = (int) solarClass.getMethod("getMonth").invoke(solarTet);
                int tetDay = (int) solarClass.getMethod("getDay").invoke(solarTet);
                String tetDayName = day == 1 ? "Lunar New Year Day 1" : "Lunar New Year Day " + day;
                holidays.put(LocalDate.of(year, tetMonth, tetDay),
                        tetDayName);
            }
        }
    }

    // Tính ngày lễ âm lịch theo công thức gần đúng nếu không có thư viện
    private void calculateLunarHolidaysDynamic(Map<LocalDate, String> holidays, int year) {
        LocalDate hungKingsDate = calculateLunarToSolar(year, 3, 10);
        if (hungKingsDate != null) {
            holidays.put(hungKingsDate, "Hung Kings' Commemoration Day");
        }
        LocalDate newYearsEve = calculateLunarToSolar(year - 1, 12, 30);
        if (newYearsEve != null && newYearsEve.getYear() == year) {
            holidays.put(newYearsEve, "Lunar New Year's Eve");
        }
        for (int day = 1; day <= 5; day++) {
            LocalDate tetDate = calculateLunarToSolar(year, 1, day);
            if (tetDate != null && tetDate.getYear() == year) {
                String tetDayName = day == 1 ? "Lunar New Year Day 1" : "Lunar New Year Day " + day;
                holidays.put(tetDate, tetDayName);
            }
        }
    }

    // Chuyển âm lịch sang dương lịch (công thức gần đúng)
    private LocalDate calculateLunarToSolar(int lunarYear, int lunarMonth, int lunarDay) {
        try {
            double jdn = getJulianDayNumber(lunarYear, lunarMonth, lunarDay);
            return julianToGregorian(jdn);
        } catch (Exception e) {
            // Thông báo lỗi chuyển đổi âm - dương lịch
            return null;
        }
    }

    // Tính gần đúng số ngày Julian từ ngày âm lịch
    private double getJulianDayNumber(int lunarYear, int lunarMonth, int lunarDay) {
        double baseJDN = 2415021.0; // mốc 1900-01-31
        int yearsFrom1900 = lunarYear - 1900;
        int monthsFromYearStart = lunarMonth - 1;
        int daysFromMonthStart = lunarDay - 1;
        double lunarCycle = 29.530588853;
        double jdn = baseJDN;
        jdn += yearsFrom1900 * 365.25;
        jdn += monthsFromYearStart * lunarCycle;
        jdn += daysFromMonthStart;
        return jdn;
    }

    // Chuyển Julian Number sang ngày dương lịch
    private LocalDate julianToGregorian(double jdn) {
        int j = (int) (jdn + 0.5);
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

    // Tính toán các ngày nghỉ lễ trong 1 tuần làm việc (thứ 2 - thứ 7)
    public List<String> calculateHolidaysInWeek(LocalDate weekStart) {
        List<String> holidays = new ArrayList<>();
        String[] dayNames = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" };
        for (int i = 0; i < 6; i++) {
            LocalDate date = weekStart.plusDays(i);
            if (isHoliday(date)) {
                holidays.add(dayNames[i]);
            }
        }
        return holidays;
    }
}