package sunshine_dental_care.dto.hrDTO.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.utils.WorkHoursConstants;

/**
 * Mapper để chuyển đổi Attendance entities sang Report Response DTOs.
 * Tách logic mapping từ AttendanceReportService để code gọn hơn, theo pattern
 * của Reception module.
 */
@Component
@RequiredArgsConstructor
public class AttendanceReportMapper {

    private final DoctorScheduleRepo doctorScheduleRepo;

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

        // Kiểm tra note có chứa explanation request không để xử lý mâu thuẫn
        String note = attendance.getNote();
        boolean hasExplanationRequest = note != null && note.contains("[EXPLANATION_REQUEST:");
        String explanationType = null;
        if (hasExplanationRequest) {
            // Trích xuất loại explanation từ note: [EXPLANATION_REQUEST:ABSENT], [EXPLANATION_REQUEST:LATE], etc.
            Pattern pattern = Pattern.compile("\\[EXPLANATION_REQUEST:([^\\]]+)\\]");
            Matcher matcher = pattern.matcher(note);
            if (matcher.find()) {
                explanationType = matcher.group(1);
            }
        }

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
                    // Nếu có explanation request cho ABSENT nhưng status là Present → có mâu thuẫn
                    // Ưu tiên hiển thị status theo explanation request nếu chưa được xử lý
                    if (hasExplanationRequest && "ABSENT".equals(explanationType)) {
                        item.setStatus("Present (Pending Explanation)");
                        item.setStatusColor("orange");
                    } else {
                        item.setStatus("Present");
                        item.setStatusColor("green");
                    }
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
        // Không tính giờ làm nếu status là ABSENT hoặc APPROVED_ABSENCE (vắng mặt/nghỉ phép được duyệt)
        if (hasCheckIn && hasCheckOut && !"ABSENT".equals(status) && !"APPROVED_ABSENCE".equals(status)) {
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
                
                // Kiểm tra xem có phải bác sĩ không (dựa vào có schedule hay không)
                List<DoctorSchedule> userSchedules = doctorScheduleRepo
                        .findByUserIdAndClinicIdAndWorkDate(user.getId(), attendance.getClinicId(), workDate);
                boolean isDoctor = !userSchedules.isEmpty();
                
                // Sử dụng WorkHoursConstants để tính lunch break (nhân viên mới trừ, bác sĩ không trừ)
                int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                        checkInLocalTime, checkOutLocalTime, isDoctor);
                
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

        // Kiểm tra xem có phải bác sĩ không (dựa vào có schedule hay không)
        List<DoctorSchedule> schedulesByClinic = doctorScheduleRepo
                .findByUserIdAndClinicIdAndWorkDate(user.getId(), attendance.getClinicId(), workDate);
        
        // Nếu không tìm thấy schedule theo clinic, thử tìm tất cả schedule của bác sĩ trong ngày
        List<DoctorSchedule> allSchedules = schedulesByClinic.isEmpty() 
                ? doctorScheduleRepo.findByDoctorIdAndWorkDate(user.getId(), workDate)
                : schedulesByClinic;
        
        boolean isDoctor = !allSchedules.isEmpty();
        
        DoctorSchedule matchedSchedule = null;
        boolean shiftMismatch = false;
        String shiftMismatchWarning = "";

