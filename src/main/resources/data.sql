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
