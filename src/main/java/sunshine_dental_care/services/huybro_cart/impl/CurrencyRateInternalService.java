package sunshine_dental_care.services.huybro_cart.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class CurrencyRateInternalService {

    @Value("${exchange.rate.api.url}")
    private String apiUrl;

    @Value("${exchange.rate.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public BigDecimal getRate(String from, String to) {

        log.info("[CurrencyRateInternalService] → START: from={}, to={}", from, to);

        String url = apiUrl
                + "?access_key=" + apiKey
                + "&from=" + from
                + "&to=" + to
                + "&amount=1";

        log.info("[CurrencyRateInternalService] → Request URL = {}", url);

        Map<String, Object> response;

        try {
            response = restTemplate.getForObject(url, Map.class);
        } catch (Exception ex) {
            log.error("[CurrencyRateInternalService] → ERROR calling API: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to connect to exchange rate API");
        }

        log.info("[CurrencyRateInternalService] → Raw Response = {}", response);

        if (response == null) {
            log.error("[CurrencyRateInternalService] → Null response from API");
            throw new IllegalStateException("Null response from exchange rate API");
        }

        Object successObj = response.get("success");
        if (successObj instanceof Boolean success && !success) {
            Object error = response.get("error");
            log.error("[CurrencyRateInternalService] → API returned error: {}", error);
            throw new IllegalStateException("Exchange rate API error: " + error);
        }

        BigDecimal rate = null;

        if (response.containsKey("info")) {
            Object infoObj = response.get("info");
            if (infoObj instanceof Map<?, ?> infoMap) {
                Object rateObj = infoMap.get("rate");
                if (rateObj != null) {
                    rate = new BigDecimal(rateObj.toString());
                }
            }
        }

        if (rate == null && response.containsKey("result")) {
            Object resultObj = response.get("result");
            if (resultObj != null) {
                rate = new BigDecimal(resultObj.toString());
            }
        }

        if (rate == null) {
            log.error("[CurrencyRateInternalService] → Cannot find rate in response (no info.rate and no result)");
            throw new IllegalStateException("Invalid response from exchange rate API");
        }

        log.info("[CurrencyRateInternalService] → SUCCESS: rate = {}", rate);

        return rate;
    }
}
