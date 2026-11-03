-- WELCOME EMAIL TEMPLATE
INSERT INTO SO_NOTIFICATION_TEMPLATE (PURPOSE, CHANNEL, SUBJECT, BODY)
VALUES ('WELCOME', 'EMAIL', 'Welcome to ShipOrbit, {{name}}!',
'<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Welcome to ShipOrbit</title>
</head>
<body style="margin:0; padding:0; background-color:#f4f6fb; font-family:Arial, Helvetica, sans-serif; color:#1f2937;">

  <!-- Preheader: preview text shown in the inbox list, hidden in the email -->
  <div style="display:none; max-height:0; overflow:hidden; mso-hide:all;">
    Your ShipOrbit account is ready. Compare courier rates, book shipments and track every order from one place.
  </div>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6fb;">
    <tr>
      <td align="center" style="padding:24px 12px;">

        <!-- Main container -->
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%; max-width:600px; background-color:#ffffff; border-radius:8px; overflow:hidden;">

          <!-- Header -->
          <tr>
            <td align="center" style="background-color:#1e3a8a; padding:28px 24px;">
              <!-- Replace the text logo with an image once hosted, e.g.:
              <img src="https://www.shiporbit.in/logo.png" alt="ShipOrbit" width="160" style="display:block; border:0;"> -->
              <h1 style="margin:0; font-size:28px; line-height:34px; color:#ffffff; letter-spacing:1px;">ShipOrbit</h1>
              <p style="margin:6px 0 0; font-size:14px; color:#c7d2fe;">Ship smarter. Deliver faster.</p>
            </td>
          </tr>

          <!-- Greeting -->
          <tr>
            <td style="padding:32px 32px 8px;">
              <h2 style="margin:0 0 16px; font-size:22px; color:#111827;">Welcome aboard, {{name}}!</h2>
              <p style="margin:0 0 14px; font-size:15px; line-height:24px;">
                Thank you for signing up with <strong>ShipOrbit</strong>. Your account is now active, and you can start shipping right away.
              </p>
              <p style="margin:0; font-size:15px; line-height:24px;">
                ShipOrbit brings multiple courier partners onto <em>one platform</em>, so you always get the best rate and the fastest delivery for every order.
              </p>
            </td>
          </tr>

          <!-- Account details -->
          <tr>
            <td style="padding:24px 32px 8px;">
              <h3 style="margin:0 0 12px; font-size:17px; color:#1e3a8a;">Your account details</h3>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e5e7eb; border-radius:6px; font-size:14px;">
                <tr>
                  <td style="padding:10px 14px; background-color:#f9fafb; border-bottom:1px solid #e5e7eb; width:40%; font-weight:bold;">Name</td>
                  <td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{name}}</td>
                </tr>
                <tr>
                  <td style="padding:10px 14px; background-color:#f9fafb; border-bottom:1px solid #e5e7eb; font-weight:bold;">Registered email</td>
                  <td style="padding:10px 14px; border-bottom:1px solid #e5e7eb;">{{email}}</td>
                </tr>
                <tr>
                  <td style="padding:10px 14px; background-color:#f9fafb; font-weight:bold;">Account status</td>
                  <td style="padding:10px 14px; color:#047857; font-weight:bold;">Active</td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Features (two-column) -->
          <tr>
            <td style="padding:24px 32px 8px;">
              <h3 style="margin:0 0 12px; font-size:17px; color:#1e3a8a;">What you can do with ShipOrbit</h3>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td valign="top" width="50%" style="padding:0 8px 16px 0;">
                    <p style="margin:0 0 4px; font-size:15px; font-weight:bold;">&#128176; Compare rates</p>
                    <p style="margin:0; font-size:14px; line-height:21px; color:#4b5563;">See prices from multiple couriers side by side and pick the best one.</p>
                  </td>
                  <td valign="top" width="50%" style="padding:0 0 16px 8px;">
                    <p style="margin:0 0 4px; font-size:15px; font-weight:bold;">&#128230; Book shipments</p>
                    <p style="margin:0; font-size:14px; line-height:21px; color:#4b5563;">Create and book orders in a few clicks, with doorstep pickup.</p>
                  </td>
                </tr>
                <tr>
                  <td valign="top" width="50%" style="padding:0 8px 16px 0;">
                    <p style="margin:0 0 4px; font-size:15px; font-weight:bold;">&#128205; Track every order</p>
                    <p style="margin:0; font-size:14px; line-height:21px; color:#4b5563;">Get live status updates from pickup to delivery.</p>
                  </td>
                  <td valign="top" width="50%" style="padding:0 0 16px 8px;">
                    <p style="margin:0 0 4px; font-size:15px; font-weight:bold;">&#127968; Manage pickups</p>
                    <p style="margin:0; font-size:14px; line-height:21px; color:#4b5563;">Save multiple pickup addresses and reuse them anytime.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Getting started (ordered list) -->
          <tr>
            <td style="padding:8px 32px 8px;">
              <h3 style="margin:0 0 12px; font-size:17px; color:#1e3a8a;">Get started in 4 steps</h3>
              <ol style="margin:0; padding-left:20px; font-size:15px; line-height:26px;">
                <li>Log in to your ShipOrbit dashboard.</li>
                <li>Add your <strong>pickup address</strong>.</li>
                <li>Enter pickup and delivery pincodes to <strong>compare rates</strong>.</li>
                <li>Book your shipment and <strong>track it</strong> till delivery.</li>
              </ol>
            </td>
          </tr>

          <!-- CTA button (table-based so it works in Outlook too) -->
          <tr>
            <td align="center" style="padding:28px 32px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" bgcolor="#f97316" style="border-radius:6px;">
                    <a href="https://www.shiporbit.in" target="_blank"
                       style="display:inline-block; padding:14px 32px; font-size:16px; font-weight:bold; color:#ffffff; text-decoration:none; border-radius:6px;">
                      Go to Dashboard
                    </a>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Tips box -->
          <tr>
            <td style="padding:0 32px 24px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#eff6ff; border-left:4px solid #1e3a8a; border-radius:4px;">
                <tr>
                  <td style="padding:14px 16px; font-size:14px; line-height:22px; color:#1e3a8a;">
                    <strong>Tip:</strong> Keep your package weight and dimensions accurate to avoid extra charges from courier partners.
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <!-- Divider -->
          <tr>
            <td style="padding:0 32px;">
              <hr style="border:0; border-top:1px solid #e5e7eb; margin:0;">
            </td>
          </tr>

          <!-- Support -->
          <tr>
            <td style="padding:24px 32px;">
              <h3 style="margin:0 0 10px; font-size:17px; color:#1e3a8a;">Need help?</h3>
              <ul style="margin:0 0 14px; padding-left:20px; font-size:14px; line-height:24px;">
                <li>General queries: <a href="mailto:enquiry@shiporbit.in" style="color:#1d4ed8;">enquiry@shiporbit.in</a></li>
                <li>Sales and bulk pricing: <a href="mailto:sales@shiporbit.in" style="color:#1d4ed8;">sales@shiporbit.in</a></li>
                <li>Partnerships: <a href="mailto:partners@shiporbit.in" style="color:#1d4ed8;">partners@shiporbit.in</a></li>
              </ul>
              <p style="margin:0; font-size:15px; line-height:24px;">
                Happy shipping!<br>
                <strong>Team ShipOrbit</strong>
              </p>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td align="center" style="background-color:#f9fafb; padding:20px 24px; font-size:12px; line-height:18px; color:#6b7280;">
              <p style="margin:0 0 6px;">
                <a href="https://www.shiporbit.in" style="color:#6b7280; text-decoration:underline;">Website</a> &nbsp;|&nbsp;
                <a href="mailto:enquiry@shiporbit.in" style="color:#6b7280; text-decoration:underline;">Contact</a>
              </p>
              <p style="margin:0 0 6px;">You received this email because you created an account on ShipOrbit.</p>
              <p style="margin:0 0 6px;">This is an automated message. Please do not reply to this email.</p>
              <p style="margin:0;">&copy; 2026 ShipOrbit. All rights reserved.</p>
            </td>
          </tr>

        </table>
        <!-- /Main container -->

      </td>
    </tr>
  </table>
</body>
</html>
');
