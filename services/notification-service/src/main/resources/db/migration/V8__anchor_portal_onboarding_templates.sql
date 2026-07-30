INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('ANCHOR_INTAKE_INVITE', 'EMAIL', 'Complete your anchor onboarding — {{applicationNumber}}',
     $body$Dear {{anchorName}},

Your lender has started an anchor onboarding application. Please sign in to the anchor portal and complete the remaining steps.

Application: {{applicationNumber}}
Portal: {{portalUrl}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}

You will be asked to change your password on first login.$body$),
    ('ANCHOR_APPLICATION_SENT_BACK', 'EMAIL', 'More details needed — {{applicationNumber}}',
     $body$Dear {{anchorName}},

We need additional information for application {{applicationNumber}}.

Notes from reviewer:
{{notes}}

Please sign in to update and resubmit: {{portalUrl}}$body$),
    ('ANCHOR_DOC_VERIFICATION_PENDING', 'EMAIL', 'eSign complete — documents under review — {{applicationNumber}}',
     $body$Dear {{anchorName}},

Thank you for completing eSign for application {{applicationNumber}}. Our operations team is reviewing your documents. We will notify you when verification is complete.$body$),
    ('ANCHOR_DOC_VERIFICATION_SENT_BACK', 'EMAIL', 'Document updates needed — {{applicationNumber}}',
     $body$Dear {{anchorName}},

Additional or corrected documents are needed for application {{applicationNumber}}.

Notes:
{{notes}}

Please sign in to upload and resubmit: {{portalUrl}}$body$),
    ('ANCHOR_INTAKE_RESUBMITTED', 'EMAIL', 'Application resubmitted — {{applicationNumber}}',
     $body$Dear {{anchorName}},

We received your resubmission for application {{applicationNumber}}. Our team will continue processing.$body$)
ON CONFLICT (template_code, channel) DO NOTHING;
