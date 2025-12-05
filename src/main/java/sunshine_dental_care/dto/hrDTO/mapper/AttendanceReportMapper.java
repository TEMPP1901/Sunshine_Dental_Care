package sunshine_dental_care.dto.hrDTO.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.services.impl.hr.attend.AttendanceStatusCalculator;

/**
 * Mapper để chuyển đổi Attendance entities sang Report Response DTOs.
 * Tách logic mapping từ AttendanceReportService để code gọn hơn, theo pattern
 * của Reception module.
 */
@Component
@RequiredArgsConstructor
public class AttendanceReportMapper {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AttendanceStatusCalculator attendanceStatusCalculator;

    /**
     * Map Attendance entity sang DailyAttendanceListItemResponse DTO.
     */
    public DailyAttendanceListItemResponse mapToDailyListItem(
            Attendance attendance,
            User user,
            LocalDate workDate) {

        DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
        item.setId(attendance.getId());
        item.setCheckInTime(attendance.getCheckInTime());
        item.setCheckOutTime(attendance.getCheckOutTime());

        String status = attendance.getAttendanceStatus();
        boolean hasCheckIn = attendance.getCheckInTime() != null;
        boolean hasCheckOut = attendance.getCheckOutTime() != null;

        // Phân loại trạng thái chấm công
        if (status == null) {
            if (hasCheckIn || hasCheckOut) {
                item.setStatus(hasCheckIn && hasCheckOut ? "Present" : (hasCheckIn ? "Present" : "Absent"));
                item.setStatusColor(hasCheckIn && hasCheckOut ? "green" : (hasCheckIn ? "green" : "red"));
            } else {
                item.setStatus("Offday");
                item.setStatusColor("gray");
            }
        } else {
            switch (status) {
                case "ON_TIME":
                case "APPROVED_PRESENT":
                    item.setStatus("Present");
                    item.setStatusColor("green");
                    break;
                case "LATE":
                case "APPROVED_LATE":
                    item.setStatus("Late");
                    item.setStatusColor("orange");
                    break;
                case "ABSENT":
                    item.setStatus("Absent");
                    item.setStatusColor("red");
                    break;
                case "APPROVED_ABSENCE":
                    item.setStatus("Approved Leave");
                    item.setStatusColor("blue");
                    break;
                case "HOLIDAY":
                    item.setStatus("Holiday");
                    item.setStatusColor("purple");
                    break;
                default:
                    item.setStatus("Unknown");
                    item.setStatusColor("gray");
                    break;
            }
        }

        // Nếu là làm thêm giờ thì đánh dấu lại trạng thái là Overtime
        if (attendance.getIsOvertime() != null && attendance.getIsOvertime()) {
            item.setStatus("Overtime");
            item.setStatusColor("blue");
        }

        // Tính worked hours: ưu tiên actualWorkHours (đã trừ lunch break), nếu không có thì tính và trừ lunch break
        if (hasCheckIn && hasCheckOut) {
            BigDecimal actualHours = null;
            long adjustedMinutes = 0;
            
            // Ưu tiên dùng actualWorkHours nếu có và > 0 (đã được tính đúng với lunch break)
            if (attendance.getActualWorkHours() != null && 
                attendance.getActualWorkHours().compareTo(BigDecimal.ZERO) > 0) {
                actualHours = attendance.getActualWorkHours();
            } else {
                // Tính từ check-in/check-out và trừ lunch break (11:00-13:00 = 120 phút)
                long totalMinutes = java.time.Duration.between(
                        attendance.getCheckInTime(),
                        attendance.getCheckOutTime()).toMinutes();
                
                LocalTime checkInLocalTime = attendance.getCheckInTime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime();
                LocalTime checkOutLocalTime = attendance.getCheckOutTime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime();
                
                long lunchBreakMinutes = 0;
                LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
                LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
                if (checkInLocalTime.isBefore(LUNCH_BREAK_START)
                        && checkOutLocalTime.isAfter(LUNCH_BREAK_END)) {
                    lunchBreakMinutes = 120;
                }
                
                adjustedMinutes = totalMinutes - lunchBreakMinutes;
                if (adjustedMinutes < 0) {
                    adjustedMinutes = 0;
                }
                actualHours = BigDecimal.valueOf(adjustedMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            }
            
            // Chuyển đổi BigDecimal sang hours và minutes
            long hours = actualHours.longValue();
            BigDecimal fractionalHours = actualHours.subtract(BigDecimal.valueOf(hours));
            long remainingMinutes = fractionalHours.multiply(BigDecimal.valueOf(60))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValue();
            
            item.setWorkedHours(hours);
            item.setWorkedMinutes(remainingMinutes);
            item.setWorkedDisplay(String.format("%d hr %02d min", hours, remainingMinutes));
        } else {
            item.setWorkedHours(0L);
            item.setWorkedMinutes(0L);
            item.setWorkedDisplay("0 hr 00 min");
        }

        // Lấy ca trực cho bác sĩ (nếu có)
        List<DoctorSchedule> schedules = doctorScheduleRepo
                .findByUserIdAndClinicIdAndWorkDate(user.getId(), attendance.getClinicId(), workDate);

        if (!schedules.isEmpty()) {
            DoctorSchedule schedule = schedules.get(0);
            item.setShiftStartTime(schedule.getStartTime());
            item.setShiftEndTime(schedule.getEndTime());
            item.setShiftDisplay(formatTime(schedule.getStartTime()) + " - " + formatTime(schedule.getEndTime()));
            long shiftHours = java.time.Duration.between(schedule.getStartTime(), schedule.getEndTime()).toHours();
            item.setShiftHours(shiftHours + " hr Shift: A");
        } else {
            LocalTime defaultStart = attendanceStatusCalculator.getDefaultStartTime();
            LocalTime defaultEnd = defaultStart.plusHours(9);
            item.setShiftStartTime(defaultStart);
            item.setShiftEndTime(defaultEnd);
            item.setShiftDisplay(formatTime(defaultStart) + " - " + formatTime(defaultEnd));
            item.setShiftHours("9 hr Shift: A");
        }

        item.setRemarks(attendance.getNote() != null ? attendance.getNote() : "Fixed Attendance");

        return item;
    }

    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour < 12 ? "am" : "pm";
        int displayHour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
        return String.format("%d.%02d%s", displayHour, minute, period);
    }

