package com.example.disbursement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_providers")
public class PaymentProvider {

    @Id
    private UUID id;

    private String name; 

    private String code;

    private String channel;

    @Column("active")
    private boolean active;

    @Column("config")
    private String config;

}
