-- =====================================================================
-- V12: Zone rules for partners priced from our own rate cards:
--   Amazon Shipping (4), TCI B2C (9), Ekart (10), Pikndel (11), XpressBees B2B (14).
-- Delhivery (1) and Shree Maruti (13) are priced by their live APIs: no rules.
-- Parked until partners send definitions/pincode lists: DTDC, Shadowfax,
-- Rivigo, Bluedart, India Post, Safexpress.
--
-- SO_ZONE_MAP sample rows (V8) are NOT removed: they still override these rules
-- for the 10 test pincodes, so existing sample quotes are unchanged. Remove a
-- partner's SO_ZONE_MAP rows when its real (CONTRACT) rate card is loaded.
--
-- State rows use a scalar sub-select on STATE_CODE: a typo gives NULL, which
-- fails SO_CK_ZONE_GROUP_MATCH, so the migration stops instead of silently
-- dropping the row (a JOIN would drop it without an error).
-- =====================================================================


-- ---------------------------------------------------------------------
-- Amazon Shipping (4), ZONE_SLAB
-- Source: ZONE MAPPING.docx (Amazon).
-- LOCAL = same state. METRO: Delhi and Chandigarh by state, the other 4 by city code.
-- Noida/Gurugram (CITY_CODE DELHI_NCR, not state DL) are NOT metro: the docx says Delhi only.
-- Open with Amazon: LOCAL vs REMOTE inside a remote state, REGIONAL vs METRO (Chennai<->Bengaluru),
-- Chandigarh in no region, Andaman & Nicobar / Lakshadweep unmapped (fall to NATIONAL).
-- ---------------------------------------------------------------------
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, STATE_ID, GROUP_VALUE)
SELECT 4, 'ZONE_SLAB', v.GROUP_NAME, (SELECT s.STATE_ID FROM SO_STATES s WHERE s.STATE_CODE = v.STATE_CODE), v.GROUP_VALUE
FROM (VALUES ('REGION', 'DL', 'NORTH'),
             ('REGION', 'HR', 'NORTH'),
             ('REGION', 'RJ', 'NORTH'),
             ('REGION', 'UP', 'NORTH'),
             ('REGION', 'PB', 'NORTH'),
             ('REGION', 'UK', 'NORTH'),
             ('REGION', 'HP', 'NORTH'),
             ('REGION', 'GJ', 'WEST'),
             ('REGION', 'MH', 'WEST'),
             ('REGION', 'GA', 'WEST'),
             ('REGION', 'WB', 'EAST'),
             ('REGION', 'OD', 'EAST'),
             ('REGION', 'BR', 'EAST'),
             ('REGION', 'JH', 'EAST'),
             ('REGION', 'CG', 'EAST'),
             ('REGION', 'AP', 'SOUTH'),
             ('REGION', 'TS', 'SOUTH'),
             ('REGION', 'KA', 'SOUTH'),
             ('REGION', 'TN', 'SOUTH'),
             ('REGION', 'MP', 'CENTRAL'),
             ('REMOTE', 'AR', 'Y'),
             ('REMOTE', 'AS', 'Y'),
             ('REMOTE', 'MN', 'Y'),
             ('REMOTE', 'ML', 'Y'),
             ('REMOTE', 'MZ', 'Y'),
             ('REMOTE', 'NL', 'Y'),
             ('REMOTE', 'SK', 'Y'),
             ('REMOTE', 'TR', 'Y'),
             ('REMOTE', 'KL', 'Y'),
             ('REMOTE', 'LA', 'Y'),
             ('REMOTE', 'DD', 'Y'),
             ('REMOTE', 'JK', 'Y'),
             ('REMOTE', 'PY', 'Y'),
             ('METRO', 'DL', 'Y'),
             ('METRO', 'CH', 'Y')) AS v(GROUP_NAME, STATE_CODE, GROUP_VALUE);
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, CITY_CODE, GROUP_VALUE)
VALUES (4, 'ZONE_SLAB', 'METRO', 'MUMBAI', 'Y'),
       (4, 'ZONE_SLAB', 'METRO', 'BENGALURU', 'Y'),
       (4, 'ZONE_SLAB', 'METRO', 'CHENNAI', 'Y'),
       (4, 'ZONE_SLAB', 'METRO', 'KOLKATA', 'Y');
