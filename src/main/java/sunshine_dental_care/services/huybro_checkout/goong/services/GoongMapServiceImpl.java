package sunshine_dental_care.services.huybro_checkout.goong.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongAutocompleteResponse;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongGeocodeResponse;
import sunshine_dental_care.services.huybro_checkout.goong.services.client.GoongApiClient;

@Service
@RequiredArgsConstructor
public class GoongMapServiceImpl implements IGoongMapService {

    private final GoongApiClient goongApiClient;

    @Override
    public GoongAutocompleteResponse getAddressSuggestions(String keyword, Double lat, Double lng) {

        // STRICT BACKEND VALIDATION
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        // LOGIC MỚI: Xử lý location bias
        String locationStr = null;
        if (lat != null && lng != null) {
            // Goong yêu cầu format "lat,lng"
            locationStr = lat + "," + lng;
        }

        // Gọi Client với 2 tham số (đúng với code client bạn vừa sửa)
        GoongAutocompleteResponse response = goongApiClient.autocomplete(keyword.trim(), locationStr);

        // Kiểm tra status từ Goong trả về
        if (response == null || !"OK".equals(response.getStatus())) {
            return new GoongAutocompleteResponse(); // Trả về rỗng để an toàn
        }

        return response;
    }

    @Override
    public GoongGeocodeResponse getAddressByCoordinates(Double lat, Double lng) {
        // STRICT BACKEND VALIDATION
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Coordinates (lat, lng) cannot be null");
        }

        // Validate range
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinates range");
        }

        GoongGeocodeResponse response = goongApiClient.reverseGeocode(lat, lng);

        if (response == null || !"OK".equals(response.getStatus())) {
            return new GoongGeocodeResponse();
        }

        return response;
    }
}