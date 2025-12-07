package sunshine_dental_care.services.huybro_checkout.goong.services.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongAutocompleteResponse;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongGeocodeResponse;


@Component
@RequiredArgsConstructor
@Slf4j
public class GoongApiClient {

    private final GoongConfig goongConfig;
    private final RestTemplate restTemplate;

    // API 1: Gợi ý địa chỉ (Autocomplete)
    public GoongAutocompleteResponse autocomplete(String input, String locationStr) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(goongConfig.getBaseUrl() + "/Place/AutoComplete")
                .queryParam("api_key", goongConfig.getApiKey())
                .queryParam("input", input)
                .queryParam("limit", 10); // Lấy nhiều hơn để lọc

        // KEY CHÍNH ĐỂ KHÔN HƠN: Nếu có tọa độ, ưu tiên tìm trong bán kính 50km quanh đó
        if (locationStr != null && !locationStr.isEmpty()) {
            builder.queryParam("location", locationStr);
            builder.queryParam("radius", 50); // Đơn vị: Km (Goong dùng Km cho radius ở endpoint này, check doc kỹ nhé, nếu goong dùng mét thì là 50000)
            // Lưu ý: Goong Places AutoComplete thường dùng 'location' để bias.
        }

        String url = builder.toUriString();

        try {
            return restTemplate.getForObject(url, GoongAutocompleteResponse.class);
        } catch (Exception e) {
            log.error("Error calling Goong Autocomplete: {}", e.getMessage());
            return null;
        }
    }

    // API 2: Lấy địa chỉ từ tọa độ (Reverse Geocoding)
    public GoongGeocodeResponse reverseGeocode(double lat, double lng) {
        String latLng = lat + "," + lng;
        String url = UriComponentsBuilder.fromHttpUrl(goongConfig.getBaseUrl() + "/Geocode")
                .queryParam("api_key", goongConfig.getApiKey())
                .queryParam("latlng", latLng)
                .toUriString();

        try {
            return restTemplate.getForObject(url, GoongGeocodeResponse.class);
        } catch (Exception e) {
            log.error("Error calling Goong Geocode: {}", e.getMessage());
            return null;
        }
    }
}