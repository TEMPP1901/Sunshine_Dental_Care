package sunshine_dental_care.api.huybro_checkout;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongAutocompleteResponse;
import sunshine_dental_care.services.huybro_checkout.goong.dto.GoongGeocodeResponse;
import sunshine_dental_care.services.huybro_checkout.goong.services.IGoongMapService;

@RestController
@RequestMapping("/api/checkout/goongmap")
@RequiredArgsConstructor
// @CrossOrigin("*") // Mở comment dòng này nếu FE chạy port khác (ví dụ 3000) mà bị chặn CORS
public class GoongController {

    private final IGoongMapService goongMapService;

    // API 1: Lấy danh sách gợi ý khi gõ phím
    // URL: GET /api/v1/goong/autocomplete?keyword=115
    @GetMapping("/autocomplete")
    public ResponseEntity<GoongAutocompleteResponse> autocomplete(
            @RequestParam String keyword,
            @RequestParam(required = false) Double lat, // Thêm
            @RequestParam(required = false) Double lng  // Thêm
    ) {
        // Truyền đủ 3 tham số vào Service
        return ResponseEntity.ok(goongMapService.getAddressSuggestions(keyword, lat, lng));
    }

    // API 2: Lấy địa chỉ thật từ tọa độ (Khi bấm "Cho phép vị trí")
    // URL: GET /api/v1/goong/geocode?lat=10.123&lng=106.456
    @GetMapping("/geocode")
    public ResponseEntity<GoongGeocodeResponse> geocode(
            @RequestParam Double lat,
            @RequestParam Double lng
    ) {
        GoongGeocodeResponse response = goongMapService.getAddressByCoordinates(lat, lng);
        return ResponseEntity.ok(response);
    }
}