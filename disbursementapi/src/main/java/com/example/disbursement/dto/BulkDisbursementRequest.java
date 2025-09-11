package com.example.disbursement.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BulkDisbursementRequest {
    @NotBlank
    private String batchNumber;

    @NotNull
    @Size(min = 1, message = "At least one record is required")
    private List<DisbursementRequest> records;

    
}
