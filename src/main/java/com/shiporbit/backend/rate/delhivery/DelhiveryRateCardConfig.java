package com.shiporbit.backend.rate.delhivery;

import com.shiporbit.backend.constants.Constants;
import com.shiporbit.backend.rate.RateCardConfig;
import org.springframework.stereotype.Component;

/**
 * Delhivery's values for the "6 CFT Rate Card - Pan India" contract, layered on
 * top of the common {@link RateCardConfig} defaults.
 */
@Component
public class DelhiveryRateCardConfig extends RateCardConfig {

    public DelhiveryRateCardConfig() {
        this.volumetricDivisor = Constants.DELHIVERY_DEVISOR;

        handlingSlabs.add(new double[]{100, 250, 0});
        handlingSlabs.add(new double[]{250, 400, 0});
        handlingSlabs.add(new double[]{400, Double.MAX_VALUE, 3});

        odaSlabs.add(new double[]{0, 500, 4, 750});
        odaSlabs.add(new double[]{500, Double.MAX_VALUE, 4, 750});

        // odaEnabled stays false (inherited default) until we have Delhivery's actual
        // ODA-exempt pincode list to populate deliveryOdaExemptPincodes/pickupOdaExemptPincodes -
        // see RateCardConfig.odaEnabled for why leaving those sets empty with ODA "on"
        // would charge every single shipment instead of none.
    }
}
