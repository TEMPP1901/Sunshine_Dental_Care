package sunshine_dental_care.dto.huybro_checkout;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutCreateRequestDto {

    @NotBlank
    @Size(min = 3, max = 50)
    private String paymentType; // COD, BANK_TRANSFER

    @Size(max = 50)
    private String paymentChannel; // PAYPAL, CASH_ON_DELIVERY, ...

    @Size(max = 10)
    private String currency; // USD, VND

    private String customerFullName;
    @Email
    private String customerEmail;
    private String customerPhone;
    private String shippingAddress;

    @Size(max = 400)
    private String note;
}
