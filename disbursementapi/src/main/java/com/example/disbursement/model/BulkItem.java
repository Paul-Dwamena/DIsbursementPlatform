package com.example.disbursement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bulk_items")
public class BulkItem {

    @Id
    private UUID id;

    private UUID bulkId;
    private UUID disbursementId;

}
