package com.fixflow.party.dto;

import com.fixflow.party.domain.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PartyDtos {

    private PartyDtos() {
    }

    @Schema(name = "CustomerRequest")
    public record CustomerRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 32) String phone,
            @Size(max = 255) String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 120) String city,
            CustomerType customerType,
            @Size(max = 20) String gstNumber,
            BigDecimal creditLimit,
            String notes
    ) {
    }

    @Schema(name = "CustomerResponse")
    public record CustomerResponse(
            UUID id,
            String name,
            String phone,
            String email,
            String addressLine1,
            String city,
            CustomerType customerType,
            String gstNumber,
            BigDecimal creditLimit,
            BigDecimal outstandingAmount,
            BigDecimal totalPurchases,
            Instant lastTransactionAt,
            String notes,
            boolean active
    ) {
    }

    @Schema(name = "SupplierRequest")
    public record SupplierRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactPerson,
            @Size(max = 32) String phone,
            @Size(max = 255) String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 120) String city,
            @Size(max = 20) String gstNumber,
            Integer paymentTermsDays,
            String notes
    ) {
    }

    @Schema(name = "SupplierResponse")
    public record SupplierResponse(
            UUID id,
            String name,
            String contactPerson,
            String phone,
            String email,
            String addressLine1,
            String city,
            String gstNumber,
            int paymentTermsDays,
            BigDecimal outstandingAmount,
            String notes,
            boolean active
    ) {
    }
}
