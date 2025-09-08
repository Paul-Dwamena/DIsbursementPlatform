package com.example.dispatcher.service;

import lombok.Value;

@Value(staticConstructor = "of")
public class ProviderResult {
    boolean success;
    String providerRef;
    String errorCode;
    String errorMessage;

    public static ProviderResult success(String providerRef) {
        return new ProviderResult(true, providerRef, null, null);
    }

    public static ProviderResult failed(String code, String msg) {
        return new ProviderResult(false, null, code, msg);
    }
}
