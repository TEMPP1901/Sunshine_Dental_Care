package sunshine_dental_care.dto.huybro_payroll;

import lombok.Data;

@Data
public class PayslipSearchRequest {
    // 1. Context (Bắt buộc)
    private Integer month;
    private Integer year;

    // 2. Filter (Tìm kiếm)
    private String keyword;

    // 3. Pagination (FE truyền vào)
    private int page = 0; // Mặc định trang 0 (Trang đầu)

    private int size = 8;
}