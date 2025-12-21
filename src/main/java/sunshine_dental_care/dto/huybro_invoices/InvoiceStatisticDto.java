package sunshine_dental_care.dto.huybro_invoices;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceStatisticDto {
    private String status;
    private Long totalCount;
}