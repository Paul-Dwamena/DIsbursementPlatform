package com.example.dispatcher.service;
import com.example.disbursement.model.Disbursement;
import lombok.Value;
import java.math.BigDecimal;


@Value
public class ProviderRequest {
    String disbursementId;
    String account;
    BigDecimal amount;
    String currency;

    public static ProviderRequest from(Disbursement d) {
        return new ProviderRequest(
            d.getId().toString(),
            d.getCustomerId(),
            d.getAmount(),
            d.getCurrency()
        );
    }
    
    
}
