package com.shiporbit.backend.rate.engine.zone;

import com.shiporbit.backend.rate.engine.PincodeInfo;
import com.shiporbit.backend.rate.engine.zone.PartnerZoneRules.ValuePair;
import com.shiporbit.backend.rate.entity.PricingModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shiporbit.backend.rate.entity.ZoneCondition.ANY;
import static com.shiporbit.backend.rate.entity.ZoneCondition.BOTH_IN;
import static com.shiporbit.backend.rate.entity.ZoneCondition.DEST_IN;
import static com.shiporbit.backend.rate.entity.ZoneCondition.EITHER_IN;
import static com.shiporbit.backend.rate.entity.ZoneCondition.PAIR;
import static com.shiporbit.backend.rate.entity.ZoneCondition.SAME_GROUP;
import static com.shiporbit.backend.rate.entity.ZoneCondition.SAME_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zone rules without Spring or a database: the same rules as V12, on real pincodes.
 * State ids are SO_STATES ids (V2): UP 26, DL 32, MH 14, KA 11, TN 23, KL 12, AS 3, GJ 7, CH 30, AN 29.
 */
class PartnerZoneRulesTest {

    private static final int UP = 26, DL = 32, MH = 14, KA = 11, TN = 23, KL = 12, AS = 3, GJ = 7, CH = 30, AN = 29;

    private static final PincodeInfo NOIDA = pin("201301", "DELHI_NCR", UP);
    private static final PincodeInfo LUCKNOW = pin("226001", "LUCKNOW", UP);
    private static final PincodeInfo DELHI = pin("110001", "DELHI_NCR", DL);
    private static final PincodeInfo MUMBAI = pin("400001", "MUMBAI", MH);
    private static final PincodeInfo BENGALURU = pin("560001", "BENGALURU", KA);
    private static final PincodeInfo CHENNAI = pin("600001", "CHENNAI", TN);
    private static final PincodeInfo KOCHI = pin("682001", "ERNAKULAM", KL);
    private static final PincodeInfo TRIVANDRUM = pin("695001", "THIRUVANANTHAPURAM", KL);
    private static final PincodeInfo GUWAHATI = pin("781001", "GUWAHATI", AS);
    private static final PincodeInfo AHMEDABAD = pin("380001", "AHMEDABAD", GJ);
    private static final PincodeInfo CHANDIGARH = pin("160017", "CHANDIGARH", CH);
    private static final PincodeInfo PORT_BLAIR = pin("744101", "PORT_BLAIR", AN);

    /** Amazon Shipping rules from V12. */
    private static final PartnerZoneRules AMAZON = new PartnerZoneRules(
            List.of(new ZoneRule(50, ANY, null, "NATIONAL"),            // out of order on purpose: SEQ decides
                    new ZoneRule(10, SAME_STATE, null, "LOCAL"),
                    new ZoneRule(20, EITHER_IN, "REMOTE", "REMOTE"),
                    new ZoneRule(30, SAME_GROUP, "REGION", "REGIONAL"),
                    new ZoneRule(40, BOTH_IN, "METRO", "METRO")),
            Map.of("REGION", new ZoneGroup("REGION", Map.of(),
                            Map.of(UP, "NORTH", DL, "NORTH", MH, "WEST", GJ, "WEST", KA, "SOUTH", TN, "SOUTH")),
                    "REMOTE", new ZoneGroup("REMOTE", Map.of(), Map.of(KL, "Y", AS, "Y")),
                    "METRO", new ZoneGroup("METRO",
                            Map.of("MUMBAI", "Y", "BENGALURU", "Y", "CHENNAI", "Y", "KOLKATA", "Y"),
                            Map.of(DL, "Y", CH, "Y"))),
            Map.of());

    /** XpressBees-style region pairs: a city row beats its state's row. */
    private static final PartnerZoneRules XPRESSBEES = new PartnerZoneRules(
            List.of(new ZoneRule(10, PAIR, "REGION", null)),
            Map.of("REGION", new ZoneGroup("REGION",
                    Map.of("DELHI_NCR", "N1", "BENGALURU", "S1"),
                    Map.of(UP, "N2", KA, "S2", DL, "N1"))),
            Map.of("REGION", Map.of(
                    new ValuePair("N1", "S1"), "N1-S1",
                    new ValuePair("N2", "S2"), "N2-S2",
                    new ValuePair("N2", "S1"), "N2-S1")));