        if (isDoctor) {
            // Bác sĩ có schedule - tìm schedule khớp với shiftType của attendance
            String shiftType = attendance.getShiftType();
            if (shiftType != null && !shiftType.equals("FULL_DAY")) {
                // Tìm schedule khớp với shiftType (MORNING hoặc AFTERNOON)
                for (DoctorSchedule schedule : allSchedules) {
                    // Xác định shift type từ sta   rtTime của schedule
                    String scheduleShiftType = WorkHoursConstants.determineShiftType(schedule.getStartTime());
                    if (shiftType.equals(scheduleShiftType)) {
                        matchedSchedule = schedule;
                        break;
                    }
                }
            }
            // Set shift display: ưu tiên schedule khớp, nếu không có thì dùng giờ mặc định theo shiftType
            LocalTime shiftStart, shiftEnd;
            if (matchedSchedule != null) {
                // Có schedule khớp với shiftType
                shiftStart = matchedSchedule.getStartTime();
                shiftEnd = matchedSchedule.getEndTime();
            } else if (shiftType != null) {
                // Không có schedule khớp: dùng giờ mặc định theo shiftType
                if ("MORNING".equals(shiftType)) {
                    shiftStart = WorkHoursConstants.MORNING_SHIFT_START;
                    shiftEnd = WorkHoursConstants.MORNING_SHIFT_END;
                } else if ("AFTERNOON".equals(shiftType)) {
                    shiftStart = WorkHoursConstants.AFTERNOON_SHIFT_START;
                    shiftEnd = WorkHoursConstants.AFTERNOON_SHIFT_END;
                } else {
                    shiftStart = WorkHoursConstants.EMPLOYEE_START_TIME;
                    shiftEnd = WorkHoursConstants.EMPLOYEE_END_TIME;
                }
            } else {
                // Không có shiftType: skip (sẽ hiển thị "-" ở frontend)
                shiftStart = null;
                shiftEnd = null;
            }
            
            if (shiftStart != null && shiftEnd != null) {
                item.setShiftStartTime(shiftStart);
                item.setShiftEndTime(shiftEnd);
                item.setShiftDisplay(formatTime(shiftStart) + " - " + formatTime(shiftEnd));
                long shiftHours = java.time.Duration.between(shiftStart, shiftEnd).toHours();
                item.setShiftHours(shiftHours + " hr Shift: A");

                // Kiểm tra shift mismatch: nếu có check-in/check-out nhưng không khớp với shift được gán
                if (hasCheckIn && hasCheckOut && matchedSchedule != null) {
                    LocalTime checkInLocalTime = attendance.getCheckInTime()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime();
                    LocalTime checkOutLocalTime = attendance.getCheckOutTime()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime();

                    // Kiểm tra xem check-in/check-out có nằm trong khoảng shift không
                    // Cho phép sai lệch 1 giờ (60 phút) để xử lý trường hợp trễ/muộn
                    long checkInDiff = java.time.Duration.between(shiftStart, checkInLocalTime).toMinutes();
                    long checkOutDiff = java.time.Duration.between(checkOutLocalTime, shiftEnd).toMinutes();

                    // Nếu check-in sớm hơn shift start quá 2 giờ hoặc muộn hơn shift end quá 2 giờ → mismatch
                    if (checkInDiff < -120 || checkOutDiff < -120 || 
                        (checkInLocalTime.isAfter(shiftEnd.plusHours(1)) && checkOutLocalTime.isAfter(shiftEnd.plusHours(1)))) {
                        shiftMismatch = true;
                        shiftMismatchWarning = String.format(" [SHIFT_MISMATCH: Worked %s-%s but scheduled %s-%s]", 
                            formatTime(checkInLocalTime), formatTime(checkOutLocalTime),
                            formatTime(shiftStart), formatTime(shiftEnd));
                    }
                }
            }
        } else {
            // Nhân viên không có schedule → dùng giờ mặc định
            LocalTime defaultStart = WorkHoursConstants.EMPLOYEE_START_TIME;
            LocalTime defaultEnd = WorkHoursConstants.EMPLOYEE_END_TIME;
            item.setShiftStartTime(defaultStart);
            item.setShiftEndTime(defaultEnd);
            item.setShiftDisplay(formatTime(defaultStart) + " - " + formatTime(defaultEnd));
            // Nhân viên: 8 giờ làm việc (10 giờ tổng - 2 giờ nghỉ trưa)
            item.setShiftHours(WorkHoursConstants.EMPLOYEE_EXPECTED_HOURS + " hr Shift: A");
        }

        // Xử lý remarks: thêm cảnh báo nếu có shift mismatch hoặc mâu thuẫn status
        StringBuilder remarksBuilder = new StringBuilder();
        if (note != null && !note.trim().isEmpty()) {
            remarksBuilder.append(note);
        }
        if (shiftMismatch) {
            if (remarksBuilder.length() > 0) {
                remarksBuilder.append(" | ");
            }
            remarksBuilder.append(shiftMismatchWarning);
        }
        // Nếu có mâu thuẫn giữa status và explanation request, thêm cảnh báo
        if (hasExplanationRequest && explanationType != null) {
            if (("ABSENT".equals(explanationType) && !"ABSENT".equals(status) && !"APPROVED_ABSENCE".equals(status)) ||
                ("LATE".equals(explanationType) && !"LATE".equals(status) && !"APPROVED_LATE".equals(status))) {
                if (remarksBuilder.length() > 0) {
                    remarksBuilder.append(" | ");
                }
                remarksBuilder.append(String.format(" [STATUS_MISMATCH: Status is %s but explanation request is for %s]", 
                    status != null ? status : "null", explanationType));
            }
        }
        
