package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerDetails {

    @NotBlank
    private String name;

    @NotBlank
    private String phoneNumber;

    private String email;
}
