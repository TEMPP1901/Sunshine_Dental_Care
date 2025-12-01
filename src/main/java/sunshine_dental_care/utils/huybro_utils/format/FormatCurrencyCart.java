package sunshine_dental_care.utils.huybro_utils.format;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sunshine_dental_care.services.huybro_cart.impl.CurrencyRateInternalService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class FormatCurrencyCart {

    private final CurrencyRateInternalService rateService;

    public FormatCurrencyCart(CurrencyRateInternalService rateService) {
        this.rateService = rateService;
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        log.info("[FormatCurrencyCart] → START convert: amount={}, from={}, to={}", amount, from, to);

        if (amount == null) {
            log.warn("[FormatCurrencyCart] → amount is null, return null");
            return null;
        }

        if (from == null || to == null) {
            log.warn("[FormatCurrencyCart] → from/to is null, return original amount");
            return amount;
        }

        if (from.equalsIgnoreCase(to)) {
            log.info("[FormatCurrencyCart] → Same currency, return original amount");
            return amount;
        }

        BigDecimal rate = rateService.getRate(from, to);
        return convert(amount, rate, from, to);
    }

    public BigDecimal convert(BigDecimal amount, BigDecimal rate, String from, String to) {
        log.info("[FormatCurrencyCart] → START convert with given rate: amount={}, rate={}, from={}, to={}",
                amount, rate, from, to);

        if (amount == null || rate == null) {
            log.warn("[FormatCurrencyCart] → amount or rate is null, return original amount");
            return amount;
        }

        BigDecimal result = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        log.info("[FormatCurrencyCart] → Converted (no API call): result={}", result);

        return result;
    }

    public BigDecimal getRate(String from, String to) {
        log.info("[FormatCurrencyCart] → getRate: from={}, to={}", from, to);
        return rateService.getRate(from, to);
    }
}
