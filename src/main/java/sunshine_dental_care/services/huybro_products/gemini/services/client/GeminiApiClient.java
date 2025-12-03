package sunshine_dental_care.services.huybro_products.gemini.services.client;

public interface GeminiApiClient {

    String callModeration(String requestJson);

    String callVision(String requestJson);
}
