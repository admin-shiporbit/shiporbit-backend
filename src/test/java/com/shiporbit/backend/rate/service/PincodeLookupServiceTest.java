package com.shiporbit.backend.rate.service;

import com.shiporbit.backend.rate.entity.PartnerPincodeEntity;
import com.shiporbit.backend.rate.entity.PartnerPincodeId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PincodeLookupServiceTest {

    private static PartnerPincodeEntity row(int partnerId, String city, int stateId) {
        PartnerPincodeEntity e = new PartnerPincodeEntity();
        e.setId(new PartnerPincodeId(partnerId, "400601"));
        e.setCityCode(city);
        e.setStateId(stateId);
        return e;
    }

    @Test
    void locationIsTheOneMostPartnersAgreeOn() {
        // Real data shape (400601): Amazon, Ekart, XpressBees say THANE, Pikndel says MUMBAI.
        List<PartnerPincodeEntity> rows = List.of(row(4, "THANE", 14), row(10, "THANE", 14), row(11, "MUMBAI", 14), row(14, "THANE", 14));
        assertEquals("THANE", PincodeLookupService.mostCommonLocation(rows).getCityCode());
    }

    @Test
    void tieGoesToLowestPartnerId() {
        List<PartnerPincodeEntity> rows = List.of(row(4, "LEH_LADAKH", 34), row(9, "LEH", 34));
        assertEquals("LEH_LADAKH", PincodeLookupService.mostCommonLocation(rows).getCityCode());
    }

    @Test
    void cityCodeBecomesADisplayName() {
        assertEquals("Delhi NCR", PincodeLookupService.districtName("DELHI_NCR"));
        assertEquals("24 Paraganas North", PincodeLookupService.districtName("24_PARAGANAS_NORTH"));
        assertEquals("Pune", PincodeLookupService.districtName("PUNE"));
        assertNull(PincodeLookupService.districtName(" "));
    }
}
