-- NOTIFICATION TEMPLATE TABLE
CREATE TABLE SO_NOTIFICATION_TEMPLATE
(
    ID              BIGSERIAL PRIMARY KEY,
    PURPOSE         VARCHAR(40) NOT NULL,
    CHANNEL         VARCHAR(20) NOT NULL,
    SUBJECT         VARCHAR(200),
    BODY            TEXT        NOT NULL,
    DLT_TEMPLATE_ID VARCHAR(50),
    ACTIVE          BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT SO_UQ_NOTIFICATION_TEMPLATE_PURPOSE_CHANNEL UNIQUE (PURPOSE, CHANNEL)
);

-- EMAIL TEMPLATES
INSERT INTO SO_NOTIFICATION_TEMPLATE (PURPOSE, CHANNEL, SUBJECT, BODY)
VALUES ('OTP', 'EMAIL', 'Your ShipOrbit login OTP',
        '<p>Hi {{name}},</p><p>Your login OTP is <b>{{otp}}</b>. It is valid for {{validMinutes}} minutes.</p><p>Do not share it with anyone.</p><p>- Team ShipOrbit</p>'),
       ('SIGN_UP', 'EMAIL', 'Verify your ShipOrbit account',
        '<p>Hi {{name}},</p><p>Your sign-up OTP is <b>{{otp}}</b>. It is valid for {{validMinutes}} minutes.</p><p>- Team ShipOrbit</p>'),
       ('PASSWORD_RESET', 'EMAIL', 'Reset your ShipOrbit password',
        '<p>Hi {{name}},</p><p>Your password reset OTP is <b>{{otp}}</b>. It is valid for {{validMinutes}} minutes.</p><p>If you did not request this, ignore this email.</p><p>- Team ShipOrbit</p>'),
       ('ORDER', 'EMAIL', 'Order {{orderId}} created',
        '<p>Hi {{name}},</p><p>Your order <b>{{orderId}}</b> has been created.</p><p>- Team ShipOrbit</p>'),
       ('BOOKED', 'EMAIL', 'Order {{orderId}} booked',
        '<p>Hi {{name}},</p><p>Your order <b>{{orderId}}</b> has been booked with {{courier}}. AWB: {{awb}}.</p><p>- Team ShipOrbit</p>'),
       ('OUT_FOR_DELIVERY', 'EMAIL', 'Order {{orderId}} is out for delivery',
        '<p>Hi {{name}},</p><p>Your order <b>{{orderId}}</b> is out for delivery today.</p><p>- Team ShipOrbit</p>'),
       ('DELIVERED', 'EMAIL', 'Order {{orderId}} delivered',
        '<p>Hi {{name}},</p><p>Your order <b>{{orderId}}</b> has been delivered.</p><p>- Team ShipOrbit</p>');
