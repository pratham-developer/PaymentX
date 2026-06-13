package com.pratham.paymentx.messaging.event;

import java.util.Map;

public record EmailNotificationEvent(
        String toEmail,
        String toName,
        String subject,
        String templateName,
        Map<String, String> placeholders
) {}