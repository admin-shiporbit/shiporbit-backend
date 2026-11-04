-- =====================================================================
-- V11: Zone rules. Works out the slab LOCATION_KEY for a lane from the
-- two pincodes' city/state, instead of one SO_ZONE_MAP row per pincode
-- pair (19,586 pincodes = ~383 million pairs per partner).
-- (V9 is reserved for payment-gateway's wallet migration.)
--
-- Lookup order for a partner + pricing model (RateCardLoader.lanes):
--   1. SO_ZONE_MAP row for the exact origin/destination pincodes (override)
--   2. SO_ZONE_RULE rows in SEQ order; the first match wins
-- No match = ZONE_UNKNOWN for that partner's services of that model.
-- =====================================================================

-- Named groupings of pincodes, e.g. REGION (state UP -> NORTH), METRO (city MUMBAI -> Y).
-- A row matches a pincode on CITY_CODE or on STATE_ID (exactly one is set).
-- CITY_CODE is compared with SO_PARTNER_PINCODE.CITY_CODE of that partner (upper case);
-- a city match wins over a state match.
CREATE TABLE SO_ZONE_GROUP
(
    ID            BIGSERIAL PRIMARY KEY,
    PARTNER_ID    INT         NOT NULL,
    PRICING_MODEL VARCHAR(20) NOT NULL,
    GROUP_NAME    VARCHAR(30) NOT NULL,
    STATE_ID      INT,
    CITY_CODE     VARCHAR(30),
    GROUP_VALUE   VARCHAR(20) NOT NULL,
    CONSTRAINT SO_FK_ZONE_GROUP_PARTNER FOREIGN KEY (PARTNER_ID) REFERENCES SO_DELIVERY_PARTNER_LIST (ID),
    CONSTRAINT SO_FK_ZONE_GROUP_STATE FOREIGN KEY (STATE_ID) REFERENCES SO_STATES (STATE_ID),
    CONSTRAINT SO_CK_ZONE_GROUP_MODEL CHECK (PRICING_MODEL IN ('ZONE_SLAB', 'DISTANCE_BAND', 'B2B_FREIGHT')),
    CONSTRAINT SO_CK_ZONE_GROUP_MATCH CHECK ((STATE_ID IS NULL) <> (CITY_CODE IS NULL)),
    CONSTRAINT SO_CK_ZONE_GROUP_CITY_UPPER CHECK (CITY_CODE IS NULL OR CITY_CODE = UPPER(CITY_CODE))
);
-- One value per city/state within a group.
CREATE UNIQUE INDEX SO_UX_ZONE_GROUP ON SO_ZONE_GROUP
    (PARTNER_ID, PRICING_MODEL, GROUP_NAME, COALESCE(STATE_ID, -1), COALESCE(CITY_CODE, ''));

-- Ordered rules. MATCH_CONDITION (see ZoneCondition.java):
--   ANY, SAME_STATE                       -> no GROUP_NAME
--   SAME_GROUP, BOTH_IN, ORIGIN_IN,
--   DEST_IN, EITHER_IN                    -> GROUP_NAME required
--   PAIR                                  -> GROUP_NAME required, key comes from SO_ZONE_PAIR
CREATE TABLE SO_ZONE_RULE
(
    ID              BIGSERIAL PRIMARY KEY,
    PARTNER_ID      INT         NOT NULL,
    PRICING_MODEL   VARCHAR(20) NOT NULL,
    SEQ             INT         NOT NULL,
    MATCH_CONDITION VARCHAR(15) NOT NULL,
    GROUP_NAME      VARCHAR(30),
    LOCATION_KEY    VARCHAR(20),                            -- must match SO_RATE_SLAB.LOCATION_KEY
    CONSTRAINT SO_FK_ZONE_RULE_PARTNER FOREIGN KEY (PARTNER_ID) REFERENCES SO_DELIVERY_PARTNER_LIST (ID),
    CONSTRAINT SO_UQ_ZONE_RULE UNIQUE (PARTNER_ID, PRICING_MODEL, SEQ),
    CONSTRAINT SO_CK_ZONE_RULE_MODEL CHECK (PRICING_MODEL IN ('ZONE_SLAB', 'DISTANCE_BAND', 'B2B_FREIGHT')),
    CONSTRAINT SO_CK_ZONE_RULE_CONDITION CHECK (MATCH_CONDITION IN ('ANY', 'SAME_STATE', 'SAME_GROUP', 'BOTH_IN',
                                                                    'ORIGIN_IN', 'DEST_IN', 'EITHER_IN', 'PAIR')),
    CONSTRAINT SO_CK_ZONE_RULE_GROUP CHECK ((MATCH_CONDITION IN ('ANY', 'SAME_STATE')) = (GROUP_NAME IS NULL)),
    CONSTRAINT SO_CK_ZONE_RULE_KEY CHECK ((MATCH_CONDITION = 'PAIR') = (LOCATION_KEY IS NULL))
);

-- Zone for (origin value, destination value) of a group, used by PAIR rules
-- (e.g. XpressBees REGION N1 -> S1 = 'N1-S1', Pikndel CITY DELHI_NCR -> MUMBAI = 'C').
CREATE TABLE SO_ZONE_PAIR
(
    ID                BIGSERIAL PRIMARY KEY,
    PARTNER_ID        INT         NOT NULL,
    PRICING_MODEL     VARCHAR(20) NOT NULL,
    GROUP_NAME        VARCHAR(30) NOT NULL,
    ORIGIN_VALUE      VARCHAR(20) NOT NULL,
    DESTINATION_VALUE VARCHAR(20) NOT NULL,
    LOCATION_KEY      VARCHAR(20) NOT NULL,
    CONSTRAINT SO_FK_ZONE_PAIR_PARTNER FOREIGN KEY (PARTNER_ID) REFERENCES SO_DELIVERY_PARTNER_LIST (ID),
    CONSTRAINT SO_UQ_ZONE_PAIR UNIQUE (PARTNER_ID, PRICING_MODEL, GROUP_NAME, ORIGIN_VALUE, DESTINATION_VALUE),
    CONSTRAINT SO_CK_ZONE_PAIR_MODEL CHECK (PRICING_MODEL IN ('ZONE_SLAB', 'DISTANCE_BAND', 'B2B_FREIGHT'))
);