        item.setRemarks(remarksBuilder.length() > 0 ? remarksBuilder.toString() : "Fixed Attendance");

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

        int offDays = 0;
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        int totalLateMinutes = 0;
        int totalEarlyMinutes = 0;

        // Tính tổng giờ làm việc, late minutes, early minutes (theo records)
        for (Attendance attendance : userAttendances) {
            String status = attendance.getAttendanceStatus();
            if (status == null) {
                offDays++;
            }

            if (attendance.getLateMinutes() != null) {
                totalLateMinutes += attendance.getLateMinutes();
            }
            if (attendance.getEarlyMinutes() != null) {
                totalEarlyMinutes += attendance.getEarlyMinutes();
            }

            // Tính giờ làm việc: ưu tiên actualWorkHours > 0, fallback tính từ checkIn/checkOut và trừ lunch break
            // Không tính giờ làm nếu status là ABSENT hoặc APPROVED_ABSENCE (vắng mặt/nghỉ phép được duyệt)
            BigDecimal workHours = BigDecimal.ZERO;
            
            // Nếu status là ABSENT hoặc APPROVED_ABSENCE, không tính giờ làm dù có check-in/check-out
            if ("ABSENT".equals(status) || "APPROVED_ABSENCE".equals(status)) {
                workHours = BigDecimal.ZERO;
            } else if (attendance.getActualWorkHours() != null &&
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
                
                // Kiểm tra xem có phải bác sĩ không (dựa vào shiftType)
                boolean isDoctorForMonthly = attendance.getShiftType() != null 
                        && (attendance.getShiftType().equals("MORNING") || attendance.getShiftType().equals("AFTERNOON"));
                
                // Sử dụng WorkHoursConstants để tính lunch break (nhân viên mới trừ, bác sĩ không trừ)
                int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                        checkInLocalTime, checkOutLocalTime, isDoctorForMonthly);
                
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

        // Kiểm tra user có phải bác sĩ không (có shiftType MORNING/AFTERNOON)
        boolean isDoctor = userAttendances.stream()
                .anyMatch(a -> a.getShiftType() != null && 
                    ("MORNING".equals(a.getShiftType()) || "AFTERNOON".equals(a.getShiftType())));
        
        // Tính số ngày có present records
        // Nếu là bác sĩ: tính theo tỷ lệ ca (2 ca/ngày = 1 ngày, 1 ca/ngày = 0.5 ngày)
        // Nếu là nhân viên: tính theo ngày unique
        double presentDaysByDate;
        if (isDoctor) {
            // Bác sĩ: group theo workDate và đếm số ca present trên từng ngày
            java.util.Map<LocalDate, Long> presentShiftsByDate = userAttendances.stream()
                    .filter(a -> {
                        String status = a.getAttendanceStatus();
                        return status != null && (
                            "ON_TIME".equals(status) ||
                            "APPROVED_PRESENT".equals(status) ||
                            "APPROVED_LATE".equals(status) ||
                            "APPROVED_EARLY_LEAVE".equals(status)
                        );
                    })
                    .filter(a -> a.getWorkDate() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                        Attendance::getWorkDate,
                        java.util.stream.Collectors.counting()
                    ));
            
            // Tính tổng số ngày present: nếu có 2 ca = 1 ngày, 1 ca = 0.5 ngày
            presentDaysByDate = presentShiftsByDate.values().stream()
                    .mapToDouble(count -> count >= 2 ? 1.0 : 0.5)
                    .sum();
        } else {
            // Nhân viên: đếm theo ngày unique
            presentDaysByDate = userAttendances.stream()
                    .filter(a -> {
                        String status = a.getAttendanceStatus();
                        return status != null && (
                            "ON_TIME".equals(status) ||
                            "APPROVED_PRESENT".equals(status) ||
                            "APPROVED_LATE".equals(status) ||
                            "APPROVED_EARLY_LEAVE".equals(status)
                        );
                    })
                    .map(Attendance::getWorkDate)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
        }
        
        // Tính số ngày có LATE records
        // Nếu là bác sĩ: tính theo tỷ lệ ca (2 ca late = 1 ngày, 1 ca late = 0.5 ngày)
        // Nếu là nhân viên: tính theo ngày unique
        double lateDaysByDate;
        if (isDoctor) {
            // Bác sĩ: group theo workDate và đếm số ca LATE trên từng ngày
            java.util.Map<LocalDate, Long> lateShiftsByDate = userAttendances.stream()
                    .filter(a -> "LATE".equals(a.getAttendanceStatus()))
                    .filter(a -> a.getWorkDate() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                        Attendance::getWorkDate,
                        java.util.stream.Collectors.counting()
                    ));
            
