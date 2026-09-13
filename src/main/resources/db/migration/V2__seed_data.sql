-- INSERT ROLES FOR USERS
INSERT INTO SO_ROLE
VALUES (1, 'ADMIN');
INSERT INTO SO_ROLE
VALUES (2, 'SELLER');
INSERT INTO SO_ROLE
VALUES (3, 'OPS');
INSERT INTO SO_ROLE
VALUES (4, 'SUPER_ADMIN');

-- INSERT STATE FOR SO_STATES
INSERT INTO SO_STATES (STATE_ID, STATE_NAME, STATE_CODE, REGION_TYPE, COUNTRY)
VALUES (1, 'Andhra Pradesh', 'AP', 'STATE', 'India'),
       (2, 'Arunachal Pradesh', 'AR', 'STATE', 'India'),
       (3, 'Assam', 'AS', 'STATE', 'India'),
       (4, 'Bihar', 'BR', 'STATE', 'India'),
       (5, 'Chhattisgarh', 'CG', 'STATE', 'India'),
       (6, 'Goa', 'GA', 'STATE', 'India'),
       (7, 'Gujarat', 'GJ', 'STATE', 'India'),
       (8, 'Haryana', 'HR', 'STATE', 'India'),
       (9, 'Himachal Pradesh', 'HP', 'STATE', 'India'),
       (10, 'Jharkhand', 'JH', 'STATE', 'India'),
       (11, 'Karnataka', 'KA', 'STATE', 'India'),
       (12, 'Kerala', 'KL', 'STATE', 'India'),
       (13, 'Madhya Pradesh', 'MP', 'STATE', 'India'),
       (14, 'Maharashtra', 'MH', 'STATE', 'India'),
       (15, 'Manipur', 'MN', 'STATE', 'India'),
       (16, 'Meghalaya', 'ML', 'STATE', 'India'),
       (17, 'Mizoram', 'MZ', 'STATE', 'India'),
       (18, 'Nagaland', 'NL', 'STATE', 'India'),
       (19, 'Odisha', 'OD', 'STATE', 'India'),
       (20, 'Punjab', 'PB', 'STATE', 'India'),
       (21, 'Rajasthan', 'RJ', 'STATE', 'India'),
       (22, 'Sikkim', 'SK', 'STATE', 'India'),
       (23, 'Tamil Nadu', 'TN', 'STATE', 'India'),
       (24, 'Telangana', 'TS', 'STATE', 'India'),
       (25, 'Tripura', 'TR', 'STATE', 'India'),
       (26, 'Uttar Pradesh', 'UP', 'STATE', 'India'),
       (27, 'Uttarakhand', 'UK', 'STATE', 'India'),
       (28, 'West Bengal', 'WB', 'STATE', 'India'),
       (29, 'Andaman and Nicobar Islands', 'AN', 'UNION_TERRITORY', 'India'),
       (30, 'Chandigarh', 'CH', 'UNION_TERRITORY', 'India'),
       (31, 'Dadra and Nagar Haveli and Daman and Diu', 'DD', 'UNION_TERRITORY', 'India'),
       (32, 'Delhi', 'DL', 'UNION_TERRITORY', 'India'),
       (33, 'Jammu and Kashmir', 'JK', 'UNION_TERRITORY', 'India'),
       (34, 'Ladakh', 'LA', 'UNION_TERRITORY', 'India'),
       (35, 'Lakshadweep', 'LD', 'UNION_TERRITORY', 'India'),
       (36, 'Puducherry', 'PY', 'UNION_TERRITORY', 'India');

-- DEV-ONLY seed user, used by SecurityConfig's DevAuthenticationFilter when
-- app.security.enabled=false (see app.security.dev-user-email in application.yaml).
-- Password is 'DevPassword123!' (bcrypt hash below) in case security is ever re-enabled
-- and someone wants to log in as this account through the normal /api/v1/auth/login flow.
INSERT INTO SO_USERS (ID, EMAIL, FULL_NAME, PASSWORD_HASH, ROLE_ID, ENABLED)
VALUES ('11111111-1111-1111-1111-111111111111',
        'dev@shiporbit.local',
        'Dev User',
        '$2b$10$Zt.4o7NUPp.SiEY6FuZ8negF5/alTMM0VirX5OUY09Bce39/j4eV2',
        1,
        TRUE);

INSERT INTO rate_card_config_delhivery (partner_code, effective_from, effective_to,
                                        volumetric_divisor, min_chargeable_weight_kg, min_lr_charge,
                                        fsc_percent, processing_charge_per_lr,
                                        rov_owner_percent, rov_owner_min_per_lr,
                                        rov_carrier_percent, rov_carrier_min_per_lr,
                                        oda_mode,
                                        floor_delivery_per_kg, floor_delivery_min_per_lr,
                                        mall_delivery_per_kg, mall_delivery_min_per_lr,
                                        csd_army_per_kg, csd_army_min_per_lr,
                                        sunday_holiday_per_lr, to_pay_per_lr, cheque_handling_per_lr,
                                        cash_handling_percent, cash_handling_min_per_lr,
                                        green_tax_per_kg, green_tax_min_per_lr,
                                        round_off_total)