    /**
     * Map User và tính toán thống kê tháng sang MonthlyAttendanceListItemResponse
     * DTO.
     */
    public MonthlyAttendanceListItemResponse mapToMonthlyListItem(
            User user,
            java.util.List<Attendance> userAttendances,
            int workingDays,
            LocalDate startDate,
            LocalDate endDate) {

        MonthlyAttendanceListItemResponse item = new MonthlyAttendanceListItemResponse();
        item.setUserId(user.getId());
        item.setEmployeeName(user.getFullName());
        item.setAvatarUrl(user.getAvatarUrl());

        int presentDays = 0;
        int lateDays = 0;
        int absentDays = 0;
        int leaveDays = 0;
        int offDays = 0;
        int actualWorkedDays = 0;
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        int totalLateMinutes = 0;
        int totalEarlyMinutes = 0;

        for (Attendance attendance : userAttendances) {
            String status = attendance.getAttendanceStatus();
            if (status == null) {
                offDays++;
            } else {
                switch (status) {
                    case "ON_TIME":
                    case "APPROVED_PRESENT":
                    case "APPROVED_LATE":
                    case "APPROVED_EARLY_LEAVE":
                        presentDays++;
                        break;
                    case "LATE":
                        lateDays++;
                        break;
                    case "ABSENT":
                        absentDays++;
                        break;
                    case "APPROVED_ABSENCE":
                        leaveDays++;
                        break;
                    default:
                        offDays++;
                        break;
                }
            }

            if (attendance.getCheckInTime() != null) {
                actualWorkedDays++;
            }

            if (attendance.getLateMinutes() != null) {
                totalLateMinutes += attendance.getLateMinutes();
            }
            if (attendance.getEarlyMinutes() != null) {
                totalEarlyMinutes += attendance.getEarlyMinutes();
            }

            // Tính giờ làm việc: ưu tiên actualWorkHours > 0, fallback tính từ checkIn/checkOut và trừ lunch break
            BigDecimal workHours = BigDecimal.ZERO;

            if (attendance.getActualWorkHours() != null &&
                    attendance.getActualWorkHours().compareTo(BigDecimal.ZERO) > 0) {
                // Có actualWorkHours và > 0 → dùng nó (đã trừ lunch break)
                workHours = attendance.getActualWorkHours();
            } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                // Không có actualWorkHours hoặc = 0 → tính từ checkIn/checkOut và trừ lunch break (11:00-13:00 = 120 phút)
                long totalMinutes = java.time.Duration.between(
                        attendance.getCheckInTime(),
                        attendance.getCheckOutTime()).toMinutes();
                
                LocalTime checkInLocalTime = attendance.getCheckInTime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime();
                LocalTime checkOutLocalTime = attendance.getCheckOutTime()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime();
                
                long lunchBreakMinutes = 0;
                LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
                LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
                if (checkInLocalTime.isBefore(LUNCH_BREAK_START)
                        && checkOutLocalTime.isAfter(LUNCH_BREAK_END)) {
                    lunchBreakMinutes = 120;
                }
                
                long adjustedMinutes = totalMinutes - lunchBreakMinutes;
                if (adjustedMinutes < 0) {
                    adjustedMinutes = 0;
                }
                workHours = BigDecimal.valueOf(adjustedMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            }
            // Nếu không có thông tin nào → workHours = 0 (không cộng vào)

            totalWorkHours = totalWorkHours.add(workHours);
        }

        item.setWorkingDays(workingDays);
        item.setPresentDays(presentDays);
        item.setLateDays(lateDays);
        item.setAbsentDays(absentDays);
        item.setLeaveDays(leaveDays);
        item.setOffDays(offDays);
        item.setActualWorkedDays(actualWorkedDays);
        item.setTotalLateMinutes(totalLateMinutes);
        item.setTotalEarlyMinutes(totalEarlyMinutes);

        // Chuyển đổi BigDecimal totalWorkHours sang giờ và phút
        long totalHoursLong = totalWorkHours.longValue();
        BigDecimal fractionalHours = totalWorkHours.subtract(BigDecimal.valueOf(totalHoursLong));
        long remainingMinutes = fractionalHours.multiply(BigDecimal.valueOf(60))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValue();

        item.setTotalWorkedHours(totalHoursLong);
        item.setTotalWorkedMinutes(remainingMinutes);
        item.setTotalWorkedDisplay(String.format("%d hr %02d min", totalHoursLong, remainingMinutes));

        return item;
    }
}
