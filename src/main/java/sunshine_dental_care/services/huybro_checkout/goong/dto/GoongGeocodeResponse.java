package sunshine_dental_care.services.huybro_checkout.goong.dto;

import lombok.Data;
import java.util.List;

@Data
public class GoongGeocodeResponse {
    private List<Result> results;
    private String status;

    @Data
    public static class Result {
        private String formatted_address; // Địa chỉ đầy đủ
        private String place_id;
        private Geometry geometry;
    }

    @Data
    public static class Geometry {
        private Location location;
    }

    @Data
    public static class Location {
        private Double lat;
        private Double lng;
    }
}