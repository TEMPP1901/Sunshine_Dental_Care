package sunshine_dental_care.services.huybro_payroll.impl;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.huybro_payroll.ManualItemRequest;
import sunshine_dental_care.dto.huybro_payroll.PayrollCalculationRequest;
import sunshine_dental_care.dto.huybro_payroll.PayslipAllowanceDto;
import sunshine_dental_care.dto.huybro_payroll.PayslipDetailResponse;
import sunshine_dental_care.dto.huybro_payroll.PayslipSearchRequest;
import sunshine_dental_care.dto.huybro_payroll.PayslipViewDto;
import sunshine_dental_care.dto.huybro_payroll.SalaryProfileRequest;
import sunshine_dental_care.dto.huybro_payroll.SalaryProfileResponse;
import sunshine_dental_care.dto.huybro_payroll.UserSearchResponse;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.huybro_salary.PayslipAllowance;
import sunshine_dental_care.entities.huybro_salary.PayslipsSnapshot;
import sunshine_dental_care.entities.huybro_salary.SalaryAllowance;
import sunshine_dental_care.entities.huybro_salary.SalaryCycle;
import sunshine_dental_care.entities.huybro_salary.SalaryProfile;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;
import sunshine_dental_care.entities.huybro_salary.enums.PeriodStatus;
import sunshine_dental_care.entities.huybro_salary.enums.SalaryCalculationType;
import sunshine_dental_care.entities.huybro_salary.enums.TaxType;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.huybro_custom.UserCustomRepository;
import sunshine_dental_care.repositories.huybro_payroll.PayrollAttendanceRepo;
import sunshine_dental_care.repositories.huybro_payroll.PayslipAllowanceRepo;
import sunshine_dental_care.repositories.huybro_payroll.PayslipsSnapshotRepo;
import sunshine_dental_care.repositories.huybro_payroll.SalaryAllowanceRepo;
import sunshine_dental_care.repositories.huybro_payroll.SalaryCycleRepo;
import sunshine_dental_care.repositories.huybro_payroll.SalaryProfileRepo;
import sunshine_dental_care.services.huybro_payroll.interfaces.PayrollService;
import sunshine_dental_care.utils.huybro_utils.ExcelExportUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollServiceImpl implements PayrollService {

    private final SalaryProfileRepo salaryProfileRepo;
    private final SalaryAllowanceRepo salaryAllowanceRepo;
    private final SalaryCycleRepo salaryCycleRepo;
    private final PayslipsSnapshotRepo payslipRepo;
    private final UserRepo userRepo;
    private final PayrollAttendanceRepo attendanceRepo;
    private final PayslipAllowanceRepo payslipAllowanceRepo;
    private final UserCustomRepository userCustomRepository;
    private static final BigDecimal SELF_RELIEF = new BigDecimal("11000000"); // 11 triệu
    private static final BigDecimal DEPENDENT_RELIEF_UNIT = new BigDecimal("4400000"); // 4.4 triệu/người
    // =================================================================================
    // 1. CẤU HÌNH LƯƠNG (PROFILE CONFIGURATION)
    // =================================================================================

    @Override
    public SalaryProfileResponse getProfileByUserId(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<SalaryProfile> profileOpt = salaryProfileRepo.findByUserId(userId);

        if (profileOpt.isPresent()) {
            return mapToProfileResponse(profileOpt.get());
        }

        return createDefaultTemplateForUser(user);
    }

    private SalaryProfileResponse createDefaultTemplateForUser(User user) {
        boolean isDoctor = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getRoleName().equalsIgnoreCase("DOCTOR"));

        boolean isOffice = user.getUserRoles().stream()
                .anyMatch(ur -> List.of("HR", "ACCOUNTANT", "RECEPTION").contains(ur.getRole().getRoleName()));

        return SalaryProfileResponse.builder()
                .id(null)
                .userId(user.getId())
                .userFullName(user.getFullName())
                .userEmail(user.getEmail())
                .userCode(user.getCode())
                .calculationType(isDoctor ? SalaryCalculationType.SHIFT_BASED : SalaryCalculationType.MONTHLY)
                .taxType(TaxType.PROGRESSIVE)
                .baseSalary(BigDecimal.ZERO)
                .standardWorkDays(isDoctor ? 0.0 : 26.0)
                .standardShifts(isDoctor ? 25 : 0)
                .overShiftRate(isDoctor ? new BigDecimal("500000") : BigDecimal.ZERO)
                .otRate(isOffice ? new BigDecimal("10.0") : new BigDecimal("0.1"))
                .lateDeductionRate(BigDecimal.ZERO)
                .insuranceAmount(BigDecimal.ZERO)
                .allowances(new ArrayList<>())
                .build();
    }

    @Override
    @Transactional
    public SalaryProfileResponse createOrUpdateProfile(SalaryProfileRequest request) {
        User user = userRepo.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));
        SalaryProfile profile = salaryProfileRepo.findByUserId(request.getUserId()).orElse(new SalaryProfile());

        profile.setUser(user);
        profile.setCalculationType(request.getCalculationType());
        profile.setBaseSalary(request.getBaseSalary());
        profile.setInsuranceAmount(request.getInsuranceAmount()); // Mức lương đóng BH
        profile.setOtRate(request.getOtRate()); // Lưu tỷ lệ % (VD: 150)
        profile.setOverShiftRate(request.getOverShiftRate());
        profile.setLateDeductionRate(request.getLateDeductionRate());
        profile.setTaxType(TaxType.PROGRESSIVE);

        if (request.getCalculationType() == SalaryCalculationType.SHIFT_BASED) {
            profile.setStandardShifts(request.getStandardShifts());
            profile.setStandardWorkDays(0.0);
        } else {
            profile.setStandardWorkDays(request.getStandardWorkDays());
            profile.setStandardShifts(0);
        }

        if (profile.getAllowances() == null) profile.setAllowances(new ArrayList<>());
        else profile.getAllowances().clear();

        if (request.getAllowances() != null) {
            for (SalaryProfileRequest.AllowanceDTO dto : request.getAllowances()) {
                SalaryAllowance sa = new SalaryAllowance();
                sa.setSalaryProfile(profile);
                sa.setAllowanceName(dto.getAllowanceName());
                sa.setAmount(dto.getAmount());
                sa.setType(dto.getType());
                sa.setNote(dto.getNote());
                profile.getAllowances().add(sa);
            }
        }
        return mapToProfileResponse(salaryProfileRepo.save(profile));
    }

    // =================================================================================
    // 2. TÍNH TOÁN LƯƠNG (CORE CALCULATION LOGIC)
    // =================================================================================

    @Override
    @Transactional
    public List<PayslipViewDto> calculatePayroll(PayrollCalculationRequest request) {
        SalaryCycle cycle = getOrCreateCycle(request.getMonth(), request.getYear());
        if (cycle.getStatus() == PeriodStatus.FINALIZED) throw new RuntimeException("Kỳ lương đã CHỐT!");

        // 1. Lấy Profiles (Giữ logic filter của bạn)
        List<SalaryProfile> profiles = (request.getUserIds() != null && !request.getUserIds().isEmpty())
                ? request.getUserIds().stream().map(uid -> salaryProfileRepo.findByUserId(uid).orElse(null)).filter(p -> p != null).collect(Collectors.toList())
                : salaryProfileRepo.findAll().stream().filter(p -> p.getUser().getUserRoles().stream().anyMatch(ur -> ur.getIsActive() && List.of("HR", "DOCTOR", "RECEPTION", "ACCOUNTANT").contains(ur.getRole().getRoleName()))).collect(Collectors.toList());

        List<PayslipsSnapshot> results = new ArrayList<>();

        for (SalaryProfile profile : profiles) {
            List<Attendance> attendances = attendanceRepo.findByUserIdAndWorkDateBetween(profile.getUser().getId(), cycle.getStartDate(), cycle.getEndDate());
            PayslipsSnapshot slip = payslipRepo.findBySalaryCycleIdAndUserId(cycle.getId(), profile.getUser().getId()).orElse(new PayslipsSnapshot());

            // --- A. SNAPSHOT ---
            slip.setSalaryCycle(cycle); slip.setUser(profile.getUser());
            slip.setBaseSalarySnapshot(profile.getBaseSalary());
            slip.setStandardWorkDaysSnapshot(profile.getStandardWorkDays());
            slip.setStandardShiftsSnapshot(profile.getStandardShifts());

            // --- B. TÍNH LƯƠNG CƠ BẢN (Giữ logic cũ của bạn) ---
            calculateBaseSalaryAmount(slip, profile, attendances);

            // --- C. TÍNH OT (Cập nhật logic % ) ---
            double totalOtHours = attendances.stream().filter(a -> Boolean.TRUE.equals(a.getIsOvertime())).mapToDouble(a -> a.getActualWorkHours() != null ? a.getActualWorkHours().doubleValue() : 0.0).sum();
            slip.setTotalOtHours(totalOtHours);
            if (totalOtHours > 0 && profile.getOtRate() != null) {
                BigDecimal standardDays = (profile.getStandardWorkDays() != null && profile.getStandardWorkDays() > 0) ? BigDecimal.valueOf(profile.getStandardWorkDays()) : BigDecimal.valueOf(26);
                BigDecimal hourlyRate = profile.getBaseSalary().divide(standardDays, 2, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(8), 2, RoundingMode.HALF_UP);
                // Lương OT = Giờ * LươngGiờ * (%OT / 100)
                slip.setOtSalaryAmount(hourlyRate.multiply(BigDecimal.valueOf(totalOtHours)).multiply(profile.getOtRate().divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)));
            }

            // --- D. LATE PENALTY & INITIAL VALUES ---
            int totalLate = attendances.stream().mapToInt(a -> a.getLateMinutes() != null ? a.getLateMinutes() : 0).sum();
            slip.setTotalLateMinutes(totalLate);
            slip.setLatePenaltyAmount(profile.getLateDeductionRate() != null ? profile.getLateDeductionRate().multiply(BigDecimal.valueOf(totalLate)) : BigDecimal.ZERO);
            slip.setAdvancePayment(BigDecimal.ZERO);

            // --- E. SYNC ALLOWANCES ---
            if (slip.getPeriodicAllowances() == null) slip.setPeriodicAllowances(new ArrayList<>());
            slip.getPeriodicAllowances().removeIf(PayslipAllowance::getIsSystemGenerated);
            if (profile.getAllowances() != null) {
                for (SalaryAllowance configItem : profile.getAllowances()) {
                    PayslipAllowance item = new PayslipAllowance();
                    item.setPayslip(slip); item.setAllowanceName(configItem.getAllowanceName());
                    item.setAmount(configItem.getAmount()); item.setType(configItem.getType());
                    item.setIsSystemGenerated(true); item.setNote("From Contract");
                    slip.getPeriodicAllowances().add(item);
                }
            }

            // --- F. FINAL RECALCULATE ---
            recalculateTotals(slip, profile);
            results.add(payslipRepo.save(slip));
        }
        return results.stream().map(this::mapToDto).collect(Collectors.toList());
    }
    // Helper: Tính lương cứng (Tách ra cho gọn)
    private void calculateBaseSalaryAmount(PayslipsSnapshot slip, SalaryProfile profile, List<Attendance> attendances) {
        BigDecimal salaryAmount = BigDecimal.ZERO; BigDecimal bonusAmount = BigDecimal.ZERO;
        if (profile.getCalculationType() == SalaryCalculationType.MONTHLY) {
            // MONTHLY: Chỉ đếm ngày có check-in (giống attendance report)
            long workDays = attendances.stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsOvertime()))
                    .filter(a -> a.getCheckInTime() != null) // Chỉ đếm ngày có check-in
                    .map(Attendance::getWorkDate)
                    .distinct()
                    .count();
            slip.setActualWorkDays((double) workDays);
            if (profile.getStandardWorkDays() > 0) salaryAmount = profile.getBaseSalary().divide(BigDecimal.valueOf(profile.getStandardWorkDays()), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(workDays));
        } else {
            // SHIFT_BASED: Bám sát logic attendance report
            // Chỉ đếm ca có STATUS PRESENT (giống AttendanceReportMapper)
            java.util.Map<java.time.LocalDate, Long> presentShiftsByDate = attendances.stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getIsOvertime()))
                    .filter(a -> {
                        // Chỉ đếm ca có status present (giống AttendanceReportMapper)
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
            
            // Tính số ngày present: 1 ca = 0.5 ngày, 2 ca = 1.0 ngày (giống AttendanceReportMapper)
            double presentDays = presentShiftsByDate.values().stream()
                    .mapToDouble(count -> count >= 2 ? 1.0 : 0.5)
                    .sum();
            
            long totalPresentShifts = presentShiftsByDate.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
            
            slip.setActualShifts((int) totalPresentShifts);
            slip.setActualWorkDays(presentDays);
            
            // Tính standardDays động theo workingDays trong tháng (trừ chủ nhật)
            int workingDaysInMonth = 26; // Mặc định 26 ngày
            if (slip.getSalaryCycle() != null) {
                java.time.LocalDate startDate = slip.getSalaryCycle().getStartDate();
                java.time.LocalDate endDate = slip.getSalaryCycle().getEndDate();
                if (startDate != null && endDate != null) {
                    workingDaysInMonth = calculateWorkingDaysInMonth(startDate, endDate);
                }
            }
            
            // standardDays = workingDays (mỗi ngày làm 2 ca, nhưng tính theo ngày)
            double standardDays = workingDaysInMonth;
            
            // Tính lương dựa trên presentDays
            if (presentDays <= standardDays) {
                salaryAmount = profile.getBaseSalary()
                        .divide(BigDecimal.valueOf(standardDays), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(presentDays));
            } else {
                // Vượt chuẩn: full lương + thưởng
                salaryAmount = profile.getBaseSalary();
                double extraDays = presentDays - standardDays;
                if (extraDays > 0) {
                    // Thưởng = số ngày vượt × lương/ngày
                    BigDecimal dayRate = profile.getBaseSalary()
                            .divide(BigDecimal.valueOf(standardDays), 2, RoundingMode.HALF_UP);
                    bonusAmount = dayRate.multiply(BigDecimal.valueOf(extraDays));
                }
            }
        }
        slip.setSalaryAmount(salaryAmount); slip.setBonusAmount(bonusAmount);
    }
    
    // Helper: Tính số ngày làm việc trong tháng (trừ chủ nhật)
    private int calculateWorkingDaysInMonth(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        int workingDays = 0;
        java.time.LocalDate current = startDate;
        // Tính đến ngày hiện tại nếu tháng chưa kết thúc
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate effectiveEndDate = today.isBefore(endDate) ? today : endDate;
        
        while (!current.isAfter(effectiveEndDate)) {
            if (current.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }
    // =================================================================================
    // 3. HIỂN THỊ CHI TIẾT
    // =================================================================================

    @Override
    public PayslipDetailResponse getPayslipDetailById(Integer id) {
        PayslipsSnapshot slip = payslipRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payslip not found"));
        SalaryProfile profile = salaryProfileRepo.findByUserId(slip.getUser().getId()).orElse(null);

        // Insurance base for explanation
        BigDecimal insBase = (profile != null && profile.getInsuranceAmount() != null && profile.getInsuranceAmount().compareTo(BigDecimal.ZERO) > 0)
                ? profile.getInsuranceAmount() : slip.getBaseSalarySnapshot();

        // --- A. INCOMES ---
        List<PayslipDetailResponse.LineItem> incomes = new ArrayList<>();

        // 1. Standard Basic Salary
        String baseFormula;
        if (slip.getStandardShiftsSnapshot() > 0) {
            baseFormula = String.format("(%s / %d shifts) × %d actual shifts",
                    formatMoney(slip.getBaseSalarySnapshot()),
                    slip.getStandardShiftsSnapshot(),
                    Math.min(slip.getActualShifts(), slip.getStandardShiftsSnapshot()));
        } else {
            double stdDays = slip.getStandardWorkDaysSnapshot() != null ? slip.getStandardWorkDaysSnapshot() : 26.0;
            baseFormula = String.format("(%s / %.1f days) × %.1f actual days",
                    formatMoney(slip.getBaseSalarySnapshot()),
                    stdDays,
                    Math.min(slip.getActualWorkDays(), stdDays));
        }
        incomes.add(PayslipDetailResponse.LineItem.builder()
                .name("Standard Salary")
                .amount(slip.getSalaryAmount())
                .description(baseFormula).isHighlight(true).build());

        // 2. Over-target Bonus
        if (slip.getBonusAmount() != null && slip.getBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            String bonusDesc = "";
            if (slip.getStandardShiftsSnapshot() > 0 && slip.getActualShifts() > slip.getStandardShiftsSnapshot()) {
                int extraShifts = slip.getActualShifts() - slip.getStandardShiftsSnapshot();
                BigDecimal overShiftRate = (profile != null && profile.getOverShiftRate() != null) 
                        ? profile.getOverShiftRate() 
                        : BigDecimal.ZERO;
                bonusDesc = String.format("Exceeded %d shifts (Unit price: %s/shift)",
                        extraShifts, formatMoney(overShiftRate));
            } else if (slip.getActualWorkDays() > slip.getStandardWorkDaysSnapshot()) {
                double extraDays = slip.getActualWorkDays() - slip.getStandardWorkDaysSnapshot();
                BigDecimal dayRate = slip.getBaseSalarySnapshot().divide(BigDecimal.valueOf(slip.getStandardWorkDaysSnapshot()), 0, RoundingMode.HALF_UP);
                bonusDesc = String.format("Exceeded %.1f days (Unit price: %s/day)",
                        extraDays, formatMoney(dayRate));
            }
            incomes.add(PayslipDetailResponse.LineItem.builder()
                    .name("Over-target Bonus")
                    .amount(slip.getBonusAmount()).description(bonusDesc).isHighlight(false).build());
        }

        // 3. Overtime Pay
        if (slip.getOtSalaryAmount() != null && slip.getOtSalaryAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal otPercent = (profile != null && profile.getOtRate() != null) ? profile.getOtRate() : new BigDecimal("150");
            incomes.add(PayslipDetailResponse.LineItem.builder()
                    .name("Overtime Pay")
                    .amount(slip.getOtSalaryAmount())
                    .description(String.format("%.1f hours × Rate %s%%", slip.getTotalOtHours(), otPercent)).build());
        }

        // --- B. DEDUCTIONS & ALLOWANCES ---
        List<PayslipDetailResponse.LineItem> deductions = new ArrayList<>();
        long dependentCount = 0;

        if (slip.getPeriodicAllowances() != null) {
            for (PayslipAllowance item : slip.getPeriodicAllowances()) {
                String name = item.getAllowanceName().toUpperCase();

                if (name.equals("DEPENDENTS")) {
                    dependentCount = item.getAmount().longValue();
                    continue;
                }

                String itemDesc = item.getNote();
                if (List.of("BHXH", "BHYT", "BHTN").contains(name)) {
                    BigDecimal percent = (profile != null) ? profile.getAllowances().stream()
                            .filter(a -> a.getAllowanceName().equalsIgnoreCase(name))
                            .map(a -> a.getAmount()).findFirst().orElse(BigDecimal.ZERO) : BigDecimal.ZERO;

                    itemDesc = String.format("%s%% deduction based on Ins. Salary: %s", percent, formatMoney(insBase));
                }

                PayslipDetailResponse.LineItem li = PayslipDetailResponse.LineItem.builder()
                        .name(item.getAllowanceName()) // Tên phụ cấp/khấu trừ thường lấy từ Config (đã là Tiếng Anh)
                        .amount(item.getAmount())
                        .description(itemDesc).build();

                if (item.getType() == AllowanceType.INCOME) incomes.add(li);
                else deductions.add(li);
            }
        }

        // --- C. TAX BREAKDOWN ---
        BigDecimal familyRelief = SELF_RELIEF.add(DEPENDENT_RELIEF_UNIT.multiply(BigDecimal.valueOf(dependentCount)));
        String reliefDetail = String.format("Self-relief (11,000,000) + %d dependents (%d × 4,400,000)",
                dependentCount, dependentCount);

        BigDecimal taxableIncome = slip.getGrossSalary()
                .subtract(slip.getInsuranceDeduction())
                .subtract(familyRelief)
                .max(BigDecimal.ZERO);

        PayslipDetailResponse.TaxBreakdown taxInfo = PayslipDetailResponse.TaxBreakdown.builder()
                .grossIncome(slip.getGrossSalary())
                .insuranceDeduction(slip.getInsuranceDeduction())
                .selfRelief(familyRelief)
                .taxableIncome(taxableIncome)
                .taxAmount(slip.getTaxDeduction())
                .details(getTaxDetails(taxableIncome))
                .build();

        if (slip.getTaxDeduction().compareTo(BigDecimal.ZERO) > 0) {
            deductions.add(PayslipDetailResponse.LineItem.builder()
                    .name("Personal Income Tax (PIT)")
                    .amount(slip.getTaxDeduction())
                    .description("Detailed progressive calculation below").isHighlight(true).build());
        }

        return PayslipDetailResponse.builder()
                .id(slip.getId())
                .userFullName(slip.getUser().getFullName())
                .userCode(slip.getUser().getCode())
                .month(slip.getSalaryCycle().getMonth())
                .year(slip.getSalaryCycle().getYear())
                .status(slip.getSalaryCycle().getStatus().name())
                .workType(slip.getStandardShiftsSnapshot() > 0 ? "Shift-based" : "Monthly/Office-based")
                .workActual(slip.getStandardShiftsSnapshot() > 0 ? slip.getActualShifts() + " shifts" : slip.getActualWorkDays() + " days")
                .workStandard(slip.getStandardShiftsSnapshot() > 0 ? slip.getStandardShiftsSnapshot() + " shifts" : slip.getStandardWorkDaysSnapshot() + " days")
                .workFormula(baseFormula)
                .incomeItems(incomes)
                .totalIncome(slip.getGrossSalary())
                .deductionItems(deductions)
                .totalDeduction(slip.getGrossSalary().subtract(slip.getNetSalary()))
                .taxBreakdown(taxInfo)
                .netSalary(slip.getNetSalary())
                .note(reliefDetail)
                .build();
    }
    @Override
    public List<UserSearchResponse> getMissingConfigs() {
        List<String> employeeRoles = List.of("HR", "DOCTOR", "RECEPTION", "ACCOUNTANT", "NURSE");
        return userCustomRepository.findMissingSalaryProfiles(employeeRoles);
    }
    // --- 4. OTHER METHODS ---
    @Override
    @Transactional
    public void finalizeCycle(Integer month, Integer year) {
        SalaryCycle cycle = salaryCycleRepo.findByMonthAndYear(month, year)
                .orElseThrow(() -> new RuntimeException("Kỳ lương không tồn tại"));
        cycle.setStatus(PeriodStatus.FINALIZED);
        salaryCycleRepo.save(cycle);
    }

    @Override
    public Page<PayslipViewDto> getPayslips(PayslipSearchRequest request) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<PayslipsSnapshot> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.getMonth() != null && request.getYear() != null) {
                predicates.add(criteriaBuilder.equal(root.get("salaryCycle").get("month"), request.getMonth()));
                predicates.add(criteriaBuilder.equal(root.get("salaryCycle").get("year"), request.getYear()));
            }
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String keyword = "%" + request.getKeyword().trim().toLowerCase() + "%";
                Predicate nameLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("fullName")), keyword);
                Predicate noteLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("note")), keyword);
                predicates.add(criteriaBuilder.or(nameLike, noteLike));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<PayslipsSnapshot> entities = payslipRepo.findAll(spec, pageable);
        return entities.map(this::mapToDto);
    }

    @Override
    @Transactional
    public PayslipViewDto updateAdvancePayment(Integer payslipId, BigDecimal amount) {
        PayslipsSnapshot slip = payslipRepo.findById(payslipId)
                .orElseThrow(() -> new RuntimeException("Phiếu lương không tồn tại"));
        if (slip.getSalaryCycle().getStatus() == PeriodStatus.FINALIZED) {
            throw new RuntimeException("Kỳ lương đã chốt, không thể sửa tạm ứng!");
        }
        slip.setAdvancePayment(amount);
        BigDecimal totalDeductions = slip.getLatePenaltyAmount()
                .add(slip.getInsuranceDeduction())
                .add(slip.getTaxDeduction())
                .add(amount)
                .add(slip.getOtherDeductionAmount());
        slip.setNetSalary(slip.getGrossSalary().subtract(totalDeductions));
        PayslipsSnapshot saved = payslipRepo.save(slip);
        return mapToDto(saved);
    }
    @Override
    @Transactional
    public PayslipViewDto addManualItem(Integer payslipId, ManualItemRequest request) {
        PayslipsSnapshot slip = payslipRepo.findById(payslipId)
                .orElseThrow(() -> new RuntimeException("Payslip not found"));

        if (slip.getSalaryCycle().getStatus() == PeriodStatus.FINALIZED) {
            throw new RuntimeException("Cannot edit finalized payslip");
        }

        // 1. Tạo item mới
        PayslipAllowance item = new PayslipAllowance();
        item.setPayslip(slip);
        item.setAllowanceName(request.getName());
        item.setAmount(request.getAmount());
        item.setType(request.getType());
        item.setIsSystemGenerated(false); // Quan trọng: Đây là nhập tay
        item.setNote(request.getNote());

        // 2. LƯU TRỰC TIẾP ITEM (Để tránh lỗi Cascade)
        payslipAllowanceRepo.save(item);

        // 3. Thêm vào list và tính toán lại
        if (slip.getPeriodicAllowances() == null) slip.setPeriodicAllowances(new ArrayList<>());
        slip.getPeriodicAllowances().add(item);

        // 4. Tính lại Gross/Net dựa trên mảng allowances mới nhất
        recalculateTotals(slip);

        // 5. Lưu Slip (để cập nhật GrossSalary, NetSalary snapshot)
        PayslipsSnapshot saved = payslipRepo.save(slip);

        // Trả về DTO hoàn chỉnh đã có số mới
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public PayslipViewDto removeManualItem(Integer payslipId, Integer itemId) {
        PayslipsSnapshot slip = payslipRepo.findById(payslipId)
                .orElseThrow(() -> new RuntimeException("Payslip not found"));

        if (slip.getSalaryCycle().getStatus() == PeriodStatus.FINALIZED) {
            throw new RuntimeException("Cannot edit finalized payslip");
        }

        // Tìm và xóa item (chỉ cho phép xóa item nhập tay)
        PayslipAllowance item = payslipAllowanceRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (item.getIsSystemGenerated()) {
            throw new RuntimeException("Cannot remove system-generated items manually. Please update Salary Config.");
        }

        slip.getPeriodicAllowances().remove(item);
        payslipAllowanceRepo.delete(item); // Xóa khỏi DB

        // Tính lại số liệu
        recalculateTotals(slip);

        PayslipsSnapshot saved = payslipRepo.save(slip);
        return mapToDto(saved);
    }
    // --- MAPPERS & HELPERS ---
    private PayslipAllowanceDto mapToAllowanceDto(PayslipAllowance entity) {
        return PayslipAllowanceDto.builder()
                .id(entity.getId())
                .name(entity.getAllowanceName())
                .amount(entity.getAmount())
                .type(entity.getType())
                .isSystemGenerated(entity.getIsSystemGenerated())
                .note(entity.getNote())
                .build();
    }

    private PayslipViewDto mapToDto(PayslipsSnapshot entity) {
        return PayslipViewDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .userFullName(entity.getUser().getFullName())
                .userEmail(entity.getUser().getEmail())
                .userCode(entity.getUser().getCode())
                .month(entity.getSalaryCycle().getMonth())
                .year(entity.getSalaryCycle().getYear())
                .status(entity.getSalaryCycle().getStatus())
                .actualWorkDays(entity.getActualWorkDays())
                .actualShifts(entity.getActualShifts())
                .standardWorkDaysSnapshot(entity.getStandardWorkDaysSnapshot())
                .standardShiftsSnapshot(entity.getStandardShiftsSnapshot())
                .baseSalarySnapshot(entity.getBaseSalarySnapshot())
                .salaryAmount(entity.getSalaryAmount())
                .otSalaryAmount(entity.getOtSalaryAmount())
                .bonusAmount(entity.getBonusAmount())
                .allowanceAmount(entity.getAllowanceAmount())
                .grossSalary(entity.getGrossSalary())
                .latePenaltyAmount(entity.getLatePenaltyAmount())
                .insuranceDeduction(entity.getInsuranceDeduction())
                .taxDeduction(entity.getTaxDeduction())
                .advancePayment(entity.getAdvancePayment())
                .otherDeductionAmount(entity.getOtherDeductionAmount())
                .netSalary(entity.getNetSalary())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .allowanceDetails(
                        entity.getPeriodicAllowances() == null ? new ArrayList<>()
                                : entity.getPeriodicAllowances().stream()
                                .map(this::mapToAllowanceDto)
                                .collect(Collectors.toList())
                )
                .build();
    }

    private SalaryProfileResponse mapToProfileResponse(SalaryProfile entity) {
        return SalaryProfileResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .userFullName(entity.getUser().getFullName())
                .userEmail(entity.getUser().getEmail())
                .userCode(entity.getUser().getCode())
                .calculationType(entity.getCalculationType())
                .taxType(entity.getTaxType())
                .baseSalary(entity.getBaseSalary())
                .standardWorkDays(entity.getStandardWorkDays())
                .standardShifts(entity.getStandardShifts())
                .otRate(entity.getOtRate())
                .overShiftRate(entity.getOverShiftRate())
                .lateDeductionRate(entity.getLateDeductionRate())
                .insuranceAmount(entity.getInsuranceAmount())
                .allowances(entity.getAllowances().stream().map(a ->
                        SalaryProfileResponse.AllowanceResponseDto.builder()
                                .id(a.getId())
                                .allowanceName(a.getAllowanceName())
                                .amount(a.getAmount())
                                .type(a.getType())
                                .note(a.getNote())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }

    private SalaryCycle getOrCreateCycle(Integer month, Integer year) {
        return salaryCycleRepo.findByMonthAndYear(month, year).orElseGet(() -> {
            SalaryCycle c = new SalaryCycle();
            c.setMonth(month);
            c.setYear(year);
            YearMonth ym = YearMonth.of(year, month);
            c.setStartDate(ym.atDay(1));
            c.setEndDate(ym.atEndOfMonth());
            c.setStatus(PeriodStatus.DRAFT);
            return salaryCycleRepo.save(c);
        });
    }

    // =========================================================================
    // UPDATE: HÀM TÍNH THUẾ TỔNG (Dựa trên chi tiết -> Đảm bảo khớp 100%)
    // =========================================================================
    private BigDecimal calculateProgressiveTax(BigDecimal income) {
        List<PayslipDetailResponse.TaxTierDetail> details = getTaxDetails(income);
        // Cộng dồn tất cả các bậc thuế lại
        return details.stream()
                .map(PayslipDetailResponse.TaxTierDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =========================================================================
    // HÀM HELPER CHÍNH: TÍNH TOÁN VÀ LÀM TRÒN TỪNG BẬC
    // =========================================================================
    private List<PayslipDetailResponse.TaxTierDetail> getTaxDetails(BigDecimal taxableIncome) {
        List<PayslipDetailResponse.TaxTierDetail> details = new ArrayList<>();
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return details;
        }

        double income = taxableIncome.doubleValue();
        double remaining = income;

        // Helper func local để tạo tier và làm tròn ngay lập tức
        // Mục tiêu: Làm tròn tiền thuế từng bậc về số nguyên để tránh lỗi 0.0001

        // Bậc 1: 5% (Đến 5tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 5000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.05).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 1 (0 - 5tr) x 5%")
                    .amount(taxVal)
                    .build());
            remaining -= 5000000;
        }

        // Bậc 2: 10% (5tr - 10tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 5000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.10).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 2 (5 - 10tr) x 10%")
                    .amount(taxVal)
                    .build());
            remaining -= 5000000;
        }

        // Bậc 3: 15% (10tr - 18tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 8000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.15).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 3 (10 - 18tr) x 15%")
                    .amount(taxVal)
                    .build());
            remaining -= 8000000;
        }

        // Bậc 4: 20% (18tr - 32tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 14000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.20).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 4 (18 - 32tr) x 20%")
                    .amount(taxVal)
                    .build());
            remaining -= 14000000;
        }

        // Bậc 5: 25% (32tr - 52tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 20000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.25).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 5 (32 - 52tr) x 25%")
                    .amount(taxVal)
                    .build());
            remaining -= 20000000;
        }

        // Bậc 6: 30% (52tr - 80tr)
        if (remaining > 0) {
            double taxBlock = Math.min(remaining, 28000000);
            BigDecimal taxVal = BigDecimal.valueOf(taxBlock * 0.30).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 6 (52 - 80tr) x 30%")
                    .amount(taxVal)
                    .build());
            remaining -= 28000000;
        }

        // Bậc 7: 35% (Trên 80tr)
        if (remaining > 0) {
            BigDecimal taxVal = BigDecimal.valueOf(remaining * 0.35).setScale(0, RoundingMode.HALF_UP);
            details.add(PayslipDetailResponse.TaxTierDetail.builder()
                    .label("Bậc 7 (> 80tr) x 35%")
                    .amount(taxVal)
                    .build());
        }

        return details;
    }

    // Hàm này chịu trách nhiệm cộng tổng các dòng trong List và update vào Header
    private void recalculateTotals(PayslipsSnapshot slip, SalaryProfile profile) {
        BigDecimal insBase = (profile.getInsuranceAmount() != null && profile.getInsuranceAmount().compareTo(BigDecimal.ZERO) > 0)
                ? profile.getInsuranceAmount() : profile.getBaseSalary();

        BigDecimal totalAllowancesIncome = BigDecimal.ZERO; // Tổng phụ cấp cộng vào lương
        BigDecimal totalInsuranceDeduction = BigDecimal.ZERO;
        BigDecimal otherDeductions = BigDecimal.ZERO; // Các khoản trừ khác (bao gồm ứng lương nhập tay)
        long dependents = 0;

        for (PayslipAllowance item : slip.getPeriodicAllowances()) {
            String name = item.getAllowanceName().toUpperCase();

            if (item.getType() == AllowanceType.INCOME) {
                // Tất cả các khoản INCOME nhập tay hoặc config đều cộng vào đây
                totalAllowancesIncome = totalAllowancesIncome.add(item.getAmount());
            } else {
                // Xử lý các khoản DEDUCTION
                if (List.of("BHXH", "BHYT", "BHTN").contains(name)) {
                    BigDecimal money = insBase.multiply(item.getAmount()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
                    item.setAmount(money);
                    totalInsuranceDeduction = totalInsuranceDeduction.add(money);
                } else if (name.equals("DEPENDENTS")) {
                    dependents = item.getAmount().longValue();
                } else {
                    // Các khoản trừ nhập tay như "Ứng lương", "Phạt vi phạm" rơi vào đây
                    otherDeductions = otherDeductions.add(item.getAmount());
                }
            }
        }

        // 1. Cập nhật các trường snapshot
        slip.setAllowanceAmount(totalAllowancesIncome);
        slip.setInsuranceDeduction(totalInsuranceDeduction);
        slip.setOtherDeductionAmount(otherDeductions);

        // 2. Tính Gross Salary (Tổng thu nhập trước thuế/BH)
        BigDecimal gross = slip.getSalaryAmount()
                .add(Optional.ofNullable(slip.getBonusAmount()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(slip.getOtSalaryAmount()).orElse(BigDecimal.ZERO))
                .add(totalAllowancesIncome);
        slip.setGrossSalary(gross);

        // 3. Tính Thuế TNCN (Sau khi đã trừ BH và Giảm trừ gia cảnh)
        BigDecimal relief = SELF_RELIEF.add(DEPENDENT_RELIEF_UNIT.multiply(BigDecimal.valueOf(dependents)));
        BigDecimal taxableIncome = gross.subtract(totalInsuranceDeduction).subtract(relief);
        slip.setTaxDeduction(calculateProgressiveTax(taxableIncome.max(BigDecimal.ZERO)));

        // 4. Tính Net Salary (Thực lĩnh cuối cùng)
        // Công thức: Gross - Bảo hiểm - Thuế - Phạt đi muộn - Các khoản trừ khác (Ứng lương) - Tạm ứng (nếu có trường riêng)
        BigDecimal totalDeductions = totalInsuranceDeduction
                .add(slip.getTaxDeduction())
                .add(Optional.ofNullable(slip.getLatePenaltyAmount()).orElse(BigDecimal.ZERO))
                .add(otherDeductions)
                .add(Optional.ofNullable(slip.getAdvancePayment()).orElse(BigDecimal.ZERO));

        slip.setNetSalary(gross.subtract(totalDeductions));
    }

    private void recalculateTotals(PayslipsSnapshot slip) {
        SalaryProfile profile = salaryProfileRepo.findByUserId(slip.getUser().getId()).orElse(null);
        if (profile != null) recalculateTotals(slip, profile);
    }

    private String formatMoney(BigDecimal amount) {
        return String.format("%,.0f", amount);
    }
    @Override
    public ByteArrayInputStream exportMonthlyPayroll(Integer month, Integer year) {
        // 1. Tìm kỳ lương
        SalaryCycle cycle = salaryCycleRepo.findByMonthAndYear(month, year)
                .orElseThrow(() -> new RuntimeException("Kỳ lương tháng " + month + "/" + year + " không tồn tại."));

        // 2. Lấy danh sách snapshot
        List<PayslipsSnapshot> snapshots = payslipRepo.findBySalaryCycleId(cycle.getId());

        // 3. Convert sang DTO (để reuse logic format nếu cần, hoặc dùng thẳng entity nếu Utils hỗ trợ)
        // Ở đây Utils nhận List<PayslipViewDto> nên ta convert
        List<PayslipViewDto> dtos = snapshots.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        // 4. Gọi Utils (vừa gộp)
        return ExcelExportUtils.exportPayrollToExcel(dtos, month, year);
    }
}