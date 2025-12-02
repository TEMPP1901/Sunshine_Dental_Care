package sunshine_dental_care.services.impl.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceReportService {

    private final UserRepo userRepo;
    private final DepartmentRepo departmentRepo;
    private final AttendanceRepository attendanceRepository;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AttendanceStatusCalculator attendanceStatusCalculator;

    // Lấy tổng kết chấm công theo ngày
    public List<DailySummaryResponse> getDailySummary(LocalDate workDate) {
        log.info("Getting daily summary for date: {}", workDate);

        List<Department> departments = departmentRepo.findAllByOrderByDepartmentNameAsc();
        List<DailySummaryResponse> summaries = new ArrayList<>();
        List<User> eligibleUsers = getEligibleUsersForAttendance(null);
        List<Attendance> allAttendances = attendanceRepository.findAll().stream()
                .filter(a -> a.getWorkDate().equals(workDate))
                .toList();

        Map<Integer, Attendance> attendanceByUserId = allAttendances.stream()
                .collect(Collectors.toMap(
                        Attendance::getUserId,
                        a -> a,
                        (a1, a2) -> a1
                ));

        for (Department dept : departments) {
            DailySummaryResponse summary = new DailySummaryResponse();
            summary.setDepartmentId(dept.getId());
            summary.setDepartmentName(dept.getDepartmentName());

            List<User> deptUsers = eligibleUsers.stream()
                    .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(dept.getId()))
                    .toList();

            int totalEmployees = deptUsers.size();
            summary.setTotalEmployees(totalEmployees);
            summary.setMale(0);
            summary.setFemale(0);

            // Kiểm tra số lượng nhân viên hợp lệ, nếu không có thì bỏ qua các tính toán
            if (totalEmployees == 0) {
                summary.setPresent(0);
                summary.setPresentPercent(BigDecimal.ZERO);
                summary.setLate(0);
                summary.setLatePercent(BigDecimal.ZERO);
                summary.setAbsent(0);
                summary.setAbsentPercent(BigDecimal.ZERO);
                summary.setLeave(0);
                summary.setLeavePercent(BigDecimal.ZERO);
                summary.setOffday(0);
                summary.setOffdayPercent(BigDecimal.ZERO);
                summaries.add(summary);
                continue;
            }

            int present = 0;
            int late = 0;
            int absent = 0;
            int leave = 0;
            int offday = 0;

            for (User user : deptUsers) {
                Attendance attendance = attendanceByUserId.get(user.getId());
                if (attendance == null) {
                    offday++;
                    continue;
                }
                String status = attendance.getAttendanceStatus();
                if (status == null) {
                    offday++;
                } else if ("ON_TIME".equals(status)) {
                    present++;
                } else if ("LATE".equals(status)) {
                    late++;
                } else if ("ABSENT".equals(status)) {
                    absent++;
                } else if ("APPROVED_ABSENCE".equals(status)) {
                    leave++;
                } else {
                    offday++;
                }
            }

            summary.setPresent(present);
            summary.setPresentPercent(calculatePercent(present, totalEmployees));
            summary.setLate(late);
            summary.setLatePercent(calculatePercent(late, totalEmployees));
            summary.setAbsent(absent);
            summary.setAbsentPercent(calculatePercent(absent, totalEmployees));
            summary.setLeave(leave);
            summary.setLeavePercent(calculatePercent(leave, totalEmployees));
            summary.setOffday(offday);
            summary.setOffdayPercent(calculatePercent(offday, totalEmployees));

            summaries.add(summary);
        }

        return summaries;
    }

    // Lấy danh sách chấm công theo ngày
    public Page<DailyAttendanceListItemResponse> getDailyAttendanceList(LocalDate workDate,
                                                                        Integer departmentId,
                                                                        Integer clinicId,
                                                                        int page,
                                                                        int size) {
        log.info("Getting daily attendance list for date: {}, departmentId: {}, clinicId: {}",
                workDate, departmentId, clinicId);

        List<User> eligibleUsers = getEligibleUsersForAttendance(departmentId);

        List<Attendance> attendances;
        if (clinicId != null) {
            attendances = attendanceRepository.findByClinicIdAndWorkDate(clinicId, workDate);
        } else {
            attendances = attendanceRepository.findAll().stream()
                    .filter(a -> a.getWorkDate().equals(workDate))
                    .toList();
        }

        Map<Integer, Attendance> attendanceByUserId = attendances.stream()
                .collect(Collectors.toMap(
                        Attendance::getUserId,
                        a -> a,
                        (a1, a2) -> a1
                ));

        List<DailyAttendanceListItemResponse> items = new ArrayList<>();

        for (User user : eligibleUsers) {
            DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
            item.setId(null);
            item.setUserId(user.getId());
            item.setEmployeeName(user.getFullName());
            item.setAvatarUrl(user.getAvatarUrl());

            List<UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
            if (roles != null && !roles.isEmpty() && roles.get(0).getRole() != null) {
                item.setJobTitle(roles.get(0).getRole().getRoleName());
            }

            Attendance attendance = attendanceByUserId.get(user.getId());
            if (attendance == null) {
                // Nếu không có dữ liệu chấm công thì mặc định là vắng mặt
                item.setStatus("Absent");
                item.setStatusColor("red");
                item.setCheckInTime(null);
                item.setCheckOutTime(null);
                item.setRemarks("Fixed Attendance");
            } else {
                fillAttendanceItemWithData(item, attendance, user, workDate);
            }

            items.add(item);
        }

        items.sort((a, b) -> a.getEmployeeName().compareToIgnoreCase(b.getEmployeeName()));

        int startIdx = page * size;
        int endIdx = Math.min(startIdx + size, items.size());
        List<DailyAttendanceListItemResponse> pagedItems = startIdx < items.size()
                ? items.subList(startIdx, endIdx)
                : new ArrayList<>();

        return new PageImpl<>(pagedItems, PageRequest.of(page, size), items.size());
    }

    // Lấy tổng kết chấm công theo tháng
    public List<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month) {
        log.info("Getting monthly summary for year: {}, month: {}", year, month);

        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // Tính số ngày làm việc trong tháng (trừ thứ 7, CN)
        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            java.time.DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        List<Department> departments = departmentRepo.findAll();
        List<MonthlySummaryResponse> summaries = new ArrayList<>();

        List<User> allUsers = getEligibleUsersForAttendance(null);
        List<Attendance> allAttendances = attendanceRepository.findAll().stream()
                .filter(a -> {
                    LocalDate workDate = a.getWorkDate();
                    return workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
                })
                .toList();

        Map<Integer, List<Attendance>> attendanceByUserId = allAttendances.stream()
                .collect(Collectors.groupingBy(Attendance::getUserId));

        for (Department dept : departments) {
            MonthlySummaryResponse summary = new MonthlySummaryResponse();
            summary.setDepartmentId(dept.getId());
            summary.setDepartmentName(dept.getDepartmentName());
            summary.setWorkingDays(workingDays);

            List<User> deptUsers = allUsers.stream()
                    .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(dept.getId()))
                    .toList();

            int totalEmployees = deptUsers.size();
            summary.setTotalEmployees(totalEmployees);

            // Nếu không có nhân viên thì bỏ qua tính toán
            if (totalEmployees == 0) {
                summary.setPresent(0);
                summary.setLate(0);
                summary.setAbsent(0);
                summary.setLeave(0);
                summary.setOffday(0);
                summary.setTotalAttendance(0);
                summaries.add(summary);
                continue;
            }

            int present = 0;
            int late = 0;
            int absent = 0;
            int leave = 0;
            int offday = 0;
            int totalAttendance = 0;

            for (User user : deptUsers) {
                List<Attendance> userAttendances = attendanceByUserId.getOrDefault(user.getId(), new ArrayList<>());
                totalAttendance += userAttendances.size();

                for (Attendance attendance : userAttendances) {
                    String status = attendance.getAttendanceStatus();
                    if (status == null) {
                        offday++;
                    } else if ("ON_TIME".equals(status)) {
                        present++;
                    } else if ("LATE".equals(status)) {
                        late++;
                    } else if ("ABSENT".equals(status)) {
                        absent++;
                    } else if ("APPROVED_ABSENCE".equals(status)) {
                        leave++;
                    } else {
                        offday++;
                    }
                }
            }

            summary.setPresent(present);
            summary.setLate(late);
            summary.setAbsent(absent);
            summary.setLeave(leave);
            summary.setOffday(offday);
            summary.setTotalAttendance(totalAttendance);

            summaries.add(summary);
        }

        return summaries;
    }

    // Lấy danh sách chấm công theo tháng
    public Page<MonthlyAttendanceListItemResponse> getMonthlyAttendanceList(Integer year,
                                                                            Integer month,
                                                                            Integer departmentId,
                                                                            Integer clinicId,
                                                                            int page,
                                                                            int size) {
        log.info("Getting monthly attendance list for year: {}, month: {}, departmentId: {}, clinicId: {}",
                year, month, departmentId, clinicId);

        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<User> eligibleUsers = getEligibleUsersForAttendance(departmentId);
        List<Attendance> attendances;
        if (clinicId != null) {
            attendances = attendanceRepository.findAll().stream()
                    .filter(a -> {
                        LocalDate workDate = a.getWorkDate();
                        return a.getClinicId() != null && a.getClinicId().equals(clinicId)
                                && workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
                    })
                    .toList();
        } else {
            attendances = attendanceRepository.findAll().stream()
                    .filter(a -> {
                        LocalDate workDate = a.getWorkDate();
                        return workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
                    })
                    .toList();
        }

        Map<Integer, List<Attendance>> attendanceByUserId = attendances.stream()
                .collect(Collectors.groupingBy(Attendance::getUserId));

        List<MonthlyAttendanceListItemResponse> items = new ArrayList<>();

        for (User user : eligibleUsers) {
            MonthlyAttendanceListItemResponse item = new MonthlyAttendanceListItemResponse();
            item.setUserId(user.getId());
            item.setEmployeeName(user.getFullName());
            item.setAvatarUrl(user.getAvatarUrl());

            List<UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
            if (roles != null && !roles.isEmpty() && roles.get(0).getRole() != null) {
                item.setJobTitle(roles.get(0).getRole().getRoleName());
            }

            List<Attendance> userAttendances = attendanceByUserId.getOrDefault(user.getId(), new ArrayList<>());

            int workingDays = 0;
            int presentDays = 0;
            int lateDays = 0;
            int absentDays = 0;
            int leaveDays = 0;
            int offDays = 0;
            long totalMinutes = 0;

            for (Attendance attendance : userAttendances) {
                workingDays++;
                String status = attendance.getAttendanceStatus();
                if (status == null) {
                    offDays++;
                } else if ("ON_TIME".equals(status)) {
                    presentDays++;
                } else if ("LATE".equals(status)) {
                    lateDays++;
                } else if ("ABSENT".equals(status)) {
                    absentDays++;
                } else if ("APPROVED_ABSENCE".equals(status)) {
                    leaveDays++;
                } else {
                    offDays++;
                }

                if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                    long minutes = java.time.Duration.between(
                            attendance.getCheckInTime(),
                            attendance.getCheckOutTime()
                    ).toMinutes();
                    totalMinutes += minutes;
                }
            }

            item.setWorkingDays(workingDays);
            item.setPresentDays(presentDays);
            item.setLateDays(lateDays);
            item.setAbsentDays(absentDays);
            item.setLeaveDays(leaveDays);
            item.setOffDays(offDays);

            long totalHours = totalMinutes / 60;
            long remainingMinutes = totalMinutes % 60;
            item.setTotalWorkedHours(totalHours);
            item.setTotalWorkedMinutes(remainingMinutes);
            item.setTotalWorkedDisplay(String.format("%d hr %02d min", totalHours, remainingMinutes));

            items.add(item);
        }

        items.sort((a, b) -> a.getEmployeeName().compareToIgnoreCase(b.getEmployeeName()));

        int startIdx = page * size;
        int endIdx = Math.min(startIdx + size, items.size());
        List<MonthlyAttendanceListItemResponse> pagedItems = startIdx < items.size()
                ? items.subList(startIdx, endIdx)
                : new ArrayList<>();

        return new PageImpl<>(pagedItems, PageRequest.of(page, size), items.size());
    }

    // Lọc các nhân viên đủ điều kiện áp dụng chấm công
    private List<User> getEligibleUsersForAttendance(Integer departmentId) {
        Stream<User> stream = userRepo.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));

        if (departmentId != null) {
            stream = stream.filter(u -> u.getDepartment() != null
                    && u.getDepartment().getId().equals(departmentId));
        }

        // Loại các user có vai trò không hợp lệ chấm công
        return stream
                .filter(u -> {
                    List<UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                    return roles != null && !roles.isEmpty()
                            && !attendanceStatusCalculator.hasForbiddenRole(roles);
                })
                .toList();
    }

    // Điền thông tin chấm công chi tiết cho DailyAttendanceListItemResponse
    private void fillAttendanceItemWithData(DailyAttendanceListItemResponse item,
                                            Attendance attendance,
                                            User user,
                                            LocalDate workDate) {
        item.setId(attendance.getId());
        item.setCheckInTime(attendance.getCheckInTime());
        item.setCheckOutTime(attendance.getCheckOutTime());

        String status = attendance.getAttendanceStatus();
        boolean hasCheckIn = attendance.getCheckInTime() != null;
        boolean hasCheckOut = attendance.getCheckOutTime() != null;
        
        // Phân loại trạng thái chấm công (rất quan trọng, xử lý nghiệp vụ phần hiển thị chấm công)
        if (status == null) {
            if (hasCheckIn || hasCheckOut) {
                // Nếu có check-in hoặc check-out thì được xem là có mặt
                if (hasCheckIn && hasCheckOut) {
                    item.setStatus("Present");
                    item.setStatusColor("green");
                } else if (hasCheckIn) {
                    item.setStatus("Present");
                    item.setStatusColor("green");
                } else {
                    item.setStatus("Absent");
                    item.setStatusColor("red");
                }
            } else {
                // Không có check-in, check-out và trạng thái null thì xem là ngày nghỉ
                item.setStatus("Offday");
                item.setStatusColor("gray");
            }
        } else if ("ON_TIME".equals(status)) {
            item.setStatus("Present");
            item.setStatusColor("green");
        } else if ("LATE".equals(status)) {
            item.setStatus("Late");
            item.setStatusColor("orange");
        } else if ("ABSENT".equals(status)) {
            item.setStatus("Absent");
            item.setStatusColor("red");
        } else if ("APPROVED_ABSENCE".equals(status)) {
            item.setStatus("Approved Leave");
            item.setStatusColor("blue");
        } else if ("APPROVED_LATE".equals(status)) {
            item.setStatus("Approved Late");
            item.setStatusColor("blue");
        } else if ("APPROVED_PRESENT".equals(status)) {
            item.setStatus("Approved Present");
            item.setStatusColor("blue");
        } else {
            item.setStatus("Unknown");
            item.setStatusColor("gray");
        }

        // Nếu là làm thêm giờ thì đánh dấu lại trạng thái là Overtime
        if (attendance.getIsOvertime() != null && attendance.getIsOvertime()) {
            item.setStatus("Overtime");
            item.setStatusColor("blue");
        }

        if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            long minutes = java.time.Duration.between(
                    attendance.getCheckInTime(),
                    attendance.getCheckOutTime()
            ).toMinutes();
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
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
    }

    // Tính phần trăm số lượng nhân viên/thống kê
    private BigDecimal calculatePercent(int value, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // Định dạng giờ/phút am pm phục vụ hiển thị ca làm việc
    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour < 12 ? "am" : "pm";
        int displayHour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
        return String.format("%d.%02d%s", displayHour, minute, period);
    }
}
