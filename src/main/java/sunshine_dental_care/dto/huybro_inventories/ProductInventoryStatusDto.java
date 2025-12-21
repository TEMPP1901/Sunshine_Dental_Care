package sunshine_dental_care.dto.huybro_inventories;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductInventoryStatusDto {
    private Integer productId;
    private String productName;
    private String sku;
    private Integer totalQuantity;
    private List<ClinicInventoryDto> breakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClinicInventoryDto {
        private Integer clinicId;
        private String clinicName;
        private Integer quantity;
    }
}