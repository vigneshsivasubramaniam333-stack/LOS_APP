package com.los.notification.template;

/**
 * Styled HTML email layouts for workflow notifications.
 * Anchor sanction template only — other workflow emails remain in {@link NotificationTemplateEngine}.
 */
public final class StyledWorkflowEmailTemplates {

    private StyledWorkflowEmailTemplates() {
    }

    public static String anchorSanctionApprovedEmail() {
        return statusUpdateEmail(
                "Anchor Program Sanction Approved",
                """
                        Your anchor onboarding application {{applicationNumber}} has been sanctioned.
                        <br><br><strong>Sanctioned limit:</strong> {{sanctionedAmount}}
                        <br><strong>Interest rate:</strong> {{interestRate}}% p.a.
                        <br><strong>Tenure:</strong> {{tenureMonths}} month(s)
                        <br><br><strong>Anchor portal access</strong>
                        <br><strong>Sign-in URL:</strong> <a href="{{anchorPortalUrl}}" target="_blank" style="color:#1d4ed8;text-decoration:underline;">{{anchorPortalUrl}}</a>
                        <br><strong>Username:</strong> {{loginEmail}}
                        <br><strong>Temporary password:</strong> {{temporaryPassword}}
                        <br><br>For security, you must set a new password when you sign in for the first time.
                        <br><br>Your program is now active on the PLP platform. Use the anchor portal to view program details and begin onboarding borrowers.
                        """);
    }

    private static String statusUpdateEmail(String badge, String bodyHtml) {
        return actionRequiredEmail("BillionTech LOS", badge, bodyHtml);
    }

    private static String actionRequiredEmail(String headerLabel, String headerBadge, String intro) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>""" + headerBadge + """
                  </title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f4f7fb;margin:0;padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;">
                        <tr>
                          <td style="padding:20px 24px;border-bottom:1px solid #e2e8f0;background-color:#ffffff;">
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                              <tr>
                                <td align="left" style="font-size:18px;font-weight:700;color:#1d4ed8;">""" + headerLabel + """
                                </td>
                                <td align="right" style="font-size:12px;color:#64748b;">""" + headerBadge + """
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 14px 0;font-size:16px;line-height:24px;color:#0f172a;">Dear {{borrowerName}},</p>
                          <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;color:#334155;">""" + intro + """
                          </p>
                          <p style="margin:0;font-size:13px;line-height:20px;color:#475569;">Need help? Please contact your relationship manager or support desk.</p>
                          <p style="margin:12px 0 0 0;font-size:13px;line-height:20px;color:#475569;">Regards,<br>BillionTech LOS Team</p>
                        </td></tr>
                        <tr>
                          <td style="padding:14px 24px;border-top:1px solid #e2e8f0;background:#f8fafc;">
                            <p style="margin:0;font-size:12px;line-height:18px;color:#6b7280;">If you did not request this action, please ignore this email.</p>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """;
    }
}
