package sunshine_dental_care.services.huybro_checkout.goong.services;

import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongAutocompleteResponse;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongGeocodeResponse;

public interface IGoongMapService {
    GoongAutocompleteResponse getAddressSuggestions(String keyword, Double lat, Double lng);
    GoongGeocodeResponse getAddressByCoordinates(Double lat, Double lng);
}