-- Website contact form: stored requests + the two email templates (ContactService).
-- Numbered V15 (not V6) so it does not clash with main's V6-V14 when this branch is merged.

CREATE TABLE SO_CONTACT_REQUEST
(
    ID                UUID PRIMARY KEY,
    NAME              VARCHAR(100)  NOT NULL,
    EMAIL             VARCHAR(150)  NOT NULL,
    COMPANY           VARCHAR(150),
    PHONE             VARCHAR(20),
    TOPIC             VARCHAR(100),
    VOLUME            VARCHAR(50),
    MESSAGE           VARCHAR(5000) NOT NULL,
    WEBSITE           VARCHAR(200),
    STATUS            VARCHAR(20)   NOT NULL,          -- RECEIVED | COMPLETED | PARTIAL | FAILED
    USER_EMAIL_STATUS VARCHAR(20),                     -- SENT | FAILED | SKIPPED
    TEAM_EMAIL_STATUS VARCHAR(20),
    ERROR             VARCHAR(500),
    CREATED_AT        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT        TIMESTAMP
);

CREATE INDEX SO_IDX_CONTACT_REQUEST_EMAIL ON SO_CONTACT_REQUEST (EMAIL);
CREATE INDEX SO_IDX_CONTACT_REQUEST_STATUS_CREATED ON SO_CONTACT_REQUEST (STATUS, CREATED_AT);

-- Placeholders: referenceId, name, email, company, phone, topic, volume, website, message, submittedAt.
-- Values are HTML-escaped by TemplateServiceImpl; optional fields arrive as "-".

-- 1) To the user: confirmation with the details they submitted
INSERT INTO SO_NOTIFICATION_TEMPLATE (PURPOSE, CHANNEL, SUBJECT, BODY)
VALUES ('CONTACT_ACK', 'EMAIL', 'We have received your request - ShipOrbit',
'<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>We have received your request</title></head>
<body style="margin:0; padding:0; background-color:#f4f6fb; font-family:Arial, Helvetica, sans-serif; color:#1f2937;">
  <div style="display:none; max-height:0; overflow:hidden;">Thanks for contacting ShipOrbit. Here is a copy of your request.</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6fb;">
    <tr><td align="center" style="padding:24px 12px;">
      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%; max-width:600px; background-color:#ffffff; border-radius:8px; overflow:hidden;">
        <tr><td align="center" style="background-color:#1e3a8a; padding:28px 24px;">
          <h1 style="margin:0; font-size:28px; line-height:34px; color:#ffffff; letter-spacing:1px;">ShipOrbit</h1>
          <p style="margin:6px 0 0; font-size:14px; color:#c7d2fe;">Ship smarter. Deliver faster.</p>
        </td></tr>
        <tr><td style="padding:32px 32px 8px;">
          <h2 style="margin:0 0 16px; font-size:22px; color:#111827;">Hi {{name}}, thanks for reaching out!</h2>
          <p style="margin:0 0 14px; font-size:15px; line-height:24px;">We have received your request and our team will get back to you shortly, usually within one business day.</p>
          <p style="margin:0 0 20px; font-size:15px; line-height:24px;">Here are the details you shared with us:</p>
        </td></tr>
        <tr><td style="padding:0 32px;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e5e7eb; border-radius:6px; font-size:14px; line-height:20px;">
            <tr><td style="padding:10px 14px; width:35%; color:#6b7280; border-bottom:1px solid #e5e7eb;">Name</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{name}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Email</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{email}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Company</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{company}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Phone</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{phone}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Website</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{website}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Topic</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{topic}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Monthly volume</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{volume}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; vertical-align:top;">Message</td><td style="padding:10px 14px; white-space:pre-wrap;">{{message}}</td></tr>
          </table>
        </td></tr>
        <tr><td style="padding:20px 32px 8px;">
          <p style="margin:0 0 6px; font-size:13px; color:#6b7280;">Reference: <strong>{{referenceId}}</strong></p>
          <p style="margin:0 0 6px; font-size:13px; color:#6b7280;">Submitted: {{submittedAt}}</p>
          <p style="margin:12px 0 0; font-size:13px; line-height:20px; color:#6b7280;">If you did not submit this request, you can ignore this email.</p>
        </td></tr>
        <tr><td align="center" style="padding:24px 32px; background-color:#f9fafb; font-size:12px; color:#9ca3af;">
          &copy; ShipOrbit &middot; <a href="https://www.shiporbit.in" style="color:#6b7280;">www.shiporbit.in</a><br>This is an automated message, please do not reply to this email.
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>');

-- 2) To the ShipOrbit team (notification.contact.team-email): someone has contacted us
INSERT INTO SO_NOTIFICATION_TEMPLATE (PURPOSE, CHANNEL, SUBJECT, BODY)
VALUES ('CONTACT_TEAM', 'EMAIL', 'New contact request: {{name}} ({{topic}})',
'<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>New contact request</title></head>
<body style="margin:0; padding:0; background-color:#f4f6fb; font-family:Arial, Helvetica, sans-serif; color:#1f2937;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6fb;">
    <tr><td align="center" style="padding:24px 12px;">
      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%; max-width:600px; background-color:#ffffff; border-radius:8px; overflow:hidden;">
        <tr><td style="background-color:#1e3a8a; padding:20px 24px;">
          <h1 style="margin:0; font-size:20px; color:#ffffff;">New contact request</h1>
          <p style="margin:6px 0 0; font-size:13px; color:#c7d2fe;">Submitted {{submittedAt}} &middot; Ref {{referenceId}}</p>
        </td></tr>
        <tr><td style="padding:24px 24px 8px;">
          <p style="margin:0 0 16px; font-size:15px; line-height:22px;"><strong>{{name}}</strong> has contacted ShipOrbit through the website. A confirmation email has been sent to them.</p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e5e7eb; border-radius:6px; font-size:14px; line-height:20px;">
            <tr><td style="padding:10px 14px; width:35%; color:#6b7280; border-bottom:1px solid #e5e7eb;">Name</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{name}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Email</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;"><a href="mailto:{{email}}" style="color:#1e3a8a;">{{email}}</a></td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Phone</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{phone}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Company</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{company}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Website</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{website}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Topic</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{topic}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; border-bottom:1px solid #e5e7eb;">Monthly volume</td><td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{volume}}</td></tr>
            <tr><td style="padding:10px 14px; color:#6b7280; vertical-align:top;">Message</td><td style="padding:10px 14px; white-space:pre-wrap;">{{message}}</td></tr>
          </table>
        </td></tr>
        <tr><td style="padding:16px 24px 24px; font-size:13px; color:#6b7280;">
          Reply directly to <a href="mailto:{{email}}" style="color:#1e3a8a;">{{email}}</a>. Stored in SO_CONTACT_REQUEST (ID {{referenceId}}).
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>');