    @Test
    void amazonZonesFollowTheZoneMappingDoc() {
        assertEquals(Optional.of("LOCAL"), AMAZON.resolve(NOIDA, LUCKNOW));          // same state
        assertEquals(Optional.of("REGIONAL"), AMAZON.resolve(NOIDA, DELHI));         // UP and DL both NORTH
        assertEquals(Optional.of("METRO"), AMAZON.resolve(DELHI, MUMBAI));           // DL by state, Mumbai by city
        assertEquals(Optional.of("NATIONAL"), AMAZON.resolve(NOIDA, MUMBAI));        // Noida is not Delhi
        assertEquals(Optional.of("REMOTE"), AMAZON.resolve(DELHI, KOCHI));
        assertEquals(Optional.of("REMOTE"), AMAZON.resolve(GUWAHATI, DELHI));        // remote origin counts too
        assertEquals(Optional.of("NATIONAL"), AMAZON.resolve(NOIDA, AHMEDABAD));
        assertEquals(Optional.of("METRO"), AMAZON.resolve(DELHI, CHANDIGARH));       // CH in no region
    }

    @Test
    void ruleOrderDecidesOverlaps() {
        assertEquals(Optional.of("LOCAL"), AMAZON.resolve(KOCHI, TRIVANDRUM));       // SAME_STATE (10) before REMOTE (20)
        assertEquals(Optional.of("REGIONAL"), AMAZON.resolve(CHENNAI, BENGALURU));   // REGION (30) before METRO (40)
    }

    @Test
    void cityRowBeatsStateRow() {
        assertEquals(Optional.of("N1-S1"), XPRESSBEES.resolve(NOIDA, BENGALURU));    // Noida: city DELHI_NCR = N1
        assertEquals(Optional.of("N2-S1"), XPRESSBEES.resolve(LUCKNOW, BENGALURU));  // Lucknow: state UP = N2
        assertEquals(Optional.of("N1-S1"), XPRESSBEES.resolve(pin("110002", "OTHER", DL), BENGALURU)); // DL state row
    }

    @Test
    void noPairOrNoGroupValueMeansNoZone() {
        assertEquals(Optional.empty(), XPRESSBEES.resolve(BENGALURU, NOIDA));        // S1 -> N1 not configured
        assertEquals(Optional.empty(), XPRESSBEES.resolve(NOIDA, PORT_BLAIR));       // AN has no region
        assertEquals(Optional.empty(), XPRESSBEES.resolve(pin("999999", null, null), BENGALURU));
    }

    @Test
    void missingCityOrStateNeverThrows() {
        PincodeInfo unknown = pin("999999", null, null);
        assertEquals(Optional.of("NATIONAL"), AMAZON.resolve(unknown, DELHI));
        assertEquals(Optional.of("NATIONAL"), AMAZON.resolve(unknown, unknown));     // null state is not "same state"
    }

    @Test
    void cityCodeMatchIgnoresCase() {
        assertEquals(Optional.of("METRO"), AMAZON.resolve(DELHI, pin("400001", "mumbai", MH)));
    }

    @Test
    void zoneConfigOnlyAnswersForConfiguredPartnerAndModel() {
        ZoneConfig config = new ZoneConfig(Map.of(4, Map.of(PricingModel.ZONE_SLAB, AMAZON)));
        assertEquals(Optional.of("METRO"), config.resolve(4, PricingModel.ZONE_SLAB, DELHI, MUMBAI));
        assertEquals(Optional.empty(), config.resolve(4, PricingModel.B2B_FREIGHT, DELHI, MUMBAI));
        assertEquals(Optional.empty(), config.resolve(1, PricingModel.ZONE_SLAB, DELHI, MUMBAI));
        assertTrue(config.modelsFor(1).isEmpty());
    }

    @Test
    void problemsReportsConfigMistakes() {
        PartnerZoneRules broken = new PartnerZoneRules(
                List.of(new ZoneRule(10, DEST_IN, "SPECIAL", "E"),                    // group has no rows
                        new ZoneRule(20, PAIR, "REGION", null)),
                Map.of("REGION", new ZoneGroup("REGION", Map.of(), Map.of(UP, "N2"))),
                Map.of("REGION", Map.of(new ValuePair("N2", "S9"), "N2-S9")));        // S9: typo
        List<String> problems = broken.problems();
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("'SPECIAL'"));
        assertTrue(problems.get(1).contains("N2 -> S9"));
        assertTrue(AMAZON.problems().isEmpty());
        assertTrue(XPRESSBEES.problems().isEmpty());
    }

    private static PincodeInfo pin(String pincode, String cityCode, Integer stateId) {
        return new PincodeInfo(pincode, cityCode, stateId, true, true, true, true, true, false, false);
    }
}
