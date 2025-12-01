package sunshine_dental_care.services.huybro_checkout.paypal.services.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaypalApiClient {

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.secret}")
    private String secret;

    @Value("${paypal.base-url}")
    private String baseUrl;

    @Value("${paypal.return-url}")
    private String returnUrl;

    @Value("${paypal.cancel-url}")
    private String cancelUrl;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    public static class CreateOrderResult {
        private final String orderId;
        private final String approveUrl;

        public CreateOrderResult(String orderId, String approveUrl) {
            this.orderId = orderId;
            this.approveUrl = approveUrl;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getApproveUrl() {
            return approveUrl;
        }
    }

    public static class CaptureOrderResult {
        private final String status;
        private final String captureId;
        private final String payerEmail;

        public CaptureOrderResult(String status, String captureId, String payerEmail) {
            this.status = status;
            this.captureId = captureId;
            this.payerEmail = payerEmail;
        }

        public String getStatus() {
            return status;
        }

        public String getCaptureId() {
            return captureId;
        }

        public String getPayerEmail() {
            return payerEmail;
        }
    }

    private String obtainAccessToken() {
        String url = baseUrl + "/v1/oauth2/token";

        String basicAuth = clientId + ":" + secret;
        String encodedAuth = Base64.encodeBase64String(basicAuth.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(encodedAuth);

        HttpEntity<String> entity = new HttpEntity<>("grant_type=client_credentials", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to obtain PayPal access token");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PayPal access token response", e);
        }
    }

    public CreateOrderResult createOrder(BigDecimal totalAmount,
                                         String currency,
                                         String invoiceCode) {
        String accessToken = obtainAccessToken();

        String url = baseUrl + "/v2/checkout/orders";

        BigDecimal normalized = totalAmount
                .setScale(2, RoundingMode.HALF_UP);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String bodyJson;
        try {
            // Xây JSON request
            JsonNode root = objectMapper.createObjectNode()
                    .put("intent", "CAPTURE");

            JsonNode amountNode = objectMapper.createObjectNode()
                    .put("currency_code", currency)
                    .put("value", normalized.toPlainString());

            JsonNode purchaseUnit = objectMapper.createObjectNode()
                    .set("amount", amountNode);
            ((com.fasterxml.jackson.databind.node.ObjectNode) purchaseUnit)
                    .put("invoice_id", invoiceCode);

            com.fasterxml.jackson.databind.node.ArrayNode purchaseUnits =
                    objectMapper.createArrayNode().add(purchaseUnit);

            ((com.fasterxml.jackson.databind.node.ObjectNode) root)
                    .set("purchase_units", purchaseUnits);

            JsonNode appContext = objectMapper.createObjectNode()
                    .put("return_url", returnUrl)
                    .put("cancel_url", cancelUrl);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root)
                    .set("application_context", appContext);

            bodyJson = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build PayPal create order request", e);
        }

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to create PayPal order");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String orderId = root.path("id").asText();

            String approveUrl = null;
            for (JsonNode link : root.path("links")) {
                if ("approve".equalsIgnoreCase(link.path("rel").asText())) {
                    approveUrl = link.path("href").asText();
                    break;
                }
            }

            if (approveUrl == null) {
                throw new IllegalStateException("PayPal approve URL not found");
            }

            return new CreateOrderResult(orderId, approveUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PayPal create order response", e);
        }
    }

    public CaptureOrderResult captureOrder(String orderId) {
        String accessToken = obtainAccessToken();

        String url = baseUrl + "/v2/checkout/orders/" + orderId + "/capture";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to capture PayPal order");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText();

            JsonNode purchaseUnits = root.path("purchase_units");
            JsonNode firstUnit = purchaseUnits.isArray() && purchaseUnits.size() > 0
                    ? purchaseUnits.get(0)
                    : null;

            String captureId = null;
            if (firstUnit != null) {
                JsonNode captures = firstUnit.path("payments").path("captures");
                if (captures.isArray() && captures.size() > 0) {
                    captureId = captures.get(0).path("id").asText();
                }
            }

            String payerEmail = root.path("payer").path("email_address").asText(null);

            return new CaptureOrderResult(status, captureId, payerEmail);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PayPal capture response", e);
        }
    }
}