            // Tính tổng số ngày late: nếu có 2 ca = 1 ngày, 1 ca = 0.5 ngày
            lateDaysByDate = lateShiftsByDate.values().stream()
                    .mapToDouble(count -> count >= 2 ? 1.0 : 0.5)
                    .sum();
        } else {
            // Nhân viên: đếm theo ngày unique
            lateDaysByDate = userAttendances.stream()
                    .filter(a -> "LATE".equals(a.getAttendanceStatus()))
                    .map(Attendance::getWorkDate)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
        }
        
        // Tính số ngày có leave records
        // Nếu là bác sĩ: tính theo tỷ lệ ca (2 ca nghỉ = 1 ngày, 1 ca nghỉ = 0.5 ngày)
        // Nếu là nhân viên: tính theo ngày unique
        double leaveDaysByDate;
        if (isDoctor) {
            // Bác sĩ: group theo workDate và đếm số ca APPROVED_ABSENCE trên từng ngày
            java.util.Map<LocalDate, Long> leaveShiftsByDate = userAttendances.stream()
                    .filter(a -> "APPROVED_ABSENCE".equals(a.getAttendanceStatus()))
                    .filter(a -> a.getWorkDate() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                        Attendance::getWorkDate,
                        java.util.stream.Collectors.counting()
                    ));
            
            // Tính tổng số ngày nghỉ: nếu có 2 ca = 1 ngày, 1 ca = 0.5 ngày
            leaveDaysByDate = leaveShiftsByDate.values().stream()
                    .mapToDouble(count -> count >= 2 ? 1.0 : 0.5)
                    .sum();
        } else {
            // Nhân viên: đếm theo ngày unique
            leaveDaysByDate = userAttendances.stream()
                    .filter(a -> "APPROVED_ABSENCE".equals(a.getAttendanceStatus()))
                    .map(Attendance::getWorkDate)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
        }
        
        // Tính số ngày có check-in
        // Nếu là bác sĩ: tính theo tỷ lệ ca (2 ca check-in = 1 ngày, 1 ca check-in = 0.5 ngày)
        // Nếu là nhân viên: tính theo ngày unique
        double actualWorkedDaysByDate;
        if (isDoctor) {
            // Bác sĩ: group theo workDate và đếm số ca có check-in trên từng ngày
            java.util.Map<LocalDate, Long> workedShiftsByDate = userAttendances.stream()
                    .filter(a -> a.getCheckInTime() != null)
                    .filter(a -> a.getWorkDate() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                        Attendance::getWorkDate,
                        java.util.stream.Collectors.counting()
                    ));
            
            // Tính tổng số ngày làm việc: nếu có 2 ca = 1 ngày, 1 ca = 0.5 ngày
            actualWorkedDaysByDate = workedShiftsByDate.values().stream()
                    .mapToDouble(count -> count >= 2 ? 1.0 : 0.5)
                    .sum();
        } else {
            // Nhân viên: đếm theo ngày unique
            actualWorkedDaysByDate = userAttendances.stream()
                    .filter(a -> a.getCheckInTime() != null)
                    .map(Attendance::getWorkDate)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
        }
        
        // Tính absentDays: workingDays - presentDaysByDate - leaveDaysByDate
        // Nếu không có records nào (một tháng không check-in/check-out),
        // thì nhân viên đó vắng cả tháng → absentDays = workingDays
        int absentDays;
        if (userAttendances.isEmpty() && workingDays > 0) {
            absentDays = workingDays;
        } else {
            // Tính số ngày chưa có records hoặc không present/leave = workingDays - presentDaysByDate - leaveDaysByDate
            // Những ngày này được coi là absent
            double missingDays = workingDays - presentDaysByDate - leaveDaysByDate;
            absentDays = (int) Math.max(0, Math.ceil(missingDays));
        }
        
        item.setWorkingDays(workingDays);
        // Giữ nguyên giá trị decimal (0.5 cho bác sĩ làm 1/2 ca)
        item.setPresentDays(presentDaysByDate);
        item.setLateDays(lateDaysByDate);
        item.setAbsentDays(absentDays); // Tính từ workingDays - presentDays - leaveDays
        item.setLeaveDays(leaveDaysByDate);
        item.setOffDays(offDays);
        item.setActualWorkedDays(actualWorkedDaysByDate);
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
