package sunshine_dental_care.dto.receptionDTO.bookingDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ServiceDTO {
    private Integer id;
    private String serviceName;
    private String category;
    private Integer defaultDuration;
    private String description;
    private BigDecimal price;

    private List<ServiceVariantDTO> variants;
}
