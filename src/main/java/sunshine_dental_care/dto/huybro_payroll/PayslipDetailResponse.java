package sunshine_dental_care.dto.huybro_payroll;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PayslipDetailResponse {
    // 1. Thông tin chung
    private Integer id;
    private String userFullName;
    private String userCode;
    private String userEmail;
    private String roleName;
    private Integer month;
    private Integer year;
    private String status;
    private String createdAt;

    // 2. Thông tin chấm công (để giải trình lương cứng)
    private String workType; // "SHIFT_BASED" or "MONTHLY"
    private String workActual; // Ví dụ: "24"
    private String workStandard; // Ví dụ: "26"
    private String workFormula; // String giải thích: "(18.000.000 / 26) * 24"

    // 3. Danh sách khoản cộng (Income) - FE chỉ việc in ra
    private List<LineItem> incomeItems;
    private BigDecimal totalIncome; // Gross Salary

    // 4. Danh sách khoản trừ (Deduction) - FE chỉ việc in ra
    private List<LineItem> deductionItems;
    private BigDecimal totalDeduction;

    // 5. Thông tin Thuế (để giải trình thuế)
    private TaxBreakdown taxBreakdown;

    // 6. Thực lĩnh
    private BigDecimal netSalary;
    private String note;

    @Data
    @Builder
    public static class LineItem {
        private String name;        // Tên khoản (VD: Lương cứng, Phụ cấp ăn trưa...)
        private BigDecimal amount;  // Số tiền
        private String description; // Giải thích (VD: 5 hours * 50.000/hr)
        private boolean isHighlight; // Có tô đậm không
    }

    @Data
    @Builder
    public static class TaxBreakdown {
        private BigDecimal grossIncome;       // Tổng thu nhập
        private BigDecimal insuranceDeduction;// Trừ bảo hiểm
        private BigDecimal selfRelief;        // Giảm trừ gia cảnh (11tr)
        private BigDecimal taxableIncome;     // Thu nhập tính thuế
        private BigDecimal taxAmount;         // Thuế phải đóng
        // [NEW] Thêm danh sách chi tiết từng bậc thuế
        private List<TaxTierDetail> details;
    }
    @Data
    @Builder
    public static class TaxTierDetail {
        private String label;
        private BigDecimal amount;
    }
}