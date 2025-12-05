package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
import sunshine_dental_care.dto.hrDTO.mapper.AttendanceReportMapper;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DepartmentRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceReportService {

    private final UserRepo userRepo;
    private final DepartmentRepo departmentRepo;
    private final AttendanceRepository attendanceRepository;
    private final UserRoleRepo userRoleRepo;
    private final AttendanceStatusCalculator attendanceStatusCalculator;
    private final AttendanceReportMapper attendanceReportMapper;

    // Lấy tổng kết chấm công theo ngày cho từng phòng ban
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
                        (a1, a2) -> a1));

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

            // Nếu không có nhân viên trong phòng ban thì bỏ qua
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

    // Lấy danh sách chi tiết chấm công theo ngày, hỗ trợ phân trang
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

        // Nhóm attendance theo userId, bác sĩ có thể có nhiều ca trong ngày
        Map<Integer, List<Attendance>> attendanceByUserId = attendances.stream()
                .collect(Collectors.groupingBy(Attendance::getUserId));

        List<DailyAttendanceListItemResponse> items = new ArrayList<>();

        for (User user : eligibleUsers) {
            List<UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());

            boolean isDoctor = roles != null && roles.stream()
                    .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

            // Nếu là bác sĩ thì cập nhật trạng thái vắng theo lịch
            if (isDoctor) {
                try {
                    attendanceStatusCalculator.markAbsentForDoctorBasedOnSchedule(user.getId(), workDate);
                } catch (Exception e) {
                    log.warn("Error marking absent for doctor {} on {}: {}", user.getId(), workDate, e.getMessage(), e);
                }

                // Bác sĩ có thể có nhiều ca trong ngày, duyệt qua từng ca
                List<Attendance> doctorAttendances = attendanceRepository.findAllByUserIdAndWorkDate(user.getId(),
                        workDate);

                if (doctorAttendances.isEmpty()) {
                    DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
                    item.setId(null);
                    item.setUserId(user.getId());
                    item.setEmployeeName(user.getFullName());
                    item.setAvatarUrl(user.getAvatarUrl());
                    if (roles != null && !roles.isEmpty() && roles.get(0).getRole() != null) {
                        item.setJobTitle(roles.get(0).getRole().getRoleName());
                    }
                    item.setStatus("Absent");
                    item.setStatusColor("red");
                    item.setCheckInTime(null);
                    item.setCheckOutTime(null);
                    item.setRemarks("Fixed Attendance");
                    items.add(item);
                } else {
                    // Tạo item cho từng ca làm việc của bác sĩ trong ngày
                    for (Attendance attendance : doctorAttendances) {
                        DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
                        item.setId(attendance.getId());
                        item.setUserId(user.getId());
                        item.setEmployeeName(user.getFullName());
                        item.setAvatarUrl(user.getAvatarUrl());
                        if (roles != null && !roles.isEmpty() && roles.get(0).getRole() != null) {
                            item.setJobTitle(roles.get(0).getRole().getRoleName());
                        }
                        DailyAttendanceListItemResponse mapped = attendanceReportMapper.mapToDailyListItem(attendance,
                                user, workDate);
                        copyDailyItemFields(item, mapped);
                        items.add(item);
                    }
                }
            } else {
                // Nhân viên bình thường chỉ có một bản ghi chấm công mỗi ngày
                DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
                item.setId(null);
                item.setUserId(user.getId());
                item.setEmployeeName(user.getFullName());
                item.setAvatarUrl(user.getAvatarUrl());
                if (roles != null && !roles.isEmpty() && roles.get(0).getRole() != null) {
                    item.setJobTitle(roles.get(0).getRole().getRoleName());
                }

                List<Attendance> userAttendances = attendanceByUserId.getOrDefault(user.getId(), new ArrayList<>());
                Attendance attendance = userAttendances.isEmpty() ? null : userAttendances.get(0);

                if (attendance == null) {
                    // Nếu chưa có dữ liệu chấm công, kiểm tra xem cần đánh dấu ABSENT cho nhân viên này ở clinic tương ứng không
                    Integer userClinicId = null;
                    if (roles != null && !roles.isEmpty()) {
                        for (UserRole userRole : roles) {
                            if (userRole != null) {
                                try {
                                    if (userRole.getClinic() != null) {
                                        userClinicId = userRole.getClinic().getId();
                                        if (userClinicId != null) {
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("Could not load clinic from UserRole: {}", e.getMessage());
                                }
                            }
                        }
                    }

                    if (userClinicId != null) {
                        attendanceStatusCalculator.markAbsentIfNeeded(user.getId(), userClinicId, workDate);
                        attendance = attendanceRepository
                                .findByUserIdAndClinicIdAndWorkDate(user.getId(), userClinicId, workDate)
                                .orElse(null);
                    }

                    if (attendance == null) {
                        item.setStatus("Absent");
                        item.setStatusColor("red");
                        item.setCheckInTime(null);
                        item.setCheckOutTime(null);
                        item.setRemarks("Fixed Attendance");
                    } else {
                        item.setId(attendance.getId());
                        DailyAttendanceListItemResponse mapped = attendanceReportMapper.mapToDailyListItem(attendance,
                                user, workDate);
                        copyDailyItemFields(item, mapped);
                    }
                } else {
                    item.setId(attendance.getId());
                    DailyAttendanceListItemResponse mapped = attendanceReportMapper.mapToDailyListItem(attendance, user,
                            workDate);
                    copyDailyItemFields(item, mapped);
                }

                items.add(item);
            }
        }

        items.sort((a, b) -> a.getEmployeeName().compareToIgnoreCase(b.getEmployeeName()));
        int startIdx = page * size;
        int endIdx = Math.min(startIdx + size, items.size());
        List<DailyAttendanceListItemResponse> pagedItems = startIdx < items.size()
                ? items.subList(startIdx, endIdx)
                : new ArrayList<>();

        return new PageImpl<>(pagedItems, PageRequest.of(page, size), items.size());
    }

    // Tổng kết chấm công theo tháng cho từng phòng ban
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

        // Đếm số ngày làm việc trong tháng (trừ Chủ nhật)
        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        List<Department> departments = departmentRepo.findAll();
        List<MonthlySummaryResponse> summaries = new ArrayList<>();

        List<User> allUsers = getEligibleUsersForAttendance(null);
        List<Attendance> allAttendances = attendanceRepository.findAll().stream()
                .filter(a -> {
                    LocalDate wDate = a.getWorkDate();
                    return wDate != null && !wDate.isBefore(startDate) && !wDate.isAfter(endDate);
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

            // Bỏ qua phòng ban không có nhân viên
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

    // Lấy danh sách chi tiết chấm công theo tháng, phân trang, cho toàn bộ NV phòng ban
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
            attendances = attendanceRepository.findByClinicIdAndWorkDateBetween(clinicId, startDate, endDate);
        } else {
            attendances = attendanceRepository.findByWorkDateBetween(startDate, endDate);
        }

        // Loại bỏ Chủ nhật nếu có (dữ liệu thường không lưu)
        attendances = attendances.stream()
                .filter(a -> a.getWorkDate().getDayOfWeek() != DayOfWeek.SUNDAY)
                .collect(Collectors.toList());

        log.info("Fetched {} attendance records for monthly report", attendances.size());

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

            // Đếm số ngày làm việc trong tháng (bỏ Chủ nhật)
            int workingDays = 0;
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    workingDays++;
                }
                current = current.plusDays(1);
            }

            MonthlyAttendanceListItemResponse mapped = attendanceReportMapper.mapToMonthlyListItem(
                    user, userAttendances, workingDays, startDate, endDate);
            item.setWorkingDays(mapped.getWorkingDays());
            item.setPresentDays(mapped.getPresentDays());
            item.setLateDays(mapped.getLateDays());
            item.setAbsentDays(mapped.getAbsentDays());
            item.setLeaveDays(mapped.getLeaveDays());
            item.setOffDays(mapped.getOffDays());
            item.setActualWorkedDays(mapped.getActualWorkedDays());
            item.setTotalLateMinutes(mapped.getTotalLateMinutes());
            item.setTotalEarlyMinutes(mapped.getTotalEarlyMinutes());
            item.setTotalWorkedHours(mapped.getTotalWorkedHours());
            item.setTotalWorkedMinutes(mapped.getTotalWorkedMinutes());
            item.setTotalWorkedDisplay(mapped.getTotalWorkedDisplay());

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

    // Lọc danh sách nhân viên đủ điều kiện chấm công trong phòng ban
    private List<User> getEligibleUsersForAttendance(Integer departmentId) {
        Stream<User> stream = userRepo.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));

        if (departmentId != null) {
            stream = stream.filter(u -> u.getDepartment() != null
                    && u.getDepartment().getId().equals(departmentId));
        }

        // Loại nhân viên có vai trò không hợp lệ (không chấm công)
        return stream
                .filter(u -> {
                    List<UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                    return roles != null && !roles.isEmpty()
                            && !attendanceStatusCalculator.hasForbiddenRole(roles);
                })
                .toList();
    }

    // Hỗ trợ copy dữ liệu từ response (được map) sang item (trả về danh sách chi tiết) 
    private void copyDailyItemFields(DailyAttendanceListItemResponse item, DailyAttendanceListItemResponse mapped) {
        item.setId(mapped.getId());
        item.setCheckInTime(mapped.getCheckInTime());
        item.setCheckOutTime(mapped.getCheckOutTime());
        item.setStatus(mapped.getStatus());
        item.setStatusColor(mapped.getStatusColor());
        item.setWorkedHours(mapped.getWorkedHours());
        item.setWorkedMinutes(mapped.getWorkedMinutes());
        item.setWorkedDisplay(mapped.getWorkedDisplay());
        item.setShiftStartTime(mapped.getShiftStartTime());
        item.setShiftEndTime(mapped.getShiftEndTime());
        item.setShiftDisplay(mapped.getShiftDisplay());
        item.setShiftHours(mapped.getShiftHours());
        item.setRemarks(mapped.getRemarks());
    }

    // Tính phần trăm dựa trên tổng số
    private BigDecimal calculatePercent(int value, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
