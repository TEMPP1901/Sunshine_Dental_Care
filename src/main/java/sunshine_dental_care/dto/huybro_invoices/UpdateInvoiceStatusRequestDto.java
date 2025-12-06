package sunshine_dental_care.dto.huybro_invoices;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInvoiceStatusRequestDto implements Serializable {

    // Validate chặt chẽ các trạng thái cho phép
    // Frontend KHÔNG được tự ý gửi text rác.
    @NotNull(message = "Invoice status is required")
    @Pattern(
            regexp = "^(CONFIRMED|PROCESSING|COMPLETED|CANCELLED)$",
            message = "Status must be one of: CONFIRMED, PROCESSING, COMPLETED, CANCELLED"
    )
    private String newStatus;

    @Size(max = 400, message = "Note cannot exceed 400 characters")
    private String note; // Ghi chú lý do (ví dụ: Lý do hủy, Mã vận đơn...)
}