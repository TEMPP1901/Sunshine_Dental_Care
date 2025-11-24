package sunshine_dental_care.dto.receptionDTO.bookingDto;
import lombok.Data;

@Data
public class ServiceDTO {
    private Integer id;
    private String serviceName;
    private String category;
    private Integer defaultDuration;
    private String description;
}
