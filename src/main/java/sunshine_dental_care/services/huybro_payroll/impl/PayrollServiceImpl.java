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
        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        SalaryProfile profile = salaryProfileRepo.findByUserId(request.getUserId())
                .orElse(new SalaryProfile());

        profile.setUser(user);
        profile.setCalculationType(request.getCalculationType());
        profile.setBaseSalary(request.getBaseSalary());

        if (request.getCalculationType() == SalaryCalculationType.SHIFT_BASED) {
            profile.setStandardShifts(request.getStandardShifts());
            profile.setStandardWorkDays(0.0);
        } else {
            profile.setStandardWorkDays(request.getStandardWorkDays());
            profile.setStandardShifts(0);
        }

        profile.setOtRate(request.getOtRate());
        profile.setOverShiftRate(request.getOverShiftRate());
        profile.setLateDeductionRate(request.getLateDeductionRate());
        profile.setInsuranceAmount(request.getInsuranceAmount());
        profile.setTaxType(TaxType.PROGRESSIVE);

        if (profile.getAllowances() == null) {
            profile.setAllowances(new ArrayList<>());
        } else {
            profile.getAllowances().clear();
        }

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

        SalaryProfile savedProfile = salaryProfileRepo.save(profile);
        return mapToProfileResponse(savedProfile);
    }

    // =================================================================================
    // 2. TÍNH TOÁN LƯƠNG (CORE CALCULATION LOGIC)
    // =================================================================================

    @Override
    @Transactional
    public List<PayslipViewDto> calculatePayroll(PayrollCalculationRequest request) {
        log.info(">>> Calculating payroll for {}/{}", request.getMonth(), request.getYear());

        // 1. Khởi tạo/Lấy kỳ lương
        SalaryCycle cycle = getOrCreateCycle(request.getMonth(), request.getYear());
        if (cycle.getStatus() == PeriodStatus.FINALIZED) {
            throw new RuntimeException("Kỳ lương đã CHỐT. Không thể tính lại!");
        }

        // 2. Lấy danh sách nhân viên cần tính
        List<SalaryProfile> profiles;
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            profiles = request.getUserIds().stream()
                    .map(uid -> salaryProfileRepo.findByUserId(uid).orElse(null))
                    .filter(p -> p != null)
                    .collect(Collectors.toList());
        } else {
            List<String> ALLOWED_ROLES = List.of("HR", "DOCTOR", "RECEPTION", "ACCOUNTANT");
            profiles = salaryProfileRepo.findAll().stream()
                    .filter(p -> p.getUser().getUserRoles().stream()
                            .anyMatch(ur -> ur.getIsActive() && ALLOWED_ROLES.contains(ur.getRole().getRoleName())))
                    .collect(Collectors.toList());
        }

        List<PayslipsSnapshot> results = new ArrayList<>();

        // 3. Vòng lặp tính lương từng người
        for (SalaryProfile profile : profiles) {
            // Lấy dữ liệu chấm công
            List<Attendance> attendances = attendanceRepo.findByUserIdAndWorkDateBetween(
                    profile.getUser().getId(), cycle.getStartDate(), cycle.getEndDate());

            // Lấy hoặc tạo mới phiếu lương (Snapshot)
            PayslipsSnapshot slip = payslipRepo.findBySalaryCycleIdAndUserId(cycle.getId(), profile.getUser().getId())
                    .orElse(new PayslipsSnapshot());

            // --- A. SNAPSHOT INFO (Lưu thông tin gốc) ---
            slip.setSalaryCycle(cycle);
            slip.setUser(profile.getUser());
            slip.setBaseSalarySnapshot(profile.getBaseSalary());
            slip.setStandardWorkDaysSnapshot(profile.getStandardWorkDays());
            slip.setStandardShiftsSnapshot(profile.getStandardShifts());

            // --- B. TÍNH LƯƠNG CƠ BẢN (WORK/SHIFT) ---
            BigDecimal salaryAmount = BigDecimal.ZERO;
            BigDecimal bonusAmount = BigDecimal.ZERO;

            if (profile.getCalculationType() == SalaryCalculationType.MONTHLY) {
                // Logic tính theo ngày công
                long workDays = attendances.stream()
                        .filter(a -> !Boolean.TRUE.equals(a.getIsOvertime()))
                        .map(Attendance::getWorkDate).distinct().count();
                slip.setActualWorkDays((double) workDays);

                if (profile.getStandardWorkDays() != null && profile.getStandardWorkDays() > 0) {
                    BigDecimal dailyRate = profile.getBaseSalary()
                            .divide(BigDecimal.valueOf(profile.getStandardWorkDays()), 2, RoundingMode.HALF_UP);
                    salaryAmount = dailyRate.multiply(BigDecimal.valueOf(workDays));
                }
            } else if (profile.getCalculationType() == SalaryCalculationType.SHIFT_BASED) {
                // Logic tính theo ca (Bác sĩ)
                long shiftCount = attendances.stream()
                        .filter(a -> !Boolean.TRUE.equals(a.getIsOvertime()))
                        .filter(a -> a.getShiftType() != null && (a.getShiftType().equals("MORNING") || a.getShiftType().equals("AFTERNOON")))
                        .count();
                slip.setActualShifts((int) shiftCount);
                int stdShifts = profile.getStandardShifts() != null ? profile.getStandardShifts() : 0;

                if (stdShifts > 0) {
                    if (shiftCount <= stdShifts) {
                        BigDecimal shiftRate = profile.getBaseSalary()
                                .divide(BigDecimal.valueOf(stdShifts), 2, RoundingMode.HALF_UP);
                        salaryAmount = shiftRate.multiply(BigDecimal.valueOf(shiftCount));
                    } else {
                        salaryAmount = profile.getBaseSalary();
                        long extra = shiftCount - stdShifts;
                        if (profile.getOverShiftRate() != null) {
                            bonusAmount = profile.getOverShiftRate().multiply(BigDecimal.valueOf(extra));
                        }
                    }
                }
            }
            slip.setSalaryAmount(salaryAmount);
            slip.setBonusAmount(bonusAmount);

            // --- C. TÍNH OT (OVERTIME) ---
            BigDecimal otSalaryAmount = BigDecimal.ZERO;
            double totalOtHours = attendances.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsOvertime()))
                    .mapToDouble(a -> a.getActualWorkHours() != null ? a.getActualWorkHours().doubleValue() : 0.0)
                    .sum();

            slip.setTotalOtHours(totalOtHours);

            if (totalOtHours > 0 && profile.getOtRate() != null && profile.getOtRate().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal standardDays = (profile.getStandardWorkDays() != null && profile.getStandardWorkDays() > 0)
                        ? BigDecimal.valueOf(profile.getStandardWorkDays())
                        : BigDecimal.valueOf(26);

                BigDecimal hourlyRate = profile.getBaseSalary()
                        .divide(standardDays, 2, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(8), 2, RoundingMode.HALF_UP);

                otSalaryAmount = hourlyRate
                        .multiply(BigDecimal.valueOf(totalOtHours))
                        .multiply(profile.getOtRate());
            }
            slip.setOtSalaryAmount(otSalaryAmount);

            // --- D. TÍNH PHẠT (LATE PENALTY) ---
            // [NOTE] Đưa lên trước để hàm recalculateTotals sử dụng
            int totalLate = attendances.stream().mapToInt(a -> a.getLateMinutes() != null ? a.getLateMinutes() : 0).sum();
            slip.setTotalLateMinutes(totalLate);
            BigDecimal latePenalty = BigDecimal.ZERO;
            if (profile.getLateDeductionRate() != null) {
                latePenalty = profile.getLateDeductionRate().multiply(BigDecimal.valueOf(totalLate));
            }
            slip.setLatePenaltyAmount(latePenalty);

            // --- E. BẢO HIỂM (INSURANCE) ---
            // [NOTE] Đưa lên trước để hàm recalculateTotals sử dụng
            BigDecimal insurance = profile.getInsuranceAmount() != null ? profile.getInsuranceAmount() : BigDecimal.ZERO;
            slip.setInsuranceDeduction(insurance);

            // Set Advance = 0 vì giờ ta dùng List Deduction động, không dùng cột cứng này
            slip.setAdvancePayment(BigDecimal.ZERO);

            // --- F. QUẢN LÝ LIST PHỤ CẤP (SYNC TỪ CONFIG) ---

            // 1. Khởi tạo list nếu null
            if (slip.getPeriodicAllowances() == null) {
                slip.setPeriodicAllowances(new ArrayList<>());
            }

            // 2. Xóa các khoản "System Generated" cũ để tránh trùng lặp khi tính lại
            // (Giữ lại các khoản nhập tay - Manual)
            slip.getPeriodicAllowances().removeIf(PayslipAllowance::getIsSystemGenerated);

            // 3. Copy lại từ Config (Salary Profile) vào List
            if (profile.getAllowances() != null) {
                for (SalaryAllowance configItem : profile.getAllowances()) {
                    PayslipAllowance item = new PayslipAllowance();
                    item.setPayslip(slip);
                    item.setAllowanceName(configItem.getAllowanceName());
                    item.setAmount(configItem.getAmount());
                    item.setType(configItem.getType());
                    item.setIsSystemGenerated(true); // Đánh dấu là từ Hợp đồng
                    item.setNote("From Contract");

                    slip.getPeriodicAllowances().add(item);
                }
            }

            // --- G. TÍNH TOÁN TỔNG HỢP (FINAL CALCULATION) ---
            // Hàm này sẽ tự động:
            // 1. Cộng tổng List Income -> Update Gross
            // 2. Tính Thuế
            // 3. Cộng tổng List Deduction (bao gồm Ứng lương) + Phạt + BH -> Update Net
            recalculateTotals(slip);

            // --- H. LƯU DATABASE ---
            results.add(payslipRepo.save(slip));
        }

        return results.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    // =================================================================================
    // 3. HIỂN THỊ CHI TIẾT
    // =================================================================================

    @Override
    public PayslipDetailResponse getPayslipDetailById(Integer id) {
        PayslipsSnapshot slip = payslipRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payslip not found"));
        SalaryProfile profile = salaryProfileRepo.findByUserId(slip.getUser().getId()).orElse(null);

        // --- A. INCOME ---
        List<PayslipDetailResponse.LineItem> incomes = new ArrayList<>();
        // ... Logic tạo dòng thu nhập (Giữ nguyên như cũ) ...
        String formula;
        if (slip.getStandardShiftsSnapshot() > 0) {
            if (slip.getActualShifts() >= slip.getStandardShiftsSnapshot()) {
                formula = String.format("Đã đạt định mức (%d/%d ca) \u2192 Nhận 100%% Lương cứng",
                        slip.getActualShifts(), slip.getStandardShiftsSnapshot());
            } else {
                formula = String.format("(%s / %d ca) × %d ca thực tế",
                        formatMoney(slip.getBaseSalarySnapshot()), slip.getStandardShiftsSnapshot(), slip.getActualShifts());
            }
        } else {
            if (slip.getActualWorkDays() >= slip.getStandardWorkDaysSnapshot()) {
                formula = String.format("Đã đủ ngày công (%.1f/%.1f) \u2192 Nhận 100%% Lương cứng",
                        slip.getActualWorkDays(), slip.getStandardWorkDaysSnapshot());
            } else {
                formula = String.format("(%s / %.1f ngày) × %.1f ngày thực tế",
                        formatMoney(slip.getBaseSalarySnapshot()), slip.getStandardWorkDaysSnapshot(), slip.getActualWorkDays());
            }
        }
        incomes.add(PayslipDetailResponse.LineItem.builder()
                .name("Standard Salary (Lương cơ bản)")
                .amount(slip.getSalaryAmount()).description(formula).isHighlight(true).build());

        if (slip.getOtSalaryAmount() != null && slip.getOtSalaryAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = (profile != null && profile.getOtRate() != null) ? profile.getOtRate() : BigDecimal.ONE;
            incomes.add(PayslipDetailResponse.LineItem.builder()
                    .name("Overtime Pay (Lương làm thêm giờ)")
                    .amount(slip.getOtSalaryAmount())
                    .description(String.format("Tính theo hệ số nhân: x%s (Multiplier)", multiplier)).build());
        }

        if (slip.getBonusAmount() != null && slip.getBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = (profile != null && profile.getOverShiftRate() != null) ? profile.getOverShiftRate() : BigDecimal.ZERO;
            long extra = 0;
            if(slip.getStandardShiftsSnapshot() > 0 && slip.getActualShifts() > slip.getStandardShiftsSnapshot()) {
                extra = slip.getActualShifts() - slip.getStandardShiftsSnapshot();
            }
            incomes.add(PayslipDetailResponse.LineItem.builder()
                    .name("Over Shift Bonus (Thưởng vượt ca)")
                    .amount(slip.getBonusAmount())
                    .description(String.format("Dư %d ca × %s /ca", extra, formatMoney(rate))).build());
        }
        // --- B. DEDUCTION ---
        List<PayslipDetailResponse.LineItem> deductions = new ArrayList<>();
        // ... Logic tạo dòng khấu trừ (Giữ nguyên như cũ) ...
        if (slip.getLatePenaltyAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = (profile != null && profile.getLateDeductionRate() != null) ? profile.getLateDeductionRate() : BigDecimal.ZERO;
            deductions.add(PayslipDetailResponse.LineItem.builder()
                    .name("Phạt đi muộn (Late Penalty)")
                    .amount(slip.getLatePenaltyAmount())
                    .description(String.format("Trễ tổng %d phút × %s /phút", slip.getTotalLateMinutes(), formatMoney(rate))).build());
        }
        if (slip.getInsuranceDeduction().compareTo(BigDecimal.ZERO) > 0) {
            deductions.add(PayslipDetailResponse.LineItem.builder().name("Bảo hiểm (BHXH/BHYT)").amount(slip.getInsuranceDeduction()).description("Trừ cố định").build());
        }
       // --- XỬ LÝ LIST PHỤ CẤP (CHỈ LẤY BIẾN ĐỘNG / MANUAL) ---
        if (slip.getPeriodicAllowances() != null) {

            // A. Map INCOME
            slip.getPeriodicAllowances().stream()
                    .filter(item -> item.getType() == AllowanceType.INCOME)
                    .forEach(item -> {
                        incomes.add(PayslipDetailResponse.LineItem.builder()
                                .name("Phụ cấp: " + item.getAllowanceName())
                                .amount(item.getAmount())
                                .description(item.getNote())
                                .isHighlight(false)
                                .build());
                    });

            // B. Map DEDUCTION
            slip.getPeriodicAllowances().stream()
                    .filter(item -> item.getType() == AllowanceType.DEDUCTION)
                    .forEach(item -> {
                        deductions.add(PayslipDetailResponse.LineItem.builder()
                                .name("Khấu trừ: " + item.getAllowanceName())
                                .amount(item.getAmount())
                                .description(item.getNote())
                                .isHighlight(false)
                                .build());
                    });
        }
        if (slip.getAdvancePayment().compareTo(BigDecimal.ZERO) > 0) {
            deductions.add(PayslipDetailResponse.LineItem.builder().name("Tạm ứng (Advance)").amount(slip.getAdvancePayment()).description("Đã ứng trước trong kỳ").build());
        }
        if (slip.getTaxDeduction().compareTo(BigDecimal.ZERO) > 0) {
            deductions.add(PayslipDetailResponse.LineItem.builder()
                    .name("Personal Income Tax (Thuế TNCN) ")
                    .amount(slip.getTaxDeduction()).description("Xem chi tiết bên dưới").isHighlight(true).build());
        }

        // --- C. TAX BREAKDOWN ---
        BigDecimal selfRelief = BigDecimal.ZERO;
        BigDecimal taxableIncome = slip.getGrossSalary().subtract(slip.getInsuranceDeduction());
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) taxableIncome = BigDecimal.ZERO;

        // Lấy chi tiết thuế để hiển thị
        List<PayslipDetailResponse.TaxTierDetail> taxDetails = getTaxDetails(taxableIncome);

        // [VALIDATION] Mặc dù ta đã lưu thuế vào slip.taxDeduction, nhưng để hiển thị
        // "Tax Payable" khớp 100% với danh sách details bên dưới, ta lấy tổng từ details.
        // Điều này chỉ mang tính hiển thị, giá trị thực tế đã lưu trong DB từ hàm calculatePayroll.
        BigDecimal taxSumFromDetails = taxDetails.stream()
                .map(PayslipDetailResponse.TaxTierDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayslipDetailResponse.TaxBreakdown taxInfo = PayslipDetailResponse.TaxBreakdown.builder()
                .grossIncome(slip.getGrossSalary())
                .insuranceDeduction(slip.getInsuranceDeduction())
                .selfRelief(selfRelief)
                .taxableIncome(taxableIncome)
                .taxAmount(taxSumFromDetails) // Dùng số này để khớp với details
                .details(taxDetails)
                .build();

        return PayslipDetailResponse.builder()
                .id(slip.getId())
                .userFullName(slip.getUser().getFullName())
                .userCode(slip.getUser().getCode())
                .userEmail(slip.getUser().getEmail())
                .roleName(slip.getUser().getUserRoles().stream().findFirst().map(ur -> ur.getRole().getRoleName()).orElse("N/A"))
                .month(slip.getSalaryCycle().getMonth())
                .year(slip.getSalaryCycle().getYear())
                .status(slip.getSalaryCycle().getStatus().name())
                .createdAt(slip.getCreatedAt().toString())
                .workType(slip.getStandardShiftsSnapshot() > 0 ? "SHIFT_BASED" : "MONTHLY")
                .workActual(slip.getStandardShiftsSnapshot() > 0 ? slip.getActualShifts().toString() : slip.getActualWorkDays().toString())
                .workStandard(slip.getStandardShiftsSnapshot() > 0 ? slip.getStandardShiftsSnapshot().toString() : slip.getStandardWorkDaysSnapshot().toString())
                .workFormula(formula)
                .incomeItems(incomes)
                .totalIncome(slip.getGrossSalary())
                .deductionItems(deductions)
                .totalDeduction(slip.getGrossSalary().subtract(slip.getNetSalary()))
                .taxBreakdown(taxInfo)
                .netSalary(slip.getNetSalary())
                .note(slip.getNote())
                .incomeItems(incomes)
                .deductionItems(deductions)
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

        PayslipAllowance item = new PayslipAllowance();
        item.setPayslip(slip);
        item.setAllowanceName(request.getName());
        item.setAmount(request.getAmount());
        item.setType(request.getType());
        item.setIsSystemGenerated(false); // Quan trọng: Đây là nhập tay
        item.setNote(request.getNote());

        slip.getPeriodicAllowances().add(item);

        // Tính lại số liệu
        recalculateTotals(slip);

        PayslipsSnapshot saved = payslipRepo.save(slip);
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
    private void recalculateTotals(PayslipsSnapshot slip) {
        // 1. Tính tổng Income từ List
        BigDecimal totalIncomeAllowances = slip.getPeriodicAllowances().stream()
                .filter(a -> a.getType() == AllowanceType.INCOME)
                .map(PayslipAllowance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        slip.setAllowanceAmount(totalIncomeAllowances);

        // 2. Tính tổng Deduction từ List (Bao gồm cả Ứng lương, Phạt...)
        BigDecimal totalDeductionAllowances = slip.getPeriodicAllowances().stream()
                .filter(a -> a.getType() == AllowanceType.DEDUCTION)
                .map(PayslipAllowance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        slip.setOtherDeductionAmount(totalDeductionAllowances);

        // 3. Tính lại Gross
        // Gross = Lương cứng + Bonus + OT + Phụ cấp (Income)
        BigDecimal gross = slip.getSalaryAmount()
                .add(slip.getBonusAmount() != null ? slip.getBonusAmount() : BigDecimal.ZERO)
                .add(slip.getOtSalaryAmount() != null ? slip.getOtSalaryAmount() : BigDecimal.ZERO)
                .add(totalIncomeAllowances);
        slip.setGrossSalary(gross);

        // 4. Tính lại Thuế (Dựa trên Gross mới)
        BigDecimal taxableIncome = gross.subtract(slip.getInsuranceDeduction());
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) taxableIncome = BigDecimal.ZERO;
        BigDecimal tax = calculateProgressiveTax(taxableIncome);
        slip.setTaxDeduction(tax);

        // 5. Tính lại Net
        // Net = Gross - (Bảo hiểm + Thuế + Phạt đi muộn + Các khoản trừ khác trong List)
        // Lưu ý: advancePayment cũ coi như bỏ (hoặc cộng vào nếu chưa migration data cũ)
        BigDecimal totalDeductions = slip.getInsuranceDeduction()
                .add(tax)
                .add(slip.getLatePenaltyAmount())
                .add(totalDeductionAllowances); // List Deduction đã bao gồm Ứng lương

        slip.setNetSalary(gross.subtract(totalDeductions));
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