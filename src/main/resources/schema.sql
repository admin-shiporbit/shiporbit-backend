CREATE TABLE SO_ROLE
(
    ID   INT PRIMARY KEY,
    ROLE VARCHAR(15) NOT NULL UNIQUE
);
-- STATE TABLE
CREATE TABLE SO_STATES
(
    STATE_ID    INT PRIMARY KEY,
    STATE_NAME  VARCHAR(100) NOT NULL,
    STATE_CODE  VARCHAR(10)  NOT NULL UNIQUE,
    REGION_TYPE VARCHAR(20)  NOT NULL,
    COUNTRY     VARCHAR(50) DEFAULT 'India'
);

-- USER TABLE
CREATE TABLE SO_USERS
(
    ID            UUID PRIMARY KEY,
    EMAIL         VARCHAR(140) NOT NULL,
    FULL_NAME     VARCHAR(120) NOT NULL,
    PASSWORD_HASH VARCHAR(256) NOT NULL,
    ROLE_ID       INT          NOT NULL,
    ENABLED       BOOLEAN   DEFAULT TRUE,
    CREATED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT    TIMESTAMP,
    CONSTRAINT SO_UQ_USERS_EMAIL UNIQUE (EMAIL),
    CONSTRAINT SO_FK_USERS_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES SO_ROLE (ID)
);

--CREATE SO_PICKUP
CREATE TABLE SO_PICKUP_ADDRESS
(
    ID            UUID PRIMARY KEY,
    LABEL         VARCHAR(48)  NOT NULL,
    USER_ID       UUID         NOT NULL,
    ISD_CODE      VARCHAR(3)   NOT NULL,
    PHONE         VARCHAR(10)  NOT NULL,
    ADDRESS_LINE1 VARCHAR(256),
    ADDRESS_LINE2 VARCHAR(256),
    CITY          VARCHAR(200) NOT NULL,
    STATE_ID      INT          NOT NULL,
    PIN_CODE      VARCHAR(6)   NOT NULL,
    IS_DEFAULT    BOOLEAN               DEFAULT FALSE,
    CREATED_AT    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_PICKUP_USER_ID FOREIGN KEY (USER_ID) REFERENCES SO_USERS (ID),
    CONSTRAINT FK_STATE_ID FOREIGN KEY (STATE_ID) REFERENCES SO_STATES (STATE_ID)
);

-- CREATE INDEX FOR PICK_UP ADDRESS

CREATE INDEX SO_IDX_PICKUP_STATE_ID ON SO_PICKUP_ADDRESS (STATE_ID);

-- DELHIVERY RATE CARD CONFIG

CREATE TABLE rate_card_config_delhivery
(
    id                        BIGSERIAL PRIMARY KEY,
    partner_code              VARCHAR(50)    NOT NULL,
    effective_from            DATE           NOT NULL,
    effective_to              DATE,                    -- null = currently active

    volumetric_divisor        NUMERIC(10, 2) NOT NULL,
    min_chargeable_weight_kg  NUMERIC(10, 2) NOT NULL,
    min_lr_charge             NUMERIC(10, 2) NOT NULL,

    fsc_percent               NUMERIC(5, 2)  NOT NULL,
    processing_charge_per_lr  NUMERIC(10, 2) NOT NULL,

    rov_owner_percent         NUMERIC(5, 2)  NOT NULL,
    rov_owner_min_per_lr      NUMERIC(10, 2) NOT NULL,
    rov_carrier_percent       NUMERIC(5, 2)  NOT NULL,
    rov_carrier_min_per_lr    NUMERIC(10, 2) NOT NULL,

    oda_mode                  SMALLINT       NOT NULL, -- 1/2/3

    floor_delivery_per_kg     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    floor_delivery_min_per_lr NUMERIC(10, 2) NOT NULL DEFAULT 0,
    mall_delivery_per_kg      NUMERIC(10, 2) NOT NULL DEFAULT 0,
    mall_delivery_min_per_lr  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    csd_army_per_kg           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    csd_army_min_per_lr       NUMERIC(10, 2) NOT NULL DEFAULT 0,
    sunday_holiday_per_lr     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    to_pay_per_lr             NUMERIC(10, 2) NOT NULL DEFAULT 0,
    cheque_handling_per_lr    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    cash_handling_percent     NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    cash_handling_min_per_lr  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    green_tax_per_kg          NUMERIC(10, 2) NOT NULL DEFAULT 0,
    green_tax_min_per_lr      NUMERIC(10, 2) NOT NULL DEFAULT 0,

    round_off_total           BOOLEAN        NOT NULL DEFAULT TRUE,

    created_at                TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP      NOT NULL DEFAULT now(),

    UNIQUE (partner_code, effective_from)
);

-- TABLE FOR RATE CHARGE

CREATE TABLE rate_charge_slab_delhivery
(
    id             BIGSERIAL PRIMARY KEY,
    partner_code   VARCHAR(50)    NOT NULL,
    effective_from DATE           NOT NULL,
    effective_to   DATE,
    slab_type      VARCHAR(20)    NOT NULL, -- 'HANDLING' or 'ODA'
    min_kg         NUMERIC(10, 2) NOT NULL,
    max_kg         NUMERIC(10, 2),          -- null = unbounded
    rate_per_kg    NUMERIC(10, 2) NOT NULL,
    min_charge     NUMERIC(10, 2)           -- used by ODA, null for handling
);

-- ODA EXEMPT PINCODE

CREATE TABLE rate_oda_exempt_pincode_delhivery
(
    id           BIGSERIAL PRIMARY KEY,
    partner_code VARCHAR(50) NOT NULL,
    pincode      VARCHAR(10) NOT NULL,
    exempt_type  VARCHAR(10) NOT NULL, -- 'PICKUP' or 'DELIVERY'
    UNIQUE (partner_code, pincode, exempt_type)
);