package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.core.SlabInfo;
import com.shiporbit.backend.rate.dto.request.CompareRatesRequest;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.Carrier;
import com.shiporbit.backend.rate.dto.response.CompareRatesResponse.Fare;
import com.shiporbit.backend.rate.entity.DataStatus;
import com.shiporbit.backend.rate.entity.PaymentMode;
import com.shiporbit.backend.rate.entity.RateUnit;
import com.shiporbit.backend.rate.entity.ServiceMode;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties;
import com.shiporbit.backend.rate.routing.PartnerProfileProperties.Profile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frontend compare format <-> engine, without Spring. Figures are from a real Amazon quote (V14 card). */
class CompareRatesMapperTest {

    private static CompareRatesRequest request(String weight, String weightUnit, String dimUnit, Boolean cod, String orderValue) {
        return new CompareRatesRequest("Surface", "400001", "560001", weight, weightUnit, "10", "10", "10", dimUnit, cod, orderValue);
    }

    @Test
    void sampleRequestBecomesAQuoteRequest() {
        QuoteRequest q = CompareRatesMapper.toQuoteRequest(request("1", "kg", "cm", true, "1500"));
        assertEquals("400001", q.originPincode());
        assertEquals("560001", q.destinationPincode());
        assertEquals(PaymentMode.COD, q.paymentMode());
        assertEquals(new BigDecimal("1500"), q.codAmount());
        assertEquals(new BigDecimal("1500"), q.declaredValue());
        assertEquals(new BigDecimal("1.000"), q.packages().get(0).deadWeightKg());
        assertEquals(new BigDecimal("10.00"), q.packages().get(0).lengthCm());
    }

    @Test
    void unitsAreConverted() {
        QuoteRequest q = CompareRatesMapper.toQuoteRequest(request("750", "g", "in", null, null));
        assertEquals(new BigDecimal("0.750"), q.packages().get(0).deadWeightKg());
        assertEquals(new BigDecimal("25.40"), q.packages().get(0).lengthCm());
        assertEquals(PaymentMode.PREPAID, q.paymentMode());
        assertEquals(BigDecimal.ZERO, q.codAmount());
    }

    @Test
    void codNeedsAnOrderValueAndWeightMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> CompareRatesMapper.toQuoteRequest(request("1", "kg", "cm", true, "0")));
        assertThrows(IllegalArgumentException.class, () -> CompareRatesMapper.toQuoteRequest(request("0", "kg", "cm", false, null)));
    }

    @Test
    void deliveryModeFiltersServices() {
        assertEquals(Set.of(ServiceMode.SURFACE, ServiceMode.SURFACE_AIR), CompareRatesMapper.modesFor("Surface"));
        assertEquals(Set.of(ServiceMode.AIR, ServiceMode.SURFACE_AIR), CompareRatesMapper.modesFor("express"));
        assertTrue(!CompareRatesMapper.modesFor(null).contains(ServiceMode.HYPERLOCAL));
        assertThrows(IllegalArgumentException.class, () -> CompareRatesMapper.modesFor("Rocket"));
    }

    @Test
    void topLevelWeightsMatchTheSample() {
        var w = CompareRatesMapper.displayWeights(CompareRatesMapper.toQuoteRequest(request("1", "kg", "cm", false, null)));
        assertEquals(new BigDecimal("1"), CompareRatesMapper.plain(w.deadWeightKg()));
        assertEquals(new BigDecimal("0.2"), CompareRatesMapper.plain(w.volumetricWeightKg()));
        assertEquals(new BigDecimal("1"), CompareRatesMapper.plain(w.chargeableWeightKg()));
        assertEquals("100", CompareRatesMapper.plain(new BigDecimal("1E+2")).toPlainString());
    }

    /** Amazon Standard 400001 -> 560001, 1 kg, COD: METRO 60 + COD 30 = 90, margin 9, GST 17.82, total 116.82. */
    private static final QuoteResult.Priced AMAZON = new QuoteResult.Priced(4, "AMAZON_SHIPPING", "STANDARD", ServiceMode.SURFACE,
            DataStatus.CONTRACT, "AMZ-2026-10-08", "METRO", new BigDecimal("0.200"), new BigDecimal("1.000"),
            new SlabInfo(RateUnit.KG, new BigDecimal("1"), new BigDecimal("60.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            List.of(new ChargeLine("BASE", new BigDecimal("60")), new ChargeLine("COD", new BigDecimal("30")),
                    new ChargeLine("MARGIN", new BigDecimal("9")), new ChargeLine("GST", new BigDecimal("17.82"))),
            new BigDecimal("99.00"), new BigDecimal("17.82"), new BigDecimal("116.82"));

    @Test
    void shippingPlusCodEqualsTotal() {
        Fare fare = CompareRatesMapper.fare(AMAZON, BigDecimal.ONE);
        assertEquals(new BigDecimal("38.94"), fare.cod());          // 30 x 116.82 / 90
        assertEquals(new BigDecimal("77.88"), fare.shipping());
        assertEquals(fare.total(), fare.shipping().add(fare.cod()));
        assertEquals(new BigDecimal("1"), fare.billable());
    }

    @Test
    void carrierRatesIncludeMarginButNotGst() {
        Profile profile = new PartnerProfileProperties(Map.of()).forPartner(4, "AMAZON_SHIPPING");
        Carrier carrier = CompareRatesMapper.carrier(AMAZON, profile, null);
        assertEquals("Amazon Shipping", carrier.name());            // derived from the DB name
        assertEquals("AS", carrier.initials());
        assertEquals(new BigDecimal("66.00"), carrier.baseRate()); // 60 x 1.1
        assertEquals(null, carrier.perKg());                       // lookup slab, no per-kg step
        assertEquals(null, carrier.codFlat());                     // no card passed (live-API partner)
    }

    @Test
    void speedSortNeedsEtdEverywhere() {
        assertTrue(CompareRatesMapper.sortOptions(true).get(1).enabled());
        assertEquals("Delivery time (ETD) is not configured for every partner.", CompareRatesMapper.sortOptions(false).get(1).reason());
    }
}
