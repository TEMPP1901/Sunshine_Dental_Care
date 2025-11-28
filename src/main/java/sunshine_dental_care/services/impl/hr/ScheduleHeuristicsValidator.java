package sunshine_dental_care.services.impl.hr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;

/**
 * ✅ Lớp 2: Bộ kiểm tra suy nghiệm lịch (Schedule Heuristics Validator)
 * Kiểm tra các yếu tố "chất lượng" của lịch làm việc:
 * - Coverage: Độ bao phủ (clinic coverage)
 * - Fairness: Tính công bằng (workload distribution)
 * - Rotation: Kiểm tra yêu cầu luân phiên/xen kẽ
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleHeuristicsValidator {

    private static final double WORKLOAD_IMBALANCE_THRESHOLD = 0.20; // 20% chênh lệch
    private static final int MIN_DOCTORS_PER_CLINIC_PER_DAY = 1;

    /**
     * Validate schedule heuristics (soft validation)
     * @param request Schedule request to validate
     * @param userDescription Original user description to check for specific requirements
     * @return ValidationResultDto with warnings (not errors)
     */
    public ValidationResultDto validate(CreateWeeklyScheduleRequest request, String userDescription) {
        ValidationResultDto result = new ValidationResultDto();
        result.setValid(true); // Heuristics are warnings, not blocking errors

        if (request == null || request.getDailyAssignments() == null) {
            return result;
        }

        // 1. Check clinic coverage
        checkClinicCoverage(request, result);

        // 2. Check workload distribution (fairness)
        checkWorkloadDistribution(request, result);

        // 3. Check rotation requirements (if mentioned in user description)
        if (userDescription != null && (userDescription.toLowerCase().contains("xen kẽ") 
                || userDescription.toLowerCase().contains("luân phiên")
                || userDescription.toLowerCase().contains("rotation"))) {
            checkRotation(request, result);
        }

        return result;
    }

    /**
     * 1. Kiểm tra độ bao phủ (Coverage)
     * Có ngày nào mà một phòng khám không có ai làm việc không?
     */
    private void checkClinicCoverage(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = 
            request.getDailyAssignments();

        Set<Integer> allClinicIds = new HashSet<>();
        for (List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments : dailyAssignments.values()) {
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                if (assignment.getClinicId() != null) {
                    allClinicIds.add(assignment.getClinicId());
                }
            }
        }

        if (allClinicIds.isEmpty()) {
            result.addWarning("Không có phòng khám nào được phân công trong lịch này.");
            return;
        }

        // Kiểm tra từng ngày
        for (Map.Entry<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> entry : dailyAssignments.entrySet()) {
            String day = entry.getKey();
            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = entry.getValue();

            // Đếm số bác sĩ theo từng clinic trong ngày này
            Map<Integer, Integer> clinicDoctorCount = new HashMap<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                Integer clinicId = assignment.getClinicId();
                if (clinicId != null) {
                    clinicDoctorCount.put(clinicId, clinicDoctorCount.getOrDefault(clinicId, 0) + 1);
                }
            }

            // Kiểm tra xem có clinic nào không có bác sĩ không
            for (Integer clinicId : allClinicIds) {
                int doctorCount = clinicDoctorCount.getOrDefault(clinicId, 0);
                if (doctorCount < MIN_DOCTORS_PER_CLINIC_PER_DAY) {
                    result.addWarning(String.format(
                        "⚠️ Cảnh báo: Phòng khám %d không có bác sĩ nào làm việc vào %s. Cả hai phòng khám nên hoạt động mỗi ngày.",
                        clinicId, day
                    ));
                }
            }
        }
    }

    /**
     * 2. Kiểm tra tính công bằng (Fairness)
     * Tính tổng số ca làm việc của mỗi bác sĩ và kiểm tra chênh lệch
     */
    private void checkWorkloadDistribution(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = 
            request.getDailyAssignments();

        // Đếm số ca làm việc của mỗi bác sĩ
        Map<Integer, Integer> doctorShiftCount = new HashMap<>();
        for (List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments : dailyAssignments.values()) {
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                Integer doctorId = assignment.getDoctorId();
                if (doctorId != null) {
                    doctorShiftCount.put(doctorId, doctorShiftCount.getOrDefault(doctorId, 0) + 1);
                }
            }
        }

        if (doctorShiftCount.isEmpty()) {
            return;
        }

        // Tính trung bình
        double average = doctorShiftCount.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0);

        if (average == 0) {
            return;
        }

        // Kiểm tra chênh lệch
        for (Map.Entry<Integer, Integer> entry : doctorShiftCount.entrySet()) {
            int doctorId = entry.getKey();
            int shiftCount = entry.getValue();
            double deviation = Math.abs(shiftCount - average) / average;

            if (deviation > WORKLOAD_IMBALANCE_THRESHOLD) {
                result.addWarning(String.format(
                    "⚠️ Cảnh báo: Số ca làm việc của Bác sĩ %d (%d ca) chênh lệch %.1f%% so với trung bình (%.1f ca). Nên phân bổ công việc đều hơn.",
                    doctorId, shiftCount, deviation * 100, average
                ));
            }
        }

        // Tìm bác sĩ có số ca nhiều nhất và ít nhất
        Optional<Map.Entry<Integer, Integer>> maxEntry = doctorShiftCount.entrySet().stream()
            .max(Map.Entry.comparingByValue());
        Optional<Map.Entry<Integer, Integer>> minEntry = doctorShiftCount.entrySet().stream()
            .min(Map.Entry.comparingByValue());

        if (maxEntry.isPresent() && minEntry.isPresent()) {
            int maxShifts = maxEntry.get().getValue();
            int minShifts = minEntry.get().getValue();
            if (minShifts > 0) {
                double ratio = (double) maxShifts / minShifts;
                if (ratio > 1.5) { // Chênh lệch hơn 50%
                    result.addWarning(String.format(
                        "⚠️ Cảnh báo: Chênh lệch lớn về số ca làm việc. Bác sĩ %d có %d ca trong khi Bác sĩ %d chỉ có %d ca (tỷ lệ %.1f:1).",
                        maxEntry.get().getKey(), maxShifts, minEntry.get().getKey(), minShifts, ratio
                    ));
                }
            }
        }
    }

    /**
     * 3. Kiểm tra yêu cầu luân phiên/xen kẽ
     * Kiểm tra xem các ngày nghỉ có bị cụm lại với nhau không
     */
    private void checkRotation(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = 
            request.getDailyAssignments();

        // Lấy danh sách tất cả bác sĩ
        Set<Integer> allDoctorIds = new HashSet<>();
        for (List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments : dailyAssignments.values()) {
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                if (assignment.getDoctorId() != null) {
                    allDoctorIds.add(assignment.getDoctorId());
                }
            }
        }

        String[] dayOrder = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

        // Kiểm tra từng bác sĩ
        for (Integer doctorId : allDoctorIds) {
            List<String> offDays = new ArrayList<>();

            for (String day : dayOrder) {
                List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = 
                    dailyAssignments.getOrDefault(day, Collections.emptyList());
                
                boolean isWorking = assignments.stream()
                    .anyMatch(a -> a.getDoctorId() != null && a.getDoctorId().equals(doctorId));

                if (!isWorking) {
                    offDays.add(day);
                }
            }

            // Kiểm tra xem các ngày nghỉ có bị cụm lại không
            if (offDays.size() >= 2) {
                int maxConsecutiveOffDays = findMaxConsecutiveDays(offDays, dayOrder);
                if (maxConsecutiveOffDays >= 3) {
                    result.addWarning(String.format(
                        "⚠️ Cảnh báo: Bác sĩ %d có %d ngày nghỉ liên tiếp. Yêu cầu 'xen kẽ' hoặc 'luân phiên' nên có các ngày nghỉ phân tán hơn.",
                        doctorId, maxConsecutiveOffDays
                    ));
                }
            }
        }
    }

    /**
     * Tìm số ngày nghỉ liên tiếp tối đa
     */
    private int findMaxConsecutiveDays(List<String> offDays, String[] dayOrder) {
        if (offDays.isEmpty()) {
            return 0;
        }

        int maxConsecutive = 1;
        int currentConsecutive = 1;

        for (int i = 0; i < dayOrder.length; i++) {
            if (offDays.contains(dayOrder[i])) {
                // Kiểm tra ngày tiếp theo
                if (i < dayOrder.length - 1 && offDays.contains(dayOrder[i + 1])) {
                    currentConsecutive++;
                    maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
                } else {
                    currentConsecutive = 1;
                }
            } else {
                currentConsecutive = 1;
            }
        }

        return maxConsecutive;
    }
}

