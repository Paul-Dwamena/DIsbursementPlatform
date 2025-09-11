package com.example.disbursement.dto;

import java.util.List;

import com.example.disbursement.model.BulkDisbursement;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BulkDisbursementResult {
    private BulkDisbursement batch;
    private List<String> failedReferences;
}

    

