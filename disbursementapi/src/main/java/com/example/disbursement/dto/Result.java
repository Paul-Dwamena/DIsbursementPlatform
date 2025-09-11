
package com.example.disbursement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result {
    private String reference;
    private boolean success;
}