INSERT INTO SO_ZONE_RULE (PARTNER_ID, PRICING_MODEL, SEQ, MATCH_CONDITION, GROUP_NAME, LOCATION_KEY)
VALUES (4, 'ZONE_SLAB', 10, 'SAME_STATE', NULL, 'LOCAL'),
       (4, 'ZONE_SLAB', 20, 'EITHER_IN', 'REMOTE', 'REMOTE'),
       (4, 'ZONE_SLAB', 30, 'SAME_GROUP', 'REGION', 'REGIONAL'),
       (4, 'ZONE_SLAB', 40, 'BOTH_IN', 'METRO', 'METRO'),
       (4, 'ZONE_SLAB', 50, 'ANY', NULL, 'NATIONAL');

-- ---------------------------------------------------------------------
-- TCI Express B2C (9), ZONE_SLAB
-- Source: TCI_HSECOMRate_B2CM.xlsx, sheet B2C Zone Matrix. Zones A-F.
-- E: the sheet's state list names Assam, Meghalaya, Nagaland, Sikkim, Tripura, J&K, Kerala; the heading says
-- 'North East', so all 8 NE states are included. Ladakh is not listed -> not special. Confirm with TCI.
-- TCI's sample service in V8 is B2B_FREIGHT; these rules apply once a ZONE_SLAB (B2C) service is added.
-- ---------------------------------------------------------------------
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, STATE_ID, GROUP_VALUE)
SELECT 9, 'ZONE_SLAB', v.GROUP_NAME, (SELECT s.STATE_ID FROM SO_STATES s WHERE s.STATE_CODE = v.STATE_CODE), v.GROUP_VALUE
FROM (VALUES ('PORT_BLAIR', 'AN', 'Y'),
             ('SPECIAL', 'AR', 'Y'),
             ('SPECIAL', 'AS', 'Y'),
             ('SPECIAL', 'MN', 'Y'),
             ('SPECIAL', 'ML', 'Y'),
             ('SPECIAL', 'MZ', 'Y'),
             ('SPECIAL', 'NL', 'Y'),
             ('SPECIAL', 'SK', 'Y'),
             ('SPECIAL', 'TR', 'Y'),
             ('SPECIAL', 'JK', 'Y'),
             ('SPECIAL', 'KL', 'Y')) AS v(GROUP_NAME, STATE_CODE, GROUP_VALUE);
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, CITY_CODE, GROUP_VALUE)
VALUES (9, 'ZONE_SLAB', 'INTRA_CITY', 'DELHI_NCR', 'DELHI_NCR'),
       (9, 'ZONE_SLAB', 'INTRA_CITY', 'KOLKATA', 'KOLKATA'),
       (9, 'ZONE_SLAB', 'INTRA_CITY', 'BENGALURU', 'BENGALURU'),
       (9, 'ZONE_SLAB', 'METRO', 'DELHI_NCR', 'Y'),
       (9, 'ZONE_SLAB', 'METRO', 'MUMBAI', 'Y'),
       (9, 'ZONE_SLAB', 'METRO', 'BENGALURU', 'Y'),
       (9, 'ZONE_SLAB', 'METRO', 'CHENNAI', 'Y'),
       (9, 'ZONE_SLAB', 'METRO', 'KOLKATA', 'Y');
INSERT INTO SO_ZONE_RULE (PARTNER_ID, PRICING_MODEL, SEQ, MATCH_CONDITION, GROUP_NAME, LOCATION_KEY)
VALUES (9, 'ZONE_SLAB', 10, 'DEST_IN', 'PORT_BLAIR', 'F'),
       (9, 'ZONE_SLAB', 20, 'DEST_IN', 'SPECIAL', 'E'),
       (9, 'ZONE_SLAB', 30, 'SAME_GROUP', 'INTRA_CITY', 'A'),
       (9, 'ZONE_SLAB', 40, 'SAME_STATE', NULL, 'B'),
       (9, 'ZONE_SLAB', 50, 'BOTH_IN', 'METRO', 'C'),
       (9, 'ZONE_SLAB', 60, 'ANY', NULL, 'D');

