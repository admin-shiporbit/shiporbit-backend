package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decorator used by the old /api/rate/aggregator: adds ShipOrbit's margin to every amount of a partner quote.
 * <p>
 * Multiplying GST by the same factor is correct, not a bug: GST is a fixed % of the taxable amount, so
 * GST on (pre-tax x 1.10) = partner GST x 1.10, and the final amount is the partner's final amount + 10%.
 * This is the same total that /api/v1/rates/quotes produces (MarginTaxPolicy: margin on pre-tax, then GST).
 * <p>
 * The margin % comes from shiporbit.rates.margin-percent (default 10), shared with the new quote API.
 * Every amount is rounded to 2 decimals (HALF_UP); null amounts are treated as 0.
 */
public class MarkupPartnerClient implements DeliveryPartnerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkupPartnerClient.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DeliveryPartnerClient delegate;
    private final BigDecimal multiplier;            // 1 + margin% / 100, e.g. 1.10

    public MarkupPartnerClient(DeliveryPartnerClient delegate, BigDecimal marginPercent) {
        this.delegate = delegate;
        this.multiplier = BigDecimal.ONE.add(marginPercent.divide(HUNDRED));
    }

    public String partnerCode() { return delegate.partnerCode(); }

    public String partnerName() { return delegate.partnerName(); }

    public boolean isServiceable(RequestParamRecord r) { return delegate.isServiceable(r); }

    @Override
    public com.shiporbit.backend.rate.serviceability.Serviceability checkServiceability(RequestParamRecord r) {
        return delegate.checkServiceability(r);
    }

    public RateResponse getRate(RequestParamRecord r) {
        RateResponse rate = delegate.getRate(r);
        RateResponse markedUp = new RateResponse(
                rate.partnerCode(), rate.partnerName(),
                markUp(rate.baseFreight()),
                markUp(rate.fuelHike()),
                markUp(rate.surcharge()),
                markUp(rate.insuranceRov()),
                markUp(rate.odaCharge()),
                markUp(rate.handlingCharges()),
                markUp(rate.gst()),
                markUp(rate.finalFreight())
        );
        LOGGER.debug("Applied {}x markup to {}: {} -> {}", multiplier, rate.partnerCode(), rate.finalFreight(), markedUp.finalFreight());
        return markedUp;
    }

    private BigDecimal markUp(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
