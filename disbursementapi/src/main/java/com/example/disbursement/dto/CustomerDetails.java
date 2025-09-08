package com.example.disbursement.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"   // This field must exist in JSON
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MomoCustomerDetails.class, name = "MOMO"),
    @JsonSubTypes.Type(value = BankCustomerDetails.class, name = "BANK")
})
public interface CustomerDetails {
    String getName();
    String getPhoneNumber();
}
