package com.shiporbit.backend.rate.dto.response;

import java.util.List;

/**
 * GET /api/v1/pincodes/{pincode}: where the pincode is and which partners cover it, from ShipOrbit's own
 * pincode list (SO_PARTNER_PINCODE + SO_STATES). No partner API is called.
 *
 * @param district        district / city cluster from CITY_CODE, e.g. "Pune", "Delhi NCR"
 * @param serviceable     true when at least one enabled pincode-list partner picks up or delivers here
 * @param partners        enabled pincode-list partners that have this pincode (flags per partner)
 * @param checkedPerRoute enabled live-API partners (Delhivery, Shree Maruti): serviceability is checked
 *                        per origin -> destination when rates are fetched, so it is not known here
 */
public record PincodeLookupResponse(String pincode, String district, String state, String stateCode,
                                    boolean serviceable, List<PartnerCoverage> partners, List<String> checkedPerRoute) {

    public record PartnerCoverage(String name, boolean pickup, boolean delivery, boolean cod, boolean prepaid, boolean oda) { }
}
