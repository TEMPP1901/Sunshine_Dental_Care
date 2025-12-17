package sunshine_dental_care.api.huybro_payroll;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_payroll.*;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.huybro_custom.UserCustomRepository;
import sunshine_dental_care.services.huybro_payroll.interfaces.PayrollService;


import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final UserCustomRepository userRepo;

    private static final List<String> ALLOWED_ROLES = Arrays.asList(
            "HR", "DOCTOR", "RECEPTION", "ACCOUNTANT"
    );

    // ==========================================
    // API SEARCH & CONFIG
    // ==========================================

    @GetMapping("/employees/search")
    public ResponseEntity<List<UserSearchResponse>> searchEmployees(@RequestParam String keyword) {
        List<UserSearchResponse> users = userRepo.searchStaff(ALLOWED_ROLES, keyword.trim());
        return ResponseEntity.ok(users);
    }

    // [UPDATED] Trả về DTO SalaryProfileResponse
    @GetMapping("/config/{userId}")
    public ResponseEntity<SalaryProfileResponse> getSalaryProfile(@PathVariable Integer userId) {
        try {
            // Gọi qua service để đảm bảo trả về DTO
            SalaryProfileResponse profile = payrollService.getProfileByUserId(userId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            // Nếu chưa có profile thì trả về 204 No Content
            return ResponseEntity.noContent().build();
        }
    }

    // [UPDATED] Trả về DTO SalaryProfileResponse
    @PostMapping("/config")
    public ResponseEntity<SalaryProfileResponse> saveSalaryProfile(@RequestBody SalaryProfileRequest request) {
        SalaryProfileResponse profile = payrollService.createOrUpdateProfile(request);
        return ResponseEntity.ok(profile);
    }

    // ==========================================
    // API PAYROLL PROCESS
    // ==========================================

    @PostMapping("/calculate")
    public ResponseEntity<List<PayslipViewDto>> calculatePayroll(@RequestBody PayrollCalculationRequest request) {
        List<PayslipViewDto> payslips = payrollService.calculatePayroll(request);
        return ResponseEntity.ok(payslips);
    }

    @GetMapping("/payslips")
    public ResponseEntity<Page<PayslipViewDto>> getPayslips(
            @ModelAttribute PayslipSearchRequest request) {
        if (request.getMonth() == null || request.getYear() == null) {
            throw new RuntimeException("Vui lòng chọn Tháng và Năm!");
        }
        Page<PayslipViewDto> payslips = payrollService.getPayslips(request);
        return ResponseEntity.ok(payslips);
    }

    @PatchMapping("/payslips/{id}/advance")
    public ResponseEntity<PayslipViewDto> updateAdvancePayment(
            @PathVariable Integer id,
            @RequestParam BigDecimal amount) {
        PayslipViewDto updatedSlip = payrollService.updateAdvancePayment(id, amount);
        return ResponseEntity.ok(updatedSlip);
    }

    @PostMapping("/finalize")
    public ResponseEntity<String> finalizeCycle(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        payrollService.finalizeCycle(month, year);
        return ResponseEntity.ok("Đã chốt kỳ lương tháng " + month + "/" + year + " thành công!");
    }

    @GetMapping("/payslips/{id}/detail")
    public ResponseEntity<PayslipDetailResponse> getPayslipDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(payrollService.getPayslipDetailById(id));
    }

    @PostMapping("/payslips/{id}/items")
    public ResponseEntity<PayslipViewDto> addManualItem(
            @PathVariable Integer id,
            @RequestBody ManualItemRequest request) {
        return ResponseEntity.ok(payrollService.addManualItem(id, request));
    }

    @DeleteMapping("/payslips/{id}/items/{itemId}")
    public ResponseEntity<PayslipViewDto> removeManualItem(
            @PathVariable Integer id,
            @PathVariable Integer itemId) {
        return ResponseEntity.ok(payrollService.removeManualItem(id, itemId));
    }
    @GetMapping("/missing-configs")
    public ResponseEntity<List<UserSearchResponse>> getMissingConfigs() {
        return ResponseEntity.ok(payrollService.getMissingConfigs());
    }
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportPayroll(
            @RequestParam Integer month,
            @RequestParam Integer year
    ) {
        ByteArrayInputStream stream = payrollService.exportMonthlyPayroll(month, year);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=Payroll_" + month + "_" + year + ".xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }
}