-- ---------------------------------------------------------------------
-- Ekart (10), ZONE_SLAB
-- Source: Ekart rates.docx - flat Rs 90 up to 2 kg anywhere in India + Rs 35/kg. One zone: ALL.
-- ---------------------------------------------------------------------
INSERT INTO SO_ZONE_RULE (PARTNER_ID, PRICING_MODEL, SEQ, MATCH_CONDITION, GROUP_NAME, LOCATION_KEY)
VALUES (10, 'ZONE_SLAB', 10, 'ANY', NULL, 'ALL');

-- ---------------------------------------------------------------------
-- Pikndel NDD (11), ZONE_SLAB
-- Source: NDD Serviceability File (September).xlsx, sheet Lanes. Only these lanes exist;
-- any other city pair has no zone (not serviceable). Pikndel's 'Delhi/NCR' must import as CITY_CODE DELHI_NCR.
-- Pikndel's sample service in V8 is HYPERLOCAL; these rules apply once a ZONE_SLAB (NDD) service is added.
-- ---------------------------------------------------------------------
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, CITY_CODE, GROUP_VALUE)
VALUES (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'DELHI_NCR'),
       (11, 'ZONE_SLAB', 'CITY', 'MUMBAI', 'MUMBAI'),
       (11, 'ZONE_SLAB', 'CITY', 'BENGALURU', 'BENGALURU'),
       (11, 'ZONE_SLAB', 'CITY', 'MEERUT', 'MEERUT'),
       (11, 'ZONE_SLAB', 'CITY', 'KANPUR', 'KANPUR'),
       (11, 'ZONE_SLAB', 'CITY', 'DEHRADUN', 'DEHRADUN'),
       (11, 'ZONE_SLAB', 'CITY', 'AGRA', 'AGRA'),
       (11, 'ZONE_SLAB', 'CITY', 'JAIPUR', 'JAIPUR'),
       (11, 'ZONE_SLAB', 'CITY', 'AMRITSAR', 'AMRITSAR'),
       (11, 'ZONE_SLAB', 'CITY', 'LUCKNOW', 'LUCKNOW');
INSERT INTO SO_ZONE_RULE (PARTNER_ID, PRICING_MODEL, SEQ, MATCH_CONDITION, GROUP_NAME, LOCATION_KEY)
VALUES (11, 'ZONE_SLAB', 10, 'PAIR', 'CITY', NULL);
INSERT INTO SO_ZONE_PAIR (PARTNER_ID, PRICING_MODEL, GROUP_NAME, ORIGIN_VALUE, DESTINATION_VALUE, LOCATION_KEY)
VALUES (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'DELHI_NCR', 'A'),
       (11, 'ZONE_SLAB', 'CITY', 'MUMBAI', 'MUMBAI', 'A'),
       (11, 'ZONE_SLAB', 'CITY', 'BENGALURU', 'BENGALURU', 'A'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'MUMBAI', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'BENGALURU', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'MUMBAI', 'DELHI_NCR', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'MUMBAI', 'BENGALURU', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'BENGALURU', 'DELHI_NCR', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'BENGALURU', 'MUMBAI', 'C'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'MEERUT', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'KANPUR', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'DEHRADUN', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'AGRA', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'JAIPUR', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'AMRITSAR', 'B'),
       (11, 'ZONE_SLAB', 'CITY', 'DELHI_NCR', 'LUCKNOW', 'B');

