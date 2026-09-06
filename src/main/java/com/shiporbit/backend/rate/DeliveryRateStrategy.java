package com.shiporbit.backend.rate;

import com.shiporbit.backend.rate.dto.RateBreakdown;

/**
 * Strategy interface - one implementation per delivery partner (Delhivery, and any
 * partner added later). {@link RateCalculator} holds every Spring bean implementing
 * this interface and dispatches to the right one by partner code, so adding a new
 * partner never requires touching RateCalculator or any other partner's code.
 */
public interface DeliveryRateStrategy {

    String getPartnerCode();

    String getPartnerName();

    RateBreakdown calculateRate(ShipmentRequest request);

    default boolean isServiceable(ShipmentRequest request) {
        return true;
    }
}
