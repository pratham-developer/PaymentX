package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.auth.GoogleAccount;

import java.util.UUID;


public interface AuthPersistenceService {
    UUID resolveAndPersistUser(GoogleAccount googleAccount);
}
