package com.pratham.paymentx.enums;

import com.pratham.paymentx.exception.BadRequestException;

public enum Role {
    STUDENT,
    MERCHANT,
    ADMIN;

    public static Role from(String value){
        try{
            return Role.valueOf(value.toUpperCase());
        }catch (IllegalArgumentException | NullPointerException e){
            throw new BadRequestException("invalid role");
        }
    }
}
