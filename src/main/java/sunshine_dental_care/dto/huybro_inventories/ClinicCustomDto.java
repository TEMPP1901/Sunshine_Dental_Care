package sunshine_dental_care.dto.huybro_inventories;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicCustomDto {
    private Integer clinicId;
    private String clinicName;
    private String address;
}