-- ---------------------------------------------------------------------
-- XpressBees B2B (14), B2B_FREIGHT
-- Source: II Rate Quote.xlsx + Commercial Proposal (Territory Definition). 15 regions; city rows beat state rows.
-- Not covered (no zone): Ladakh, Andaman & Nicobar, Lakshadweep, Puducherry, Dadra & Nagar Haveli and Daman & Diu.
-- Key = ORIGIN-DESTINATION region, e.g. N1-S1 (same style as the existing B2B keys).
-- XpressBees' sample services in V8 are ZONE_SLAB; these rules apply once the B2B_FREIGHT service is added.
-- ---------------------------------------------------------------------
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, STATE_ID, GROUP_VALUE)
SELECT 14, 'B2B_FREIGHT', v.GROUP_NAME, (SELECT s.STATE_ID FROM SO_STATES s WHERE s.STATE_CODE = v.STATE_CODE), v.GROUP_VALUE
FROM (VALUES ('REGION', 'DL', 'N1'),
             ('REGION', 'MH', 'W2'),
             ('REGION', 'GJ', 'W2'),
             ('REGION', 'GA', 'W2'),
             ('REGION', 'MP', 'C2'),
             ('REGION', 'TN', 'S2'),
             ('REGION', 'KA', 'S2'),
             ('REGION', 'TS', 'S2'),
             ('REGION', 'AP', 'S2'),
             ('REGION', 'KL', 'S3'),
             ('REGION', 'UP', 'N2'),
             ('REGION', 'RJ', 'N2'),
             ('REGION', 'HR', 'N2'),
             ('REGION', 'CH', 'N2'),
             ('REGION', 'PB', 'N2'),
             ('REGION', 'UK', 'N3'),
             ('REGION', 'HP', 'N3'),
             ('REGION', 'JK', 'N4'),
             ('REGION', 'WB', 'E2'),
             ('REGION', 'BR', 'E2'),
             ('REGION', 'OD', 'E2'),
             ('REGION', 'JH', 'E2'),
             ('REGION', 'CG', 'E2'),
             ('REGION', 'AR', 'NE2'),
             ('REGION', 'AS', 'NE2'),
             ('REGION', 'MN', 'NE2'),
             ('REGION', 'ML', 'NE2'),
             ('REGION', 'MZ', 'NE2'),
             ('REGION', 'NL', 'NE2'),
             ('REGION', 'SK', 'NE2'),
             ('REGION', 'TR', 'NE2')) AS v(GROUP_NAME, STATE_CODE, GROUP_VALUE);
INSERT INTO SO_ZONE_GROUP (PARTNER_ID, PRICING_MODEL, GROUP_NAME, CITY_CODE, GROUP_VALUE)
VALUES (14, 'B2B_FREIGHT', 'REGION', 'MUMBAI', 'W1'),
       (14, 'B2B_FREIGHT', 'REGION', 'BHIWANDI', 'W1'),
       (14, 'B2B_FREIGHT', 'REGION', 'PUNE', 'W1'),
       (14, 'B2B_FREIGHT', 'REGION', 'AHMEDABAD', 'W1'),
       (14, 'B2B_FREIGHT', 'REGION', 'INDORE', 'C1'),
       (14, 'B2B_FREIGHT', 'REGION', 'BHOPAL', 'C1'),
       (14, 'B2B_FREIGHT', 'REGION', 'BENGALURU', 'S1'),
       (14, 'B2B_FREIGHT', 'REGION', 'CHENNAI', 'S1'),
       (14, 'B2B_FREIGHT', 'REGION', 'HYDERABAD', 'S1'),
       (14, 'B2B_FREIGHT', 'REGION', 'SECUNDERABAD', 'S1'),
       (14, 'B2B_FREIGHT', 'REGION', 'DELHI_NCR', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'GURUGRAM', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'NOIDA', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'FARIDABAD', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'GHAZIABAD', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'SONIPAT', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'JAIPUR', 'N1'),
       (14, 'B2B_FREIGHT', 'REGION', 'KOLKATA', 'E1'),
       (14, 'B2B_FREIGHT', 'REGION', 'GUWAHATI', 'NE1');
INSERT INTO SO_ZONE_RULE (PARTNER_ID, PRICING_MODEL, SEQ, MATCH_CONDITION, GROUP_NAME, LOCATION_KEY)
VALUES (14, 'B2B_FREIGHT', 10, 'PAIR', 'REGION', NULL);
-- Every region pair (15 x 15 = 225 rows), key ORIGIN-DESTINATION.
INSERT INTO SO_ZONE_PAIR (PARTNER_ID, PRICING_MODEL, GROUP_NAME, ORIGIN_VALUE, DESTINATION_VALUE, LOCATION_KEY)
SELECT 14, 'B2B_FREIGHT', 'REGION', o.REGION, d.REGION, o.REGION || '-' || d.REGION
FROM (VALUES ('W1'), ('W2'), ('C1'), ('C2'), ('S1'), ('S2'), ('S3'), ('N1'), ('N2'), ('N3'), ('N4'), ('E1'), ('E2'), ('NE1'), ('NE2')) AS o(REGION)
         CROSS JOIN (VALUES ('W1'), ('W2'), ('C1'), ('C2'), ('S1'), ('S2'), ('S3'), ('N1'), ('N2'), ('N3'), ('N4'), ('E1'), ('E2'), ('NE1'), ('NE2')) AS d(REGION);
