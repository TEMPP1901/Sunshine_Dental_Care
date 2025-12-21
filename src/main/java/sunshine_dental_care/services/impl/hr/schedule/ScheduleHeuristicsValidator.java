package sunshine_dental_care.services.impl.hr.schedule;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleHeuristicsValidator {

    // ngưỡng lệch % ca làm cho phép giữa các bác sĩ
    private static final double WORKLOAD_IMBALANCE_THRESHOLD = 0.20;
    // tối thiểu số bác sĩ/phòng khám/ngày
    private static final int MIN_DOCTORS_PER_CLINIC_PER_DAY = 1;

    public ValidationResultDto validate(CreateWeeklyScheduleRequest request, String userDescription) {
        ValidationResultDto result = new ValidationResultDto();
        result.setValid(true);

        if (request == null || request.getDailyAssignments() == null) {
            return result;
        }

        checkClinicCoverage(request, result);             // cảnh báo nếu có phòng khám không có bác sĩ trực ca
        checkWorkloadDistribution(request, result);       // cảnh báo nếu phân phối số ca không đều

        // kiểm tra yêu cầu luân phiên nếu user có đề cập
        if (userDescription != null && (userDescription.toLowerCase().contains("xen kẽ")
                || userDescription.toLowerCase().contains("luân phiên")
                || userDescription.toLowerCase().contains("rotation"))) {
            checkRotation(request, result);
        }

        return result;
    }

    private void checkClinicCoverage(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments =
            request.getDailyAssignments();

        // tập hợp tất cả clinicId thực sự được xếp ca
        Set<Integer> allClinicIds = new HashSet<>();
        for (List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments : dailyAssignments.values()) {
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                if (assignment.getClinicId() != null) {
                    allClinicIds.add(assignment.getClinicId());
                }
            }
        }

        if (allClinicIds.isEmpty()) {
            result.addWarning("No clinics are assigned in this schedule.");
            return;
        }

        // với từng ngày, kiểm tra đủ bác sĩ cho mỗi clinic
        for (Map.Entry<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> entry : dailyAssignments.entrySet()) {
            String day = entry.getKey();
            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = entry.getValue();

            Map<Integer, Integer> clinicDoctorCount = new HashMap<>();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                Integer clinicId = assignment.getClinicId();
                if (clinicId != null) {
                    clinicDoctorCount.put(clinicId, clinicDoctorCount.getOrDefault(clinicId, 0) + 1);
                }
            }
            // cảnh báo nếu có clinic không bác sĩ trong ngày này
            for (Integer clinicId : allClinicIds) {
                int doctorCount = clinicDoctorCount.getOrDefault(clinicId, 0);
                if (doctorCount < MIN_DOCTORS_PER_CLINIC_PER_DAY) {
                    result.addWarning(String.format(
                        "Clinic %d does not have any doctors working on %s. Every clinic should be operational every day.",
                        clinicId, day
                    ));
                }
            }
        }
    }

    // cảnh báo nếu phân công ca chênh lệch nhiều giữa các bác sĩ
    private void checkWorkloadDistribution(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments =
            request.getDailyAssignments();

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

        double average = doctorShiftCount.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0);

        if (average == 0) {
            return;
        }

        for (Map.Entry<Integer, Integer> entry : doctorShiftCount.entrySet()) {
            int doctorId = entry.getKey();
            int shiftCount = entry.getValue();
            double deviation = Math.abs(shiftCount - average) / average;

            if (deviation > WORKLOAD_IMBALANCE_THRESHOLD) {
                result.addWarning(String.format(
                    "Doctor %d has %d shifts which deviates by %.1f%% compared to the average (%.1f shifts). It is recommended to balance the distribution.",
                    doctorId, shiftCount, deviation * 100, average
                ));
            }
        }

        // cảnh báo gap workload lớn nhất - nhỏ nhất quá cao
        Optional<Map.Entry<Integer, Integer>> maxEntry = doctorShiftCount.entrySet().stream()
            .max(Map.Entry.comparingByValue());
        Optional<Map.Entry<Integer, Integer>> minEntry = doctorShiftCount.entrySet().stream()
            .min(Map.Entry.comparingByValue());

        if (maxEntry.isPresent() && minEntry.isPresent()) {
            int maxShifts = maxEntry.get().getValue();
            int minShifts = minEntry.get().getValue();
            if (minShifts > 0) {
                double ratio = (double) maxShifts / minShifts;
                if (ratio > 1.5) {
                    result.addWarning(String.format(
                        "Significant workload gap: Doctor %d has %d shifts while Doctor %d only has %d shifts (ratio %.1f:1).",
                        maxEntry.get().getKey(), maxShifts, minEntry.get().getKey(), minShifts, ratio
                    ));
                }
            }
        }
    }

    // cảnh báo bác sĩ có nhiều ngày nghỉ liền nhau (không luân phiên)
    private void checkRotation(CreateWeeklyScheduleRequest request, ValidationResultDto result) {
        Map<String, List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments =
            request.getDailyAssignments();

        Set<Integer> allDoctorIds = new HashSet<>();
        for (List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments : dailyAssignments.values()) {
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : assignments) {
                if (assignment.getDoctorId() != null) {
                    allDoctorIds.add(assignment.getDoctorId());
                }
            }
        }

        String[] dayOrder = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

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

            // nếu nghỉ >=2 ngày, check xem có chuỗi nghỉ liên tiếp >=3 không
            if (offDays.size() >= 2) {
                int maxConsecutiveOffDays = findMaxConsecutiveDays(offDays, dayOrder);
                if (maxConsecutiveOffDays >= 3) {
                    result.addWarning(String.format(
                        "Doctor %d has %d consecutive days off. The requirement for 'rotation'/'alternate' shifts suggests days off should be more evenly spread out.",
                        doctorId, maxConsecutiveOffDays
                    ));
                }
            }
        }
    }

    // trả về số ngày nghỉ liên tiếp lớn nhất của 1 bác sĩ
    private int findMaxConsecutiveDays(List<String> offDays, String[] dayOrder) {
        if (offDays.isEmpty()) {
            return 0;
        }
        int maxConsecutive = 1;
        int currentConsecutive = 1;
        for (int i = 0; i < dayOrder.length; i++) {
            if (offDays.contains(dayOrder[i])) {
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
