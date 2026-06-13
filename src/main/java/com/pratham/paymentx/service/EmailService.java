package com.pratham.paymentx.service;

import com.pratham.paymentx.messaging.event.EmailNotificationEvent;

public interface EmailService {
    void processEmailEvent(EmailNotificationEvent event);
}