VALUES ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL,
        4500, 20, 350,
        20, 150,
        0.1, 150,
        0.5, 200,
        1, -- ODA Pincode: 1 = destination-pincode based
        0, 0, -- floor_delivery: 0 per kg, 0 min/LR
        4, 750, -- mall_delivery: 4/kg, min 750
        4, 750, -- csd_army: 4/kg, min 750
        0, 100, 300, -- sunday_holiday=0/LR, to_pay=100/LR, cheque_handling=300/LR
        2, 300, -- cash_handling: 2%, min 300/LR
        0.5, 100, -- green_tax: 0.5/kg, min 100
        TRUE -- Round-off = yes
       );

INSERT INTO rate_charge_slab_delhivery (partner_code, effective_from, effective_to, slab_type, min_kg, max_kg,
                                        rate_per_kg,
                                        min_charge)
VALUES ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL, 'HANDLING', 100, 250, 0, NULL),
       ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL, 'HANDLING', 250, 400, 0, NULL),
       ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL, 'HANDLING', 400, NULL, 3, NULL),
       ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL, 'ODA', 0, 500, 4, 750),
       ('DELHIVERY_PTL_CFT6', '2026-01-01', NULL, 'ODA', 500, NULL, 4, 750);

-- Delhivery_partners_list
INSERT INTO SO_DELIVERY_PARTNER_LIST (id, partner_name)
VALUES (1, 'DELHIVERY'),
       (2, 'BLUEDART'),
       (3, 'DTDC');

-- FK_state_id now aligned with your real SO_STATES table.
-- FK_Partner_id assumes 1=DELHIVERY, 2=BLUEDART, 3=DTDC from SO_DELIVERY_PARTNER_LIST.

INSERT INTO SO_DELIVEY_PIN_CODE_LIST
(PinCode, FK_Partner_id, FK_STATE_ID, District, IS_PREPAID, IS_REVERSE_Pickup, IS_RSPL, IS_COD, IS_CASH)
VALUES ('110084', 1, 32, 'North West Delhi', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('110001', 2, 32, 'Central Delhi', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('110085', 3, 32, 'North West Delhi', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('500001', 1, 24, 'Hyderabad', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('500081', 2, 24, 'Hyderabad', TRUE, TRUE, TRUE, TRUE, TRUE),
       ('500034', 3, 24, 'Hyderabad', TRUE, FALSE, FALSE, FALSE, FALSE),
       ('400001', 1, 14, 'Mumbai City', TRUE, TRUE, TRUE, TRUE, TRUE),
       ('400070', 2, 14, 'Mumbai Suburban', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('411001', 3, 14, 'Pune', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('411014', 1, 14, 'Pune', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('560001', 1, 11, 'Bengaluru Urban', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('560066', 2, 11, 'Bengaluru Urban', TRUE, TRUE, FALSE, TRUE, FALSE),
       ('560034', 3, 11, 'Bengaluru Urban', TRUE, FALSE, FALSE, TRUE, TRUE),
       ('600001', 1, 23, 'Chennai', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('600028', 2, 23, 'Chennai', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('700001', 1, 28, 'Kolkata', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('700091', 3, 28, 'Kolkata', TRUE, FALSE, FALSE, FALSE, TRUE),
       ('380001', 1, 7, 'Ahmedabad', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('380015', 2, 7, 'Ahmedabad', TRUE, TRUE, TRUE, TRUE, FALSE),
       ('395007', 3, 7, 'Surat', TRUE, FALSE, FALSE, TRUE, TRUE),
       ('226001', 1, 26, 'Lucknow', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('208001', 2, 26, 'Kanpur Nagar', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('201301', 1, 26, 'Gautam Buddha Nagar', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('244001', 3, 26, 'Moradabad', TRUE, FALSE, FALSE, FALSE, FALSE),
       ('302001', 1, 21, 'Jaipur', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('313001', 2, 21, 'Udaipur', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('141001', 1, 20, 'Ludhiana', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('160017', 3, 30, 'Chandigarh', TRUE, TRUE, TRUE, TRUE, TRUE),
       ('682001', 1, 12, 'Ernakulam', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('695001', 2, 12, 'Thiruvananthapuram', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('751001', 1, 19, 'Khordha', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('800001', 3, 4, 'Patna', TRUE, FALSE, FALSE, FALSE, FALSE),
       ('834001', 2, 10, 'Ranchi', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('462001', 1, 13, 'Bhopal', TRUE, TRUE, FALSE, TRUE, TRUE),
       ('452001', 2, 13, 'Indore', TRUE, FALSE, FALSE, TRUE, FALSE),
       ('781001', 3, 3, 'Kamrup Metropolitan', TRUE, FALSE, FALSE, FALSE, FALSE),
       ('190001', 1, 33, 'Srinagar', FALSE, FALSE, FALSE, FALSE, TRUE),
       ('180001', 2, 33, 'Jammu', TRUE, FALSE, FALSE, TRUE, TRUE),
       ('122001', 1, 8, 'Gurugram', TRUE, TRUE, TRUE, TRUE, TRUE),
       ('121001', 3, 8, 'Faridabad', TRUE, TRUE, FALSE, TRUE, TRUE);