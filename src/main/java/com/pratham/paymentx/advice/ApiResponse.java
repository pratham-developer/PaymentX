package com.pratham.paymentx.advice;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ApiResponse<T> {

    private OffsetDateTime timeStamp;
    private T data;
    private ApiError error;

    public ApiResponse() {
        this.timeStamp = OffsetDateTime.now();
    }

    public ApiResponse(T data) {
        this();
        this.data = data;
    }

    public ApiResponse(ApiError error) {
        this();
        this.error = error;
    }
}
