package com.pratham.paymentx.exception;

public class ExternalAuthenticationException extends RuntimeException {
    public ExternalAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}