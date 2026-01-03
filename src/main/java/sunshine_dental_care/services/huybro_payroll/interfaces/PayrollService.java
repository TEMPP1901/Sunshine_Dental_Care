package sunshine_dental_care.services.huybro_payroll.interfaces;

import org.springframework.data.domain.Page;
import sunshine_dental_care.dto.huybro_payroll.*; // Import DTO

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

public interface PayrollService {
    SalaryProfileResponse createOrUpdateProfile(SalaryProfileRequest request);
    SalaryProfileResponse getProfileByUserId(Integer userId);
    List<PayslipViewDto> calculatePayroll(PayrollCalculationRequest request);
    void finalizeCycle(Integer month, Integer year);
    Page<PayslipViewDto> getPayslips(PayslipSearchRequest request);
    PayslipViewDto updateAdvancePayment(Integer payslipId, BigDecimal amount);
    PayslipDetailResponse getPayslipDetailById(Integer id);
    PayslipViewDto addManualItem(Integer payslipId, ManualItemRequest request);
    PayslipViewDto removeManualItem(Integer payslipId, Integer itemId);
    List<UserSearchResponse> getMissingConfigs();
    ByteArrayInputStream exportMonthlyPayroll(Integer month, Integer year);
